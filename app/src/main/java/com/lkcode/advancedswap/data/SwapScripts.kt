package com.lkcode.advancedswap.data

import com.lkcode.advancedswap.domain.SwapOperation

internal object SwapScripts {
    const val SWAP_FILE = "/data/local/tmp/advanced-swap/swap.img"
    private const val BASE = "/data/local/tmp/advanced-swap"
    private const val TEMP = "/data/local/tmp/advanced-swap/.swap.img.tmp"

    private val commonTools = """
        has_tool() {
          name="${'$'}1"
          for base in /system/bin /system/xbin /vendor/bin /data/adb/magisk; do
            [ -x "${'$'}base/${'$'}name" ] && return 0
          done
          for bb in /system/bin/busybox /system/xbin/busybox /data/adb/magisk/busybox /system/bin/toybox; do
            [ -x "${'$'}bb" ] && "${'$'}bb" --list 2>/dev/null | grep -qx "${'$'}name" && return 0
          done
          return 1
        }
        run_tool() {
          name="${'$'}1"; shift
          for base in /system/bin /system/xbin /vendor/bin /data/adb/magisk; do
            [ -x "${'$'}base/${'$'}name" ] && { "${'$'}base/${'$'}name" "${'$'}@"; return ${'$'}?; }
          done
          for bb in /system/bin/busybox /system/xbin/busybox /data/adb/magisk/busybox /system/bin/toybox; do
            if [ -x "${'$'}bb" ] && "${'$'}bb" --list 2>/dev/null | grep -qx "${'$'}name"; then
              "${'$'}bb" "${'$'}name" "${'$'}@"; return ${'$'}?
            fi
          done
          return 127
        }
    """.trimIndent()

    val probe = """
        ${commonTools}
        set +e
        uid=${'$'}(id -u 2>/dev/null); echo "uid=${'$'}uid"
        total=${'$'}(awk '${'$'}1 == "SwapTotal:" { print ${'$'}2; exit }' /proc/meminfo 2>/dev/null)
        free_swap=${'$'}(awk '${'$'}1 == "SwapFree:" { print ${'$'}2; exit }' /proc/meminfo 2>/dev/null)
        echo "swap_total_kb=${'$'}total"
        echo "swap_free_kb=${'$'}free_swap"
        awk 'NR > 1 { print "swap_row=" ${'$'}0 }' /proc/swaps 2>/dev/null
        active_count=${'$'}(awk 'NR > 1 { count++ } END { print count+0 }' /proc/swaps 2>/dev/null)
        kernel=unknown
        if [ "${'$'}{total:-0}" -gt 0 ] 2>/dev/null; then kernel=supported
        elif [ "${'$'}{active_count:-0}" -gt 0 ] 2>/dev/null; then kernel=supported
        fi
        echo "kernel=${'$'}kernel"
        fs=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}3; exit }' /proc/mounts 2>/dev/null)
        echo "filesystem=${'$'}fs"
        mount_opts=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}4; exit }' /proc/mounts 2>/dev/null)
        case ",${'$'}mount_opts," in *,rw,*) [ -w /data/local/tmp ] && echo "data_rw=1" || echo "data_rw=0";; *) echo "data_rw=0";; esac
        free_kb=${'$'}(df -k /data/local/tmp 2>/dev/null | awk 'END { print ${'$'}4 }')
        case "${'$'}free_kb" in ''|*[!0-9]*) echo "available=-1";; *) echo "available=${'$'}((free_kb * 1024))";; esac
        has_tool mkswap; echo "mkswap=${'$'}?"
        has_tool swapon; echo "swapon=${'$'}?"
        has_tool swapoff; echo "swapoff=${'$'}?"
        has_tool dd; echo "dd=${'$'}?"
        has_tool mv; echo "mv=${'$'}?"
        has_tool rm; echo "rm=${'$'}?"
        if [ -e "${SWAP_FILE}" ] || [ -L "${SWAP_FILE}" ]; then echo "file_exists=1"; else echo "file_exists=0"; fi
        if [ -e "${TEMP}" ] || [ -L "${TEMP}" ]; then echo "temp_exists=1"; else echo "temp_exists=0"; fi
        awk -v target="${SWAP_FILE}" 'NR > 1 && ${'$'}1 == target { found=1 } END { exit !found }' /proc/swaps
        [ ${'$'}? -eq 0 ] && echo "file_active=1" || echo "file_active=0"
    """.trimIndent()

