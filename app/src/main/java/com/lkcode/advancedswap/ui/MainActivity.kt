package com.lkcode.advancedswap.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.lkcode.advancedswap.data.SwapRepository
import com.lkcode.advancedswap.domain.*
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var swapViewModel: SwapViewModel
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST && ::swapViewModel.isInitialized) swapViewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        swapViewModel = ViewModelProvider(this)[SwapViewModel::class.java]
        setContent {
            val state by swapViewModel.state.collectAsState()
            AdvancedSwapTheme(dark = isSystemInDarkTheme()) {
                SwapScreen(
                    state = state,
                    onRefresh = swapViewModel::refresh,
                    onRequestShizuku = ::requestShizuku,
                    onSizeChange = swapViewModel::setSizeText,
                    onOperate = swapViewModel::perform,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        if (::swapViewModel.isInitialized) swapViewModel.refresh()
    }

    override fun onStop() {
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
        super.onStop()
    }

    private fun requestShizuku() {
        lifecycleScope.launch {
            val status = SwapRepository(applicationContext).requestStatus()
            when {
                !status.installed -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                !status.running -> {
                    val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    if (launchIntent != null) startActivity(launchIntent)
                }
                !status.authorized -> runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
                else -> swapViewModel.refresh()
            }
        }
    }

    companion object { private const val SHIZUKU_PERMISSION_REQUEST = 7701 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapScreen(
    state: SwapUiState,
    onRefresh: () -> Unit,
    onRequestShizuku: () -> Unit,
    onSizeChange: (String) -> Unit,
    onOperate: (SwapOperationRequest) -> Unit,
) {
    val snapshot = state.snapshot
    var pending by remember { mutableStateOf<SwapOperationRequest?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Advanced Swap", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.padding(start = 16.dp)) },
            actions = {
                IconButton(onClick = onRefresh, enabled = !state.loading && !state.operationRunning,
                    modifier = Modifier.semantics { contentDescription = "Atualizar estado da swap" }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            },
        )
    }) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Memória do sistema", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Estado atual da swap. O kernel decide quando páginas são movidas.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                if (state.loading && snapshot != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Atualizando dados…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                if (state.error != null && snapshot?.swap?.totalKb != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(state.error, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            item { SwapStatusCard(state) }
            item { CapabilityCard(state, onRequestShizuku) }
            item {
                if (state.operationRunning) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${state.operationLabel ?: "Operação"} em andamento…", fontWeight = FontWeight.Medium)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Executando validações e aguardando resposta do sistema.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                state.lastResult?.let { result ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (result.success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(if (result.success) "Operação concluída" else "Operação não concluída", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp)); Text(result.message)
                            result.exitCode?.let { Text("Código de saída: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
            item { OperationsCard(state, onSizeChange) { pending = it } }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("O que este app não controla", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Swap é global: não é possível escolher Roblox ou forçar páginas de um app específico para primeiro ou segundo plano. O kernel e o Android controlam essa política. Swap em armazenamento pode aumentar latência e desgaste; as alterações não persistem após reiniciar.")
                    }
                }
            }
            item { DiagnosticsCard(snapshot?.diagnostics.orEmpty()) }
        }
    }
    pending?.let { request ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(confirmationTitle(request.operation)) },
            text = { Text(confirmationBody(request)) },
            confirmButton = { Button(onClick = { pending = null; onOperate(request) }) { Text("Confirmar") } },
            dismissButton = { OutlinedButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SwapStatusCard(state: SwapUiState) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Estado da swap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            when {
                state.loading && state.snapshot == null -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Lendo informações disponibilizadas pelo sistema…")
                }
                state.snapshot?.swap?.totalKb == null -> {
                    Text("Os contadores de swap não estão acessíveis neste dispositivo.", style = MaterialTheme.typography.bodyLarge)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                else -> {
                    val swap = state.snapshot.swap
                    MetricLine("Capacidade total", formatKb(swap.totalKb!!))
                    MetricLine("Em uso", swap.usedKb?.let(::formatKb) ?: "Indisponível")
                    MetricLine("Livre", swap.freeKb?.let(::formatKb) ?: "Indisponível")
                    Divider()
                    if (swap.entries.isEmpty()) Text("Nenhuma área de swap ativa foi listada pelo sistema.")
                    swap.entries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${entry.type} • ${formatKb(entry.usedKb)} usados de ${formatKb(entry.sizeKb)} • prioridade ${entry.priority}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(state: SwapUiState, onRequestShizuku: () -> Unit) {
    val report = state.snapshot?.capabilities
    val shizuku = state.snapshot?.shizuku
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compatibilidade detectada", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(report?.message ?: "Verificando permissões e recursos…", style = MaterialTheme.typography.bodyMedium)
            Text(report?.storageMessage ?: "O espaço no destino ainda não foi consultado.", style = MaterialTheme.typography.bodySmall)
            if (shizuku?.running == true && shizuku.uid != 0) Text("O modo ADB/shell não substitui root para swapon e swapoff.")
            OutlinedButton(onClick = onRequestShizuku, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    shizuku == null || !shizuku.installed -> "Obter Shizuku"
                    !shizuku.running -> "Abrir Shizuku"
                    !shizuku.authorized -> "Solicitar autorização do Shizuku"
                    else -> "Atualizar permissões"
                })
            }
        }
    }
}

@Composable
private fun OperationsCard(state: SwapUiState, onSizeChange: (String) -> Unit, onConfirm: (SwapOperationRequest) -> Unit) {
    val status = state.snapshot?.shizuku ?: ShizukuStatus(false, false, false, null)
    val probe = state.snapshot?.probe
    val create = SwapOperationRequest(SwapOperation.CREATE, state.sizeText.toIntOrNull())
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Área gerenciada pelo app", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("O app administra somente /data/local/tmp/advanced-swap/swap.img.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = state.sizeText,
                onValueChange = onSizeChange,
                label = { Text("Tamanho ao criar (MiB)") },
                supportingText = { Text("Permitido: ${SwapOperationValidator.MIN_SIZE_MB}–${SwapOperationValidator.MAX_SIZE_MB} MiB") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OperationAction("Criar arquivo", create, status, probe, state.operationRunning, onConfirm)
            OperationAction("Ativar", SwapOperationRequest(SwapOperation.ACTIVATE), status, probe, state.operationRunning, onConfirm)
            OperationAction("Desativar", SwapOperationRequest(SwapOperation.DEACTIVATE), status, probe, state.operationRunning, onConfirm)
            OperationAction("Remover arquivo", SwapOperationRequest(SwapOperation.REMOVE), status, probe, state.operationRunning, onConfirm)
            Text("Criação grava no armazenamento flash. Ativação altera swap global do kernel. Cada ação pede confirmação.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OperationAction(label: String, request: SwapOperationRequest, status: ShizukuStatus, probe: PrivilegedProbe?, running: Boolean, onConfirm: (SwapOperationRequest) -> Unit) {
    val validation = SwapOperationValidator.validate(request, status, probe)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Button(onClick = { onConfirm(request) }, enabled = validation.allowed && !running, modifier = Modifier.fillMaxWidth()) { Text(label) }
        if (!validation.allowed) Text(validation.reason ?: "Recurso indisponível.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiagnosticsCard(entries: List<DiagnosticEntry>) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(if (expanded) "Ocultar diagnóstico" else "Ver diagnóstico local (${entries.size})")
            }
            if (expanded) {
                if (entries.isEmpty()) Text("Nenhuma operação registrada.")
                entries.takeLast(10).asReversed().forEach { entry ->
                    Text("${DateFormat.getDateTimeInstance().format(Date(entry.timestamp))} • ${entry.operation} • ${if (entry.success) "sucesso" else "erro"}", style = MaterialTheme.typography.labelMedium)
                    Text(entry.message, style = MaterialTheme.typography.bodySmall)
                    Divider()
                }
                Text("Os registros ficam neste dispositivo e não são enviados pela rede.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatKb(value: Long): String = when {
    value >= 1024L * 1024 -> "%.2f GiB".format(value / (1024.0 * 1024))
    value >= 1024L -> "%.1f MiB".format(value / 1024.0)
    else -> "$value KiB"
}

private fun confirmationTitle(operation: SwapOperation) = when (operation) {
    SwapOperation.CREATE -> "Criar arquivo de swap?"
    SwapOperation.ACTIVATE -> "Ativar swap global?"
    SwapOperation.DEACTIVATE -> "Desativar swap?"
    SwapOperation.REMOVE -> "Remover arquivo gerenciado?"
}

private fun confirmationBody(request: SwapOperationRequest) = when (request.operation) {
    SwapOperation.CREATE -> "Serão alocados ${request.sizeMb} MiB em /data/local/tmp. Isso grava no armazenamento flash. O arquivo será criado inativo; não exige reinicialização."
    SwapOperation.ACTIVATE -> "O kernel poderá considerar este arquivo como swap para o sistema. O app não controla quais processos ou páginas serão movidos."
    SwapOperation.DEACTIVATE -> "O kernel deixará de usar este arquivo. Isso pode aumentar a pressão de memória e o Android ainda pode encerrar processos."
    SwapOperation.REMOVE -> "O app apagará somente o arquivo gerenciado e qualquer temporário incompleto em /data/local/tmp/advanced-swap/. A área precisa estar inativa. Não é possível desfazer."
}

