package com.lkcode.advancedswap.domain

object SwapParser {
    fun parse(memInfo: String, procSwaps: String): SwapSnapshot {
        val mem = memInfo.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) parts[0].removeSuffix(":") to parts[1].toLongOrNull() else null
        }.toMap()
        val entries = procSwaps.lineSequence().drop(1).mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 5) return@mapNotNull null
            val size = fields[2].toLongOrNull() ?: return@mapNotNull null
            val used = fields[3].toLongOrNull() ?: return@mapNotNull null
            val priority = fields[4].toIntOrNull() ?: return@mapNotNull null
            SwapEntry(fields[0], fields[1], size, used, priority)
        }.toList()
        return SwapSnapshot(
            totalKb = mem["SwapTotal"],
            freeKb = mem["SwapFree"],
            entries = entries,
            readError = if ("SwapTotal" !in mem || "SwapFree" !in mem) "Os contadores SwapTotal/SwapFree não estão acessíveis." else null,
        )
    }
}

object CapabilityDetector {
    fun detect(snapshot: SwapSnapshot, shizuku: ShizukuStatus, probe: PrivilegedProbe?): CapabilityReport {
        val monitoring = snapshot.totalKb != null && snapshot.freeKb != null
        val support = probe?.kernelSwap ?: if ((snapshot.totalKb ?: 0L) > 0L) SupportState.SUPPORTED else SupportState.UNKNOWN
        val storage = probe?.let { data ->
            data.availableBytes?.let { bytes ->
                "Livre em /data/local/tmp: ${bytes / (1024 * 1024)} MiB. ${if (data.storageWritable) "Destino gravável." else "Destino somente leitura: criar/remover ficam bloqueados."}"
            }
        } ?: "Espaço no destino ainda não foi verificado com privilégio suficiente."
        val message = when {
            !shizuku.installed -> "Monitoramento disponível. Instale Shizuku e inicie o serviço como root para avaliar alterações."
            !shizuku.running -> "Shizuku não está iniciado. O monitoramento continua disponível; alterações exigem root."
            !shizuku.authorized -> "Shizuku precisa da autorização deste app para consultar o ambiente privilegiado."
            shizuku.uid != 0 -> "Shizuku está no modo ADB/shell (UID ${shizuku.uid ?: "desconhecido"}); swap exige root (UID 0)."
            probe == null -> "Não foi possível consultar as capacidades do kernel pelo Shizuku."
            probe.error != null -> probe.error
            probe.uid != 0 -> "O UserService do Shizuku não confirmou UID 0; operações bloqueadas."
            support == SupportState.UNSUPPORTED -> "O kernel informa que swap não está habilitado; criar/ativar fica bloqueado."
            support == SupportState.UNKNOWN -> "O suporte do kernel não pôde ser confirmado; criar/ativar fica bloqueado. Um arquivo inativo ainda pode ser removido com root."
            probe.filesystem != "ext4" -> "Filesystem de /data não confirmado como ext4 (${probe.filesystem ?: "desconhecido"}); criar/ativar fica bloqueado."
            !probe.toolsReady -> "Faltam algumas ferramentas; cada operação só fica disponível se seus requisitos estiverem presentes."
            else -> "Root, ferramentas e suporte detectados. As validações serão repetidas antes de cada operação."
        }
        val allowed = shizuku.root && probe != null && probe.uid == 0 && probe.error == null && support == SupportState.SUPPORTED &&
            probe.filesystem == "ext4" && probe.toolsReady
        return CapabilityReport(monitoring, allowed, support, message, storage)
    }
}

