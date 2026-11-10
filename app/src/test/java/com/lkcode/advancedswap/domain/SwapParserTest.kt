package com.lkcode.advancedswap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapParserTest {
    @Test fun parsesCountersAndActiveSwapRows() {
        val parsed = SwapParser.parse(
            "MemTotal: 4096000 kB\nSwapTotal: 2048000 kB\nSwapFree: 1536000 kB\n",
            "Filename Type Size Used Priority\n/dev/block/zram0 partition 2048000 512000 100\n",
        )
        assertEquals(2048000L, parsed.totalKb)
        assertEquals(1536000L, parsed.freeKb)
        assertEquals(512000L, parsed.usedKb)
        assertEquals("/dev/block/zram0", parsed.entries.single().path)
        assertNull(parsed.readError)
    }

    @Test fun clampsUsedCounterToZeroWhenFreeIsLargerThanTotal() {
        val parsed = SwapParser.parse("SwapTotal: 10 kB\nSwapFree: 20 kB", "Filename Type Size Used Priority\n")
        assertEquals(0L, parsed.usedKb)
    }

    @Test fun reportsMissingCountersWithoutThrowing() {
        val parsed = SwapParser.parse("MemTotal: 100 kB", "Filename Type Size Used Priority\n")
        assertTrue(parsed.totalKb == null)
        assertTrue(parsed.readError != null)
    }
}

