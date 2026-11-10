package com.lkcode.advancedswap.domain

data class SwapEntry(val path: String, val type: String, val sizeKb: Long, val usedKb: Long, val priority: Int)

data class SwapSnapshot(
    val totalKb: Long?,
    val freeKb: Long?,
    val entries: List<SwapEntry>,
    val readError: String? = null,
) {
    val usedKb: Long? get() = if (totalKb != null && freeKb != null) (totalKb - freeKb).coerceAtLeast(0) else null
}

data class ShizukuStatus(val installed: Boolean, val running: Boolean, val authorized: Boolean, val uid: Int?) {
    val root: Boolean get() = running && authorized && uid == 0
}

enum class SupportState { SUPPORTED, UNSUPPORTED, UNKNOWN }

data class PrivilegedProbe(
    val uid: Int,
    val kernelSwap: SupportState,
    val filesystem: String?,
    val availableBytes: Long?,
    val hasMkswap: Boolean,
    val hasSwapon: Boolean,
    val hasSwapoff: Boolean,
    val managedFileExists: Boolean,
    val managedFileActive: Boolean,
    val error: String? = null,
    val managedTempExists: Boolean = false,
    val hasDd: Boolean = true,
    val hasMv: Boolean = true,
    val hasRm: Boolean = true,
    val storageWritable: Boolean = true,
    val globalSwap: SwapSnapshot? = null,
) {
    val toolsReady get() = hasMkswap && hasSwapon && hasSwapoff && hasDd && hasMv && hasRm
}

data class CapabilityReport(
    val monitoringAvailable: Boolean,
    val mutationAllowed: Boolean,
    val kernelSwap: SupportState,
    val message: String,
    val storageMessage: String,
)

enum class SwapOperation { CREATE, ACTIVATE, DEACTIVATE, REMOVE }
data class SwapOperationRequest(val operation: SwapOperation, val sizeMb: Int? = null)
data class ValidationResult(val allowed: Boolean, val reason: String? = null)
data class OperationResult(val success: Boolean, val message: String, val exitCode: Int? = null)
data class DiagnosticEntry(val timestamp: Long, val operation: String, val success: Boolean, val message: String)

