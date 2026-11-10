package com.lkcode.advancedswap.domain

object SwapOperationValidator {
    const val MIN_SIZE_MB = 64
    const val MAX_SIZE_MB = 512
    private const val HEADROOM_BYTES = 16L * 1024 * 1024

    fun validate(request: SwapOperationRequest, shizuku: ShizukuStatus, probe: PrivilegedProbe?): ValidationResult {
        if (!shizuku.running) return ValidationResult(false, "Inicie o Shizuku.")
        if (!shizuku.authorized) return ValidationResult(false, "Conceda a autorização do Shizuku.")
        if (shizuku.uid != 0) return ValidationResult(false, "Esta operação exige Shizuku iniciado como root (UID 0). O modo ADB usa UID 2000.")
        if (probe == null) return ValidationResult(false, "Capacidades privilegiadas ainda não foram verificadas.")
        if (probe.error != null) return ValidationResult(false, probe.error)
        if (probe.uid != 0) return ValidationResult(false, "O processo privilegiado não confirmou UID 0.")
        return when (request.operation) {
            SwapOperation.CREATE -> {
                if (probe.kernelSwap != SupportState.SUPPORTED) return ValidationResult(false, "O suporte a swap do kernel não foi confirmado.")
                if (probe.filesystem != "ext4") return ValidationResult(false, "O filesystem de destino não foi confirmado como ext4.")
                if (!probe.toolsReady) return ValidationResult(false, "Faltam ferramentas para criar e administrar swap.")
                if (!probe.storageWritable) return ValidationResult(false, "A partição /data está somente leitura; não é possível criar o arquivo.")
                val size = request.sizeMb ?: return ValidationResult(false, "Informe o tamanho da área.")
                if (size !in MIN_SIZE_MB..MAX_SIZE_MB) return ValidationResult(false, "O tamanho deve ficar entre $MIN_SIZE_MB e $MAX_SIZE_MB MiB.")
                if (probe.managedFileExists) return ValidationResult(false, "O arquivo gerenciado já existe; remova-o antes de criar outro.")
                if (probe.managedTempExists) return ValidationResult(false, "Há um arquivo incompleto de uma operação anterior; remova-o antes de criar outro.")
                val required = size.toLong() * 1024 * 1024 + HEADROOM_BYTES
                if ((probe.availableBytes ?: 0L) < required) return ValidationResult(false, "Espaço livre insuficiente no destino, incluindo margem de segurança.")
                ValidationResult(true)
            }
            SwapOperation.ACTIVATE -> when {
                probe.kernelSwap != SupportState.SUPPORTED -> ValidationResult(false, "O suporte a swap do kernel não foi confirmado.")
                probe.filesystem != "ext4" -> ValidationResult(false, "O filesystem de destino não foi confirmado como ext4.")
                !probe.hasSwapon || !probe.hasSwapoff -> ValidationResult(false, "Faltam as ferramentas swapon/swapoff.")
                !probe.managedFileExists -> ValidationResult(false, "Crie primeiro o arquivo gerenciado pelo app.")
                probe.managedFileActive -> ValidationResult(false, "O arquivo já está ativo como swap.")
                else -> ValidationResult(true)
            }
            SwapOperation.DEACTIVATE -> when {
                !probe.hasSwapoff -> ValidationResult(false, "A ferramenta swapoff não está disponível.")
                probe.managedFileActive -> ValidationResult(true)
                else -> ValidationResult(false, "O arquivo gerenciado não está ativo.")
            }
            SwapOperation.REMOVE -> when {
                !probe.hasRm -> ValidationResult(false, "A ferramenta rm não está disponível.")
                !probe.storageWritable -> ValidationResult(false, "A partição /data está somente leitura; não é possível remover o arquivo.")
                !probe.managedFileExists && !probe.managedTempExists -> ValidationResult(false, "Não existe arquivo gerenciado para remover.")
                probe.managedFileActive -> ValidationResult(false, "Desative o arquivo antes de removê-lo.")
                else -> ValidationResult(true)
            }
        }
    }
}

