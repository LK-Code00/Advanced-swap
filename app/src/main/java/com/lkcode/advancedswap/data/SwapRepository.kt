package com.lkcode.advancedswap.data

import android.content.Context
import com.lkcode.advancedswap.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SwapRepository(context: Context) {
    private val proc = ProcSwapDataSource()
    private val shizuku = ShizukuGateway(context)
    private val log = DiagnosticLog(context)

    suspend fun refresh(): RepositorySnapshot = withContext(Dispatchers.IO) {
        val status = shizuku.status()
        val probe = if (status.running && status.authorized) {
            runCatching { parseProbe(shizuku.probe()) }.getOrElse { failedProbe(status.uid, it.message ?: "Falha na consulta Shizuku.") }
        } else null
        val localSwap = proc.read()
        val swap = if (localSwap.totalKb == null || localSwap.freeKb == null) probe?.globalSwap ?: localSwap else localSwap
        RepositorySnapshot(swap, status, probe, CapabilityDetector.detect(swap, status, probe), log.read())
    }

    suspend fun requestStatus(): ShizukuStatus = withContext(Dispatchers.IO) { shizuku.status() }

    suspend fun perform(request: SwapOperationRequest): OperationResult = withContext(Dispatchers.IO) {
        val status = shizuku.status()
        val probe = if (status.running && status.authorized) runCatching { parseProbe(shizuku.probe()) }.getOrNull() else null
        val validation = SwapOperationValidator.validate(request, status, probe)
        if (!validation.allowed) return@withContext record(request.operation, OperationResult(false, validation.reason ?: "Validação recusou a operação."))
        try {
            val remote = shizuku.runOperation(request.operation, request.sizeMb)
            val message = when {
                remote.exitCode == 0 -> remote.stdout.ifBlank { "Operação concluída." }
                remote.stderr.isNotBlank() -> remote.stderr
                remote.stdout.isNotBlank() -> remote.stdout
                else -> "O sistema recusou a operação (código ${remote.exitCode})."
            }
            record(request.operation, OperationResult(remote.exitCode == 0, message, remote.exitCode))
        } catch (error: Exception) {
            record(request.operation, OperationResult(false, error.message ?: "Falha ao executar pelo Shizuku."))
        }
    }

    private fun record(operation: SwapOperation, result: OperationResult): OperationResult {
        log.add(DiagnosticEntry(System.currentTimeMillis(), operation.name, result.success, result.message))
        return result
    }

    private fun failedProbe(uid: Int?, message: String) = PrivilegedProbe(
        uid = uid ?: -1,
        kernelSwap = SupportState.UNKNOWN,
        filesystem = null,
        availableBytes = null,
        hasMkswap = false,
        hasSwapon = false,
        hasSwapoff = false,
        managedFileExists = false,
        managedFileActive = false,
        error = "Não foi possível consultar capacidades via Shizuku: $message",
        storageWritable = false,
    )

    private fun parseProbe(output: String): PrivilegedProbe {
        val lines = output.lineSequence().toList()
        val values = lines.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index < 1) null else line.substring(0, index) to line.substring(index + 1).trim()
        }.toMap()
        fun hasTool(key: String) = values[key] == "0"
        val globalSwap = if (values["swap_total_kb"]?.toLongOrNull() != null && values["swap_free_kb"]?.toLongOrNull() != null) {
            val rows = lines.filter { it.startsWith("swap_row=") }.joinToString("\n") { it.substringAfter('=') }
            SwapParser.parse(
                "SwapTotal: ${values["swap_total_kb"]} kB\nSwapFree: ${values["swap_free_kb"]} kB",
                "Filename Type Size Used Priority\n$rows",
            )
        } else null
        return PrivilegedProbe(
            uid = values["uid"]?.toIntOrNull() ?: -1,
            kernelSwap = when (values["kernel"]) { "supported" -> SupportState.SUPPORTED; "unsupported" -> SupportState.UNSUPPORTED; else -> SupportState.UNKNOWN },
            filesystem = values["filesystem"]?.takeIf(String::isNotBlank),
            availableBytes = values["available"]?.toLongOrNull()?.takeIf { it >= 0 },
            hasMkswap = hasTool("mkswap"),
            hasSwapon = hasTool("swapon"),
            hasSwapoff = hasTool("swapoff"),
            managedFileExists = values["file_exists"] == "1",
            managedFileActive = values["file_active"] == "1",
            error = values["error"],
            managedTempExists = values["temp_exists"] == "1",
            hasDd = hasTool("dd"),
            hasMv = hasTool("mv"),
            hasRm = hasTool("rm"),
            storageWritable = values["data_rw"] == "1",
            globalSwap = globalSwap,
        )
    }
}

data class RepositorySnapshot(
    val swap: SwapSnapshot,
    val shizuku: ShizukuStatus,
    val probe: PrivilegedProbe?,
    val capabilities: CapabilityReport,
    val diagnostics: List<DiagnosticEntry>,
)

