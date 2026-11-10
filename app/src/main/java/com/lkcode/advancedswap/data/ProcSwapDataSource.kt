package com.lkcode.advancedswap.data

import com.lkcode.advancedswap.domain.SwapParser
import com.lkcode.advancedswap.domain.SwapSnapshot
import java.io.File

class ProcSwapDataSource {
    fun read(): SwapSnapshot = try {
        SwapParser.parse(File("/proc/meminfo").readText(), File("/proc/swaps").readText())
    } catch (error: Exception) {
        SwapSnapshot(null, null, emptyList(), error.message ?: "Não foi possível ler as informações de swap.")
    }
}

