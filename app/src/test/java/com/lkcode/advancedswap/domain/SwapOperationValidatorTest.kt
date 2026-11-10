package com.lkcode.advancedswap.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapOperationValidatorTest {
    private val root = ShizukuStatus(true, true, true, 0)
    private val shell = ShizukuStatus(true, true, true, 2000)
    private fun probe(
        kernel: SupportState = SupportState.SUPPORTED,
        free: Long? = 1024L * 1024 * 1024,
        exists: Boolean = false,
        active: Boolean = false,
        tools: Boolean = true,
        filesystem: String? = "ext4",
    ) = PrivilegedProbe(0, kernel, filesystem, free, tools, tools, tools, exists, active)

    @Test fun permitsValidCreateWhenPreflightPasses() {
        val result = SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), root, probe())
        assertTrue(result.reason.orEmpty(), result.allowed)
    }

    @Test fun refusesShellUidEvenWhenShizukuIsAuthorized() {
        val result = SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), shell, probe())
        assertFalse(result.allowed)
        assertTrue(result.reason.orEmpty().contains("root"))
    }

    @Test fun rejectsSizeSpaceAndUnknownKernel() {
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 32), root, probe()).allowed)
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), root, probe(free = 100)).allowed)
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), root, probe(kernel = SupportState.UNKNOWN)).allowed)
    }

    @Test fun validatesLifecycleAndPreventsRemovingActiveFile() {
        assertTrue(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.ACTIVATE), root, probe(exists = true)).allowed)
        assertTrue(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.DEACTIVATE), root, probe(exists = true, active = true)).allowed)
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.REMOVE), root, probe(exists = true, active = true)).allowed)
        assertTrue(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.REMOVE), root, probe(exists = true)).allowed)
        assertTrue(SwapOperationValidator.validate(
            SwapOperationRequest(SwapOperation.REMOVE), root, probe().copy(managedTempExists = true),
        ).allowed)
        assertFalse(SwapOperationValidator.validate(
            SwapOperationRequest(SwapOperation.CREATE, 128), root, probe().copy(managedTempExists = true),
        ).allowed)
    }

    @Test fun refusesUnsupportedFilesystemOrMissingCommands() {
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), root, probe(filesystem = "f2fs")).allowed)
        assertFalse(SwapOperationValidator.validate(SwapOperationRequest(SwapOperation.CREATE, 128), root, probe(tools = false)).allowed)
    }

    @Test fun cleanupDoesNotRequireKernelSwapButStillRequiresWritableStorage() {
        assertTrue(SwapOperationValidator.validate(
            SwapOperationRequest(SwapOperation.REMOVE), root, probe(kernel = SupportState.UNKNOWN, exists = true),
        ).allowed)
        assertFalse(SwapOperationValidator.validate(
            SwapOperationRequest(SwapOperation.REMOVE), root, probe(exists = true).copy(storageWritable = false),
        ).allowed)
        assertTrue(SwapOperationValidator.validate(
            SwapOperationRequest(SwapOperation.DEACTIVATE), root, probe(active = true).copy(storageWritable = false),
        ).allowed)
    }
}