    fun operation(operation: SwapOperation, sizeMb: Int): String {
        if (operation == SwapOperation.CREATE) require(sizeMb in 64..512) { "Tamanho fora dos limites permitidos." }
        val header = """
            set -eu
            [ "${'$'}(id -u)" = "0" ] || { echo "Root (UID 0) obrigatório." >&2; exit 77; }
            BASE="${BASE}"
            FILE="${SWAP_FILE}"
            TEMP="${TEMP}"
            ${commonTools}
            [ ! -L /data/local/tmp ] && [ ! -L "${'$'}BASE" ] && [ ! -L "${'$'}FILE" ] && [ ! -L "${'$'}TEMP" ] || { echo "Caminho inesperado ou symlink recusado." >&2; exit 78; }
        """.trimIndent()
        val body = when (operation) {
            SwapOperation.CREATE -> """
                fs=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}3; exit }' /proc/mounts)
                [ "${'$'}fs" = "ext4" ] || { echo "Filesystem de /data não é ext4; criação cancelada." >&2; exit 79; }
                total=${'$'}(awk '${'$'}1 == "SwapTotal:" { print ${'$'}2; exit }' /proc/meminfo)
                active_count=${'$'}(awk 'NR > 1 { count++ } END { print count+0 }' /proc/swaps)
                if [ "${'$'}{total:-0}" -le 0 ] && [ "${'$'}{active_count:-0}" -le 0 ]; then
                  echo "Suporte do kernel a swap não confirmado." >&2; exit 80
                fi
                has_tool mkswap && has_tool swapon && has_tool swapoff && has_tool dd && has_tool mv && has_tool rm || { echo "Ferramentas necessárias para criar swap estão ausentes." >&2; exit 81; }
                mount_opts=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}4; exit }' /proc/mounts)
                case ",${'$'}mount_opts," in *,rw,*) :;; *) echo "A partição /data está somente leitura." >&2; exit 84;; esac
                [ -w /data/local/tmp ] || { echo "Sem permissão de escrita no destino." >&2; exit 84; }
                [ ! -e "${'$'}FILE" ] || { echo "Arquivo já existe; nada foi sobrescrito." >&2; exit 82; }
                [ ! -e "${'$'}TEMP" ] || { echo "Arquivo temporário de operação anterior existe; nada foi sobrescrito." >&2; exit 83; }
                free_kb=${'$'}(df -k /data/local/tmp | awk 'END { print ${'$'}4 }')
                case "${'$'}free_kb" in ''|*[!0-9]*) echo "Espaço livre não pôde ser verificado." >&2; exit 84;; esac
                [ "${'$'}free_kb" -ge ${'$'}((sizeMb * 1024 + 16384)) ] || { echo "Espaço livre insuficiente, incluindo margem de 16 MiB." >&2; exit 85; }
                mkdir -m 700 -p "${'$'}BASE"
                [ ! -L "${'$'}BASE" ] || { echo "Diretório de destino inesperado." >&2; exit 86; }
                chown 0:0 "${'$'}BASE" && chmod 700 "${'$'}BASE"
                [ ! -e "${'$'}FILE" ] && [ ! -L "${'$'}FILE" ] && [ ! -e "${'$'}TEMP" ] && [ ! -L "${'$'}TEMP" ] || { echo "O destino mudou durante a validação; nada foi sobrescrito." >&2; exit 86; }
                cleanup() { run_tool rm -f "${'$'}TEMP"; }
                trap cleanup EXIT
                trap 'exit 129' HUP
                trap 'exit 130' INT
                trap 'exit 143' TERM
                if ! run_tool dd if=/dev/zero of="${'$'}TEMP" bs=1048576 count=$sizeMb 2>/dev/null; then
                  echo "Falha ao alocar arquivo no armazenamento." >&2; exit 87
                fi
                chmod 600 "${'$'}TEMP"
                run_tool mkswap "${'$'}TEMP"
                chown 0:0 "${'$'}TEMP"
                run_tool mv "${'$'}TEMP" "${'$'}FILE"
                trap - EXIT HUP INT TERM
                echo "Arquivo de swap criado em ${SWAP_FILE} ($sizeMb MiB). Ele ainda não foi ativado."
            """.trimIndent()
            SwapOperation.ACTIVATE -> """
                fs=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}3; exit }' /proc/mounts)
                [ "${'$'}fs" = "ext4" ] || { echo "Filesystem de /data não é ext4; ativação cancelada." >&2; exit 79; }
                total=${'$'}(awk '${'$'}1 == "SwapTotal:" { print ${'$'}2; exit }' /proc/meminfo)
                active_count=${'$'}(awk 'NR > 1 { count++ } END { print count+0 }' /proc/swaps)
                if [ "${'$'}{total:-0}" -le 0 ] && [ "${'$'}{active_count:-0}" -le 0 ]; then
                  echo "Suporte do kernel a swap não confirmado." >&2; exit 80
                fi
                has_tool swapon && has_tool swapoff || { echo "Ferramentas swapon/swapoff ausentes." >&2; exit 81; }
                [ -f "${'$'}FILE" ] && [ ! -L "${'$'}FILE" ] || { echo "Arquivo gerenciado ausente ou inválido." >&2; exit 88; }
                awk -v target="${SWAP_FILE}" 'NR > 1 && ${'$'}1 == target { found=1 } END { exit !found }' /proc/swaps && { echo "Arquivo já está ativo." >&2; exit 89; }
                run_tool swapon "${'$'}FILE"
                echo "Área gerenciada ativada. A política de uso continua sob controle do kernel/Android."
            """.trimIndent()
            SwapOperation.DEACTIVATE -> """
                has_tool swapoff || { echo "A ferramenta swapoff não está disponível." >&2; exit 81; }
                awk -v target="${SWAP_FILE}" 'NR > 1 && ${'$'}1 == target { found=1 } END { exit !found }' /proc/swaps || { echo "Arquivo gerenciado não está ativo." >&2; exit 90; }
                run_tool swapoff "${'$'}FILE"
                echo "Área gerenciada desativada."
            """.trimIndent()
            SwapOperation.REMOVE -> """
                has_tool rm || { echo "A ferramenta rm não está disponível." >&2; exit 81; }
                mount_opts=${'$'}(awk '${'$'}2 == "/data" { print ${'$'}4; exit }' /proc/mounts)
                case ",${'$'}mount_opts," in *,rw,*) :;; *) echo "A partição /data está somente leitura." >&2; exit 91;; esac
                [ -w /data/local/tmp ] || { echo "Sem permissão para remover arquivos no destino." >&2; exit 91; }
                { [ -f "${'$'}FILE" ] && [ ! -L "${'$'}FILE" ] || [ -f "${'$'}TEMP" ] && [ ! -L "${'$'}TEMP" ]; } || { echo "Arquivo gerenciado ausente ou inválido." >&2; exit 91; }
                awk -v target="${SWAP_FILE}" 'NR > 1 && ${'$'}1 == target { found=1 } END { exit !found }' /proc/swaps && { echo "Desative a área antes de remover." >&2; exit 92; }
                run_tool rm -f "${'$'}FILE" "${'$'}TEMP"
                echo "Arquivo de swap gerenciado removido (incluindo temporário incompleto, se havia)."
            """.trimIndent()
        }
        return "$header\n$body"
    }
}

