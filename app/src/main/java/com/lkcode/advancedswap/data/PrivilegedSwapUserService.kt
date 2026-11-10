package com.lkcode.advancedswap.data

import android.os.Process
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/** The service runs with the identity selected by Shizuku (root or shell). */
class PrivilegedSwapUserService : IPrivilegedSwapService.Stub() {
    override fun probe(): String {
        val result = execute(SwapScripts.probe)
        if (result.first != 0) throw IllegalStateException(result.third.ifBlank { "Consulta privilegiada recusada." })
        return result.second
    }

    override fun runOperation(operation: String, sizeMb: Int): String {
        if (Process.myUid() != 0) return JSONObject()
            .put("exitCode", 77).put("stdout", "")
            .put("stderr", "Root (UID 0) obrigatório; o serviço está como UID ${Process.myUid()}.").toString()
        val script = runCatching { SwapScripts.operation(com.lkcode.advancedswap.domain.SwapOperation.valueOf(operation), sizeMb) }
            .getOrElse { return JSONObject().put("exitCode", 78).put("stdout", "").put("stderr", "Parâmetros de operação inválidos.").toString() }
        val result = execute(script)
        return JSONObject().put("exitCode", result.first).put("stdout", result.second).put("stderr", result.third).toString()
    }

    private fun execute(script: String): Triple<Int, String, String> {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", script), null, java.io.File("/"))
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outThread = Thread { BufferedReader(InputStreamReader(process.inputStream)).useLines { lines -> lines.forEach { stdout.append(it).append('\n') } } }
        val errThread = Thread { BufferedReader(InputStreamReader(process.errorStream)).useLines { lines -> lines.forEach { stderr.append(it).append('\n') } } }
        outThread.start(); errThread.start()
        val exitCode = process.waitFor()
        outThread.join(); errThread.join()
        return Triple(exitCode, stdout.toString().trim(), stderr.toString().trim())
    }
}

