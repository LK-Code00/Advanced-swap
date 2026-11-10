package com.lkcode.advancedswap.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityDetectorTest {
    private val snapshot = SwapSnapshot(0, 0, emptyList())
    private val root = ShizukuStatus(true, true, true, 0)
    private val shell = ShizukuStatus(true, true, true, 2000)
    private fun probe(kernel: SupportState = SupportState.SUPPORTED, filesystem: String? = "ext4") = PrivilegedProbe(
        uid = 0, kernelSwap = kernel, filesystem = filesystem, availableBytes = 512L * 1024 * 1024,
        hasMkswap = true, hasSwapon = true, hasSwapoff = true,
        managedFileExists = false, managedFileActive = false,
    )

    @Test fun monitoringRemainsAvailableWithoutRootManagement() {
        val report = CapabilityDetector.detect(snapshot, shell, probe())
        assertTrue(report.monitoringAvailable)
        assertFalse(report.mutationAllowed)
        assertTrue(report.message.contains("root"))
    }

    @Test fun mutationsRequireConfirmedKernelAndFilesystem() {
        assertFalse(CapabilityDetector.detect(snapshot, root, probe(SupportState.UNKNOWN)).mutationAllowed)
        assertFalse(CapabilityDetector.detect(snapshot, root, probe(filesystem = "f2fs")).mutationAllowed)
        assertTrue(CapabilityDetector.detect(snapshot, root, probe()).mutationAllowed)
    }

    @Test fun unavailableProcCountersOnlyDisableMonitoring() {
        val report = CapabilityDetector.detect(SwapSnapshot(null, null, emptyList(), "denied"), root, probe())
        assertFalse(report.monitoringAvailable)
        assertTrue(report.mutationAllowed)
    }
}

