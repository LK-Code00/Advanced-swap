package com.lkcode.advancedswap.data

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.lkcode.advancedswap.domain.ShizukuStatus
import com.lkcode.advancedswap.domain.SwapOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import rikka.shizuku.Shizuku

class ShizukuGateway(private val context: Context) {
    private val lock = Any()
    @Volatile private var remote: IPrivilegedSwapService? = null
    private var pending: CompletableDeferred<IPrivilegedSwapService>? = null
    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, PrivilegedSwapUserService::class.java))
            .daemon(false).processNameSuffix("swap").tag("advanced-swap-root-operations")
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = binder?.let { IPrivilegedSwapService.Stub.asInterface(it) }
            synchronized(lock) {
                remote = service
                if (service != null) pending?.complete(service)
                else pending?.completeExceptionally(IllegalStateException("Shizuku não conectou o serviço privilegiado."))
                pending = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                remote = null
                pending?.completeExceptionally(IllegalStateException("O serviço Shizuku foi desconectado."))
                pending = null
            }
        }
    }

    fun status(): ShizukuStatus {
        val managerInstalled = try {
            if (Build.VERSION.SDK_INT >= 33) context.packageManager.getPackageInfo("moe.shizuku.privileged.api", PackageManager.PackageInfoFlags.of(0))
            else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            }
            true
        } catch (_: Exception) { false }
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val installed = managerInstalled || running
        if (!running) return ShizukuStatus(installed, false, false, null)
        val authorized = try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
        val uid = try { Shizuku.getUid() } catch (_: Exception) { null }
        return ShizukuStatus(installed, running, authorized, uid)
    }

    suspend fun probe(): String {
        val current = status()
        if (!current.running || !current.authorized) throw IllegalStateException("Shizuku não está autorizado.")
        return service().probe()
    }

    suspend fun runOperation(operation: SwapOperation, sizeMb: Int?): RemoteResult {
        val current = status()
        if (!current.root) throw IllegalStateException("Shizuku autorizado como shell; root (UID 0) é obrigatório.")
        if (operation == SwapOperation.CREATE) require(requireNotNull(sizeMb) in 64..512)
        val json = JSONObject(service().runOperation(operation.name, sizeMb ?: 0))
        return RemoteResult(json.optInt("exitCode", 1), json.optString("stdout"), json.optString("stderr"))
    }

    private suspend fun service(): IPrivilegedSwapService {
        remote?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        var shouldBind = false
        val deferred = synchronized(lock) {
            remote?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
            pending ?: CompletableDeferred<IPrivilegedSwapService>().also { pending = it; shouldBind = true }
        }
        if (shouldBind) try {
            Shizuku.bindUserService(args, connection)
        } catch (error: Exception) {
            synchronized(lock) {
                if (pending === deferred) pending = null
                deferred.completeExceptionally(error)
            }
        }
        return try {
            withTimeout(15_000) { deferred.await() }
        } catch (error: Exception) {
            synchronized(lock) { if (pending === deferred) pending = null }
            throw error
        }
    }

    data class RemoteResult(val exitCode: Int, val stdout: String, val stderr: String)
}

