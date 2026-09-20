package br.com.ricardobandeira.alcada.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.ricardobandeira.alcada.data.*
import br.com.ricardobandeira.alcada.domain.*
import br.com.ricardobandeira.alcada.ui.theme.*
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home), DISCOVER("Analisar", Icons.Default.AutoGraph), BACKTEST("Testar", Icons.Default.ShowChart), MORE("Mais", Icons.Default.MoreHoriz)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlcadaApp(vm: AlcadaViewModel = viewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    val datasets by vm.datasets.collectAsState(); val selected by vm.selectedDataset.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { CenterAlignedTopAppBar(title = { Brand() }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) { Destination.entries.forEach { item ->
            NavigationBarItem(selected = destination == item, onClick = { destination = item }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
        } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (destination) {
        Destination.HOME -> HomeScreen(vm)
        Destination.DISCOVER -> DiscoverScreen(vm, datasets, selected)
        Destination.BACKTEST -> BacktestScreen(vm, datasets, selected)
        Destination.MORE -> MoreScreen(vm, datasets, selected)
    } } }
}

@Composable private fun Brand() { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Logo(30.dp); Column { Text("ALÇADA", fontWeight = FontWeight.Black, letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing); Text("LABORATÓRIO QUANTITATIVO", style = MaterialTheme.typography.labelSmall, color = Analytic) } } }
@Composable private fun Logo(dimension: androidx.compose.ui.unit.Dp) { Canvas(Modifier.size(dimension)) { val p = Path().apply { moveTo(size.width*.08f,size.height*.78f); lineTo(size.width*.35f,size.height*.5f); lineTo(size.width*.54f,size.height*.64f); lineTo(size.width*.9f,size.height*.18f) }; drawPath(p, Positive, style=androidx.compose.ui.graphics.drawscope.Stroke(width=size.width*.12f)); drawLine(Analytic, Offset(size.width*.62f,size.height*.18f), Offset(size.width*.9f,size.height*.18f), size.width*.09f); drawLine(Analytic, Offset(size.width*.9f,size.height*.18f), Offset(size.width*.9f,size.height*.46f), size.width*.09f) } }

@Composable private fun HomeScreen(vm: AlcadaViewModel) {
    val strategies by vm.strategies.collectAsState()
    val health by vm.deviceHealth.collectAsState(); val backtests by vm.backtests.collectAsState(); val datasets by vm.datasets.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Hero("Laboratório quantitativo", "Processamento local • resultados históricos, nunca promessas") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("${datasets.size}", "conjuntos de dados", Analytic, Modifier.weight(1f)); Metric("${strategies.size}", "estratégias", Positive, Modifier.weight(1f)); Metric("${backtests.size}", "testes", Pending, Modifier.weight(1f)) } }
        item { SectionTitle("Estratégias por perfil", "Até 3 estratégias por perfil, quando validadas") }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(Profile.entries) { profile -> ProfilePanel(profile, strategies.filter { it.profile == profile.name }.sortedByDescending { it.definitionJson.split('|').getOrNull(6)?.toDoubleOrNull() ?: 0.0 }.take(3)) } } }
        if (strategies.isEmpty()) item { EmptyState(Icons.Default.Science, "Nenhuma estratégia validada", "Importe dados e execute uma pesquisa. Métricas não serão inventadas.") }
    }
}

@Composable private fun ProfilePanel(profile: Profile, values: List<StrategyEntity>) { val color = when(profile){ Profile.CONSERVATIVE->Positive; Profile.MODERATE->Analytic; Profile.AGGRESSIVE->Pending; Profile.EXPERIMENTAL->Negative }; val title=profileLabel(profile)
    Card(Modifier.width(292.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title, color=color, fontWeight=FontWeight.Bold); if(values.isEmpty()) Text("Em validação • sem resultados suficientes", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.bodySmall) else values.forEach { strategy -> val data=strategy.definitionJson.split('|'); Surface(shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(12.dp)) { Text(strategy.name, fontWeight=FontWeight.Bold); Text("${strategy.symbol} • ${marketLabel(strategy.market)}", style=MaterialTheme.typography.labelSmall); Text("Fora da amostra ${pct(data.getOrNull(2))}  •  robustez ${pct(data.getOrNull(6))}", color=color) } } } } }
}

@Composable private fun DiscoverScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) {
    val state by vm.researchState.collectAsState()
    val strategies by vm.strategies.collectAsState()
    val health by vm.deviceHealth.collectAsState()
    var intensive by rememberSaveable { mutableStateOf(false) }
    var candidates by rememberSaveable { mutableFloatStateOf(2_000f) }
    var researchPayout by rememberSaveable { mutableFloatStateOf(.85f) }
    val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    var threads by rememberSaveable { mutableFloatStateOf(minOf(2, processors).toFloat()) }
    var memoryMb by rememberSaveable { mutableFloatStateOf(256f) }
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Hero("Descobrir padrões", "Pavios, movimento e validação dentro e fora da amostra") }
        item { DatasetSelector(datasets, selected, vm::selectDataset) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo Pesquisa Intensiva", fontWeight = FontWeight.Bold)
                            Text("Amplia a quantidade de candidatos avaliados localmente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = intensive, onCheckedChange = { intensive = it }, enabled = !state.running)
                    }
                    Text("Retorno da pesquisa: ${(researchPayout * 100).toInt()}% • taxa mínima para equilíbrio ${pct(1.0 / (1.0 + researchPayout))}")
                    Slider(researchPayout, { researchPayout = it }, valueRange = .5f..1f, enabled = !state.running)
                    Text("Orçamento: ${candidates.toInt()} candidatos")
                    Slider(candidates, { candidates = it }, valueRange = 500f..10_000f, steps = 18, enabled = !state.running)
                    if (intensive) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Bateria: ${health.batteryPercent?.let { "$it%" } ?: "—"}${if (health.charging) " • carregando" else ""}", style = MaterialTheme.typography.bodySmall); Text("Temperatura: ${health.thermalLabel}", style = MaterialTheme.typography.bodySmall, color = if (health.thermalLabel == "alto") Negative else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("Processadores: ${threads.toInt()} de $processors")
                        Slider(threads, { threads = it }, valueRange = 1f..processors.toFloat(), steps = (processors - 2).coerceAtLeast(0), enabled = !state.running)
                        Text("Memória reservada: ${memoryMb.toInt()} MB")
                        Slider(memoryMb, { memoryMb = it }, valueRange = 128f..1024f, steps = 6, enabled = !state.running)
                        Text("A reserva define o teto de configuração da pesquisa; o Android continua controlando os recursos reais do aparelho.", color = Pending, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { ActionPanel(state, "Pesquisa local", "${candidates.toInt()} candidatos • validação temporal • busca evolutiva", { vm.runResearch(ResearchOptions(candidates.toInt(), intensive = intensive, threads = if (intensive) threads.toInt() else null, memoryMb = if (intensive) memoryMb.toInt() else null, payout = researchPayout.toDouble())) }, vm::cancelResearch) }
        if (state.running) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (state.paused) Button(onClick = vm::resumeResearch, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text("Retomar") } else OutlinedButton(onClick = vm::pauseResearch, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Pause, null); Text("Pausar") } } }
        if (!state.running && state.progress >= 1f) item { OutlinedButton(onClick = vm::repeatResearch, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Repetir pesquisa com a mesma configuração") } }
        if(strategies.isNotEmpty()) {
            item { SectionTitle("Descobertas persistidas", "Ordene os resultados e compare os campeões de cada perfil") }
            item { StrategyRankingControls(strategies) }
        } else item { EmptyState(Icons.Default.AutoGraph, "Ainda sem descobertas", "A busca mantém somente candidatos com amostra mínima e validação fora da amostra.") }
    }
}

@Composable
private fun StrategyRankingControls(strategies: List<StrategyEntity>) {
    var sort by rememberSaveable { mutableStateOf("ROBUSTEZ") }
    var profile by rememberSaveable { mutableStateOf("TODOS") }
    val filtered = strategies.filter { profile == "TODOS" || it.profile == profile }.sortedByDescending {
        val d = it.definitionJson.split('|')
        when (sort) {
            "FORA" -> d.getOrNull(2)?.toDoubleOrNull()
            "FATOR" -> d.getOrNull(3)?.toDoubleOrNull()
            "RESULTADO" -> d.getOrNull(4)?.toDoubleOrNull()
            else -> d.getOrNull(6)?.toDoubleOrNull()
        } ?: Double.NEGATIVE_INFINITY
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ordenar do melhor para o pior", fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("ROBUSTEZ" to "Robustez", "FORA" to "Fora da amostra", "FATOR" to "Fator de lucro", "RESULTADO" to "Resultado esperado")) { pair ->
                FilterChip(selected = sort == pair.first, onClick = { sort = pair.first }, label = { Text(pair.second) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("TODOS","CONSERVATIVE","MODERATE","AGGRESSIVE","EXPERIMENTAL")) { key ->
                FilterChip(selected = profile == key, onClick = { profile = key }, label = { Text(if (key == "TODOS") "Todos os perfis" else profileLabel(key)) })
            }
        }
        filtered.groupBy { it.profile }.forEach { entry ->
            val profileItems = entry.value
            Text(profileLabel(entry.key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Analytic)
            Text("Ranking: 1º melhor → " + profileItems.size + "º menor resultado segundo o filtro selecionado.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            profileItems.forEachIndexed { index, strategy ->
                StrategyCard(strategy, index + 1, index == 0)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BacktestScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { var market by rememberSaveable { mutableStateOf(Market.BINARY_OPTIONS) }; var payout by rememberSaveable { mutableFloatStateOf(.85f) }; var expiration by rememberSaveable { mutableFloatStateOf(3f) }; val state by vm.backtestState.collectAsState(); val result by vm.result.collectAsState(); val analysis by vm.analysis.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Hero("Teste histórico", "Sinais por assimetria de pavios • sem execução de ordens") }; item { DatasetSelector(datasets, selected, vm::selectDataset) }; item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { Market.entries.take(2).forEachIndexed { i, item -> SegmentedButton(market==item,{market=item},SegmentedButtonDefaults.itemShape(i,2)){Text(if(item==Market.BINARY_OPTIONS) "Opções" else "Forex")}} }; if(market==Market.BINARY_OPTIONS){ Text("Retorno ${(payout*100).toInt()}% • taxa mínima para equilíbrio ${pct(1.0/(1+payout))}"); Slider(payout,{payout=it}, valueRange=.5f..1f); Text("Expiração ${expiration.toInt()} velas"); Slider(expiration,{expiration=it},valueRange=1f..10f,steps=8) } else Text("Forex: limite de perda (SL) 0,002 • objetivo de ganho (TP) 0,004 • proteção móvel 0,0015 • saída em 20 velas") } } }; item { ActionPanel(state,"Executar teste","Custos, retorno e direção são calculados pelo aplicativo",{ vm.runBacktest(BacktestOptions(market=market, expiration=expiration.toInt(), payout=payout.toDouble())) },vm::cancelBacktest) }; result?.let { value -> item { Metrics(value.metrics) }; item { ResultCharts(value, analysis) } } ?: item { EmptyState(Icons.Default.QueryStats,"Sem resultado nesta sessão","Execute o teste; o resultado também será salvo no histórico.") } }
}

@Composable private fun MoreScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris -> vm.importFiles(uris) }; val importState by vm.importState.collectAsState(); val backtests by vm.backtests.collectAsState(); val runs by vm.runs.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) { item { Hero("Dados e histórico","Tudo permanece no dispositivo") }; item { SectionTitle("Dados locais","Selecione um ou vários arquivos CSV/ZIP. Os dados são processados somente no aparelho.") }; item { Button(onClick={launcher.launch(arrayOf("text/csv","text/comma-separated-values","application/zip","application/x-zip-compressed","text/plain"))},Modifier.fillMaxWidth(), enabled=!importState.running){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("Selecionar CSV ou ZIP")}; if(importState.running){ LinearProgressIndicator(progress={importState.progress},modifier=Modifier.fillMaxWidth()); OutlinedButton(onClick=vm::cancelImport){Text("Cancelar importação")} }; Status(importState) }; items(datasets){ d -> DatasetRow(d,d.id==selected,{vm.selectDataset(d.id)},{vm.deleteDataset(d.id)}) }; item { SectionTitle("Histórico","Resultados persistem após a limpeza do cache bruto") }; if(backtests.isEmpty()&&runs.isEmpty()) item { EmptyState(Icons.Default.History,"Histórico vazio","Testes históricos e pesquisas concluídos aparecerão aqui.") }; items(backtests){ HistoryBacktest(it) }; items(runs){ run -> ListItem(headlineContent={Text("Pesquisa ${statusLabel(run.status)}")},supportingContent={Text("${run.progress}% • semente ${run.seed} • ${date(run.startedAt)}")},leadingContent={Icon(Icons.Default.Science,null)}) }; item { SectionTitle("Configurações e privacidade","Cálculos locais • cache bruto expira após 7 dias"); Text("Fontes externas de histórico e agenda econômica permanecem desativadas até uma integração confiável ser configurada. Nenhuma ordem real é executada.",color=MaterialTheme.colorScheme.onSurfaceVariant); Text("Resultados são evidências históricas e fora da amostra; não representam garantia de desempenho futuro.", style=MaterialTheme.typography.bodySmall, color=Pending) } }
}

@Composable
private fun DatasetRow(dataset: DatasetEntity, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = if (selected) Positive else Analytic)
            Column(Modifier.weight(1f)) {
                Text(dataset.name, fontWeight = FontWeight.Bold)
                Text(
                    "${dataset.symbol} • ${dataset.rowCount} velas • ${dataset.timeframeMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selecionado", tint = Positive)
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Excluir conjunto de dados", tint = Negative) }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Excluir conjunto de dados?") }, text = { Text("O arquivo bruto importado será removido do aparelho. Estratégias e resultados históricos permanecem salvos.") }, confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir", color = Negative) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}
@Composable
private fun DatasetSelector(values: List<DatasetEntity>, selected: String?, select: (String) -> Unit) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conjunto de dados", fontWeight = FontWeight.Bold)
            if (values.isEmpty()) {
                Text("Importe CSV ou ZIP em Mais › Dados", color = Pending)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(values) { dataset ->
                        FilterChip(
                            selected = dataset.id == selected,
                            onClick = { select(dataset.id) },
                            label = { Text("${dataset.name} • ${dataset.rowCount}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(state: OperationState, title: String, subtitle: String, start: () -> Unit, cancel: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.running) LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Status(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = start, enabled = !state.running) { Icon(Icons.Default.PlayArrow, null); Text("Iniciar") }
                if (state.running) OutlinedButton(onClick = cancel) { Icon(Icons.Default.Stop, null); Text("Cancelar") }
            }
        }
    }
}

@Composable
private fun Status(state: OperationState) {
    state.message?.let { Text(it, color = if (state.running) Analytic else Positive, style = MaterialTheme.typography.bodySmall) }
    state.error?.let { Text(it, color = Negative, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Metrics(metrics: BacktestMetrics) {
    var open by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(pct(metrics.winRate), "taxa de acerto", Analytic, Modifier.weight(1f), { open = "Taxa de acerto" })
            Metric(number(metrics.netProfit), "resultado", if (metrics.netProfit >= 0) Positive else Negative, Modifier.weight(1f), { open = "Resultado" })
            Metric(number(metrics.maxDrawdown), "queda máxima", Negative, Modifier.weight(1f), { open = "Queda máxima" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric(metrics.trades.toString(), "operações", Analytic, Modifier.weight(1f), { open = "Operações" })
            Metric(number(metrics.profitFactor), "fator de lucro", if (metrics.profitFactor >= 1) Positive else Negative, Modifier.weight(1f), { open = "Fator de lucro" })
            Metric(number(metrics.expectancy), "resultado esperado", if (metrics.expectancy >= 0) Positive else Negative, Modifier.weight(1f), { open = "Resultado esperado" })
        }
        metrics.breakEvenWinRate?.let { equilibrium ->
            Text("Taxa mínima para equilíbrio: " + pct(equilibrium) + " • margem observada: " + pct(metrics.winRate - equilibrium), color = if (metrics.winRate >= equilibrium) Positive else Negative, style = MaterialTheme.typography.bodySmall)
        }
        Text("Toque em qualquer número para entender o que ele mede.", style = MaterialTheme.typography.labelSmall, color = Analytic)
    }
    open?.let { metric -> AlertDialog(onDismissRequest = { open = null }, title = { Text(metric) }, text = { Text(metricExplanation(metric, metrics)) }, confirmButton = { TextButton(onClick = { open = null }) { Text("Entendi") } }) }
}

@Composable
private fun ResultCharts(result: BacktestResult, analysis: QuantAnalysis?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LineChart("Evolução do resultado", result.equity, Positive)
        LineChart("Queda do resultado", result.drawdown, Negative)
        LineChart("Taxa de acerto ao longo do tempo", result.rollingWinRate, Analytic)
        BarChart("Distribuição das operações", ChartAnalytics.pnlDistribution(result.trades))
        BarChart("Desempenho por horário (UTC)", ChartAnalytics.performanceByHour(result.trades))
        BarChart("Desempenho por dia", ChartAnalytics.performanceByDay(result.trades))
        analysis?.let {
            HeatmapCard(it.heatmap)
            BarChart("Dentro da amostra × fora da amostra", it.isOos)
            BarChart("Período gráfico × expiração", it.timeframeExpiration)
            it.monteCarlo?.let { mc ->
                ChartCard("Simulação Monte Carlo") {
                    Text("${mc.simulations} simulações • blocos de ${mc.blockSize} operações para preservar sequências", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Resultado mediano ${number(mc.medianNetProfit)} • pior faixa de 5% ${number(mc.p05NetProfit)}")
                    Text("Queda máxima no percentil 95 ${number(mc.p95MaxDrawdown)} • cenários positivos ${pct(mc.profitableShare)}")
                    Text("Leitura rápida: " + if (mc.profitableShare >= .70) "a maioria dos reordenamentos permaneceu positiva" else "há sensibilidade relevante à ordem das operações", color = if (mc.profitableShare >= .70) Positive else Pending, style = MaterialTheme.typography.bodySmall)
                }
            }
            BarChart("Desempenho por ativo", it.assets)
            BarChart("Proporção do pavio × movimento futuro", it.wick)
        }
    }
}

@Composable
private fun HeatmapCard(values: List<Bucket>) {
    ChartCard("Mapa de desempenho: dia × hora") {
        if (values.isEmpty()) Text("Amostra insuficiente", color = Pending) else {
            Text("Cada célula mostra uma janela de dia e hora. Deslize para ver todas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(values) { bucket ->
                    Surface(Modifier.width(78.dp), shape = RoundedCornerShape(8.dp), color = if (bucket.value >= 0) Positive.copy(alpha=.22f) else Negative.copy(alpha=.22f)) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(bucket.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(number(bucket.value), style = MaterialTheme.typography.labelSmall, color = if (bucket.value >= 0) Positive else Negative, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            val best = values.maxByOrNull { it.value }
            val worst = values.minByOrNull { it.value }
            Text("Faixa observada: " + number(values.minOf { it.value }) + " até " + number(values.maxOf { it.value }) + " • " + values.size + " grupos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Melhor janela: " + (best?.label ?: "—") + " " + number(best?.value ?: Double.NaN) + " • Pior: " + (worst?.label ?: "—") + " " + number(worst?.value ?: Double.NaN), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LineChart(title: String, values: List<Double>, color: Color) {
    ChartCard(title) {
        if (values.size < 2) Text("Dados insuficientes para este gráfico", color = Pending) else {
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                val minimum = values.min()
                val range = max(1e-9, values.max() - minimum)
                values.zipWithNext().forEachIndexed { index, pair ->
                    drawLine(color, Offset(size.width * index / (values.size - 1), size.height * (1 - ((pair.first - minimum) / range)).toFloat()), Offset(size.width * (index + 1) / (values.size - 1), size.height * (1 - ((pair.second - minimum) / range)).toFloat()), strokeWidth = 4f)
                }
            }
            val first = values.first()
            val last = values.last()
            Text("Início " + number(first) + " • Final " + number(last) + " • Mínimo " + number(values.min()) + " • Máximo " + number(values.max()), style = MaterialTheme.typography.bodySmall)
            Text("Variação: " + number(last - first), color = if (last >= first) Positive else Negative, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text("Pontos analisados: " + values.size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChart(title: String, values: List<Bucket>) {
    ChartCard(title) {
        if (values.isEmpty()) Text("Amostra insuficiente", color = Pending) else {
            val maxValue = max(1e-9, values.maxOf { kotlin.math.abs(it.value) })
            LazyRow(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                items(values) { bucket ->
                    Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(number(bucket.value), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Surface(Modifier.width(46.dp).height((82 * kotlin.math.abs(bucket.value) / maxValue).toFloat().dp), color = if (bucket.value >= 0) Positive else Negative) {}
                        Text(bucket.label.take(9), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            val best = values.maxByOrNull { it.value }
            val worst = values.minByOrNull { it.value }
            Text("Faixa observada: " + number(values.minOf { it.value }) + " até " + number(values.maxOf { it.value }) + " • " + values.size + " grupos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Melhor: " + (best?.label ?: "—") + " " + number(best?.value ?: Double.NaN) + " • Pior: " + (worst?.label ?: "—") + " " + number(worst?.value ?: Double.NaN), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = true }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Info, contentDescription = "Explicar gráfico", tint = Analytic)
            }
            content()
            Text("Toque para entender e configurar no gráfico", style = MaterialTheme.typography.labelSmall, color = Analytic)
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Text(chartExplanation(title)) }, confirmButton = { TextButton(onClick = { open = false }) { Text("Entendi") } })
}

private fun chartExplanation(title: String): String = when {
    title.contains("Evolução") -> "Mostra o resultado acumulado operação a operação. Procure crescimento consistente, não apenas saltos isolados. Para reproduzir, use o ativo e período da estratégia e aplique exatamente suas regras de entrada e saída."
    title.contains("Queda") -> "Mede a perda desde um pico anterior. Quanto menor e mais controlada, melhor a estabilidade histórica. Use esta medida para dimensionar risco."
    title.contains("Taxa de acerto") -> "Mostra como a taxa de acerto muda no tempo. Quedas prolongadas podem indicar mudança de regime. Configure o mesmo período, direção, filtros e expiração ou saída do teste."
    title.contains("Distribuição") -> "Mostra como os resultados das operações se distribuem. Ajuda a identificar dependência de poucas operações excepcionais."
    title.contains("dia × hora") -> "Combina dia e hora em UTC. Deslize horizontalmente para comparar todas as janelas. Na plataforma, configure o mesmo ativo e período da estratégia, converta o relógio para UTC e use a janela junto das demais regras de entrada."
    title.contains("horário") -> "Compara o resultado por hora UTC. Converta o horário da plataforma para UTC antes de aplicar um filtro de sessão."
    title.contains("Dentro") -> "Compara dados usados no desenvolvimento com dados posteriores não usados na criação. Menor deterioração fora da amostra é evidência de maior estabilidade."
    title.contains("expiração") -> "Compara período gráfico e expiração. Configure ambos exatamente como indicados na estratégia."
    title.contains("Monte Carlo") -> "Reordena blocos das operações históricas muitas vezes para medir sensibilidade à sequência. Observe resultado mediano, faixa de 5%, queda máxima extrema e proporção de cenários positivos. Não é previsão; use para avaliar fragilidade antes de reproduzir a estratégia."
    title.contains("ativo") -> "Compara o resultado entre ativos. Interprete somente ativos com amostra suficiente e reproduza a estratégia no mesmo símbolo e período gráfico."
    title.contains("pavio") -> "Relaciona a proporção do pavio ao movimento posterior. No gráfico, compare o pavio com o corpo da vela, aplique o limite indicado nas regras e confirme direção, período e expiração ou saída antes do sinal."
    else -> "Este painel resume evidência histórica. As estratégias detalham ativo, período gráfico, direção, filtros e parâmetros para reprodução."
}

@Composable
private fun StrategyCard(strategy: StrategyEntity, rank: Int? = null, champion: Boolean = false) {
    var details by remember { mutableStateOf(false) }
    val data = strategy.definitionJson.split('|')
    val profitFactor = data.getOrNull(3)?.toDoubleOrNull()?.let(::number) ?: "—"
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { if (champion) Text("CAMPEÃ DO PERFIL", color = Pending, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black); Text((rank?.let { "#$it  " } ?: "") + strategy.name, fontWeight = FontWeight.Bold) }
                Text(profileLabel(strategy.profile), color = if (champion) Pending else Analytic)
            }
            Text("${strategy.symbol} • ${marketLabel(strategy.market)}")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Dentro ${pct(data.getOrNull(1))}")
                Text("Fora ${pct(data.getOrNull(2))}")
                Text("Fator de lucro $profitFactor")
            }
            Text("Operações ${data.getOrNull(0) ?: "—"} • Resultado esperado ${number(data.getOrNull(4)?.toDoubleOrNull() ?: Double.NaN)} • Queda máxima ${number(data.getOrNull(5)?.toDoubleOrNull() ?: Double.NaN)}")
            if (data.size > 11) {
                Text("${directionLabel(data.getOrNull(9))} • ${data.getOrNull(10) ?: "—"} min • expiração ${data.getOrNull(11) ?: "—"} vela(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Robustez ${pct(data.getOrNull(6))} • ${validationLabel(data.getOrNull(7))}",
                color = if (data.getOrNull(8) == "true") Negative else Positive,
            )
            if (data.getOrNull(8) == "true") Text("Alerta: sinais de fragilidade, instabilidade temporal ou sobreajuste.", color = Negative)
            if (data.size > 18) {
                val oosTrades = data.getOrNull(14) ?: "—"
                val oosExpectancy = data.getOrNull(15)?.toDoubleOrNull()?.let(::number) ?: "—"
                val oosPf = data.getOrNull(16)?.toDoubleOrNull()?.let(::number) ?: "—"
                val stable = data.getOrNull(17) ?: "—"
                val folds = data.getOrNull(18) ?: "—"
                Text("Evidência fora da amostra: $oosTrades operações • resultado esperado $oosExpectancy • fator de lucro $oosPf", style = MaterialTheme.typography.bodySmall)
                Text("Estabilidade temporal: $stable de $folds janelas futuras com resultado positivo e fator de lucro acima de 1.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (data.size > 20) Text("Monte Carlo: P5 do resultado ${number(data.getOrNull(19)?.toDoubleOrNull() ?: Double.NaN)} • P95 da queda ${number(data.getOrNull(20)?.toDoubleOrNull() ?: Double.NaN)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data.getOrNull(12)?.takeIf { it.isNotBlank() }?.let { encoded ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Como configurar no gráfico", fontWeight = FontWeight.SemiBold, color = Analytic)
                Text("1. Abra " + strategy.symbol + " no período de " + (data.getOrNull(10) ?: "—") + " min.", style = MaterialTheme.typography.bodySmall)
                Text("2. Direção: " + directionLabel(data.getOrNull(9)) + ". " + if (strategy.market == "BINARY_OPTIONS") "Use expiração de " + (data.getOrNull(11) ?: "—") + " vela(s)." else "Configure as saídas descritas pela estratégia.", style = MaterialTheme.typography.bodySmall)
                Text("3. Só considere o sinal quando todas as condições abaixo coincidirem.", style = MaterialTheme.typography.bodySmall)
                Text("Regras da estratégia", fontWeight = FontWeight.SemiBold)
                encoded.split('&').take(4).forEach { rule ->
                    Text("• " + ruleDescription(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val rationale = when (strategy.profile) {
                    "CONSERVATIVE" -> "Classificação: evidência mais estável, robustez elevada e vantagem fora da amostra."
                    "MODERATE" -> "Classificação: validação fora da amostra positiva, com robustez intermediária."
                    "AGGRESSIVE" -> "Classificação: resultado esperado positivo, porém com menor robustez estatística."
                    else -> "Classificação: hipótese experimental; exige mais validação antes de qualquer uso prático."
                }
                Text(rationale, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Desempenho histórico não garante resultados futuros. A validação fora da amostra reduz, mas não elimina, o risco de sobreajuste.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { details = true }) { Text("Ver explicação completa") }
                StrategyScriptActions(strategy, data)
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text("Como esta estratégia funciona") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(strategy.name, fontWeight = FontWeight.Bold); Text("Configuração prática", fontWeight = FontWeight.SemiBold, color = Analytic); Text("Ativo " + strategy.symbol + " • período " + (data.getOrNull(10) ?: "—") + " min • direção " + directionLabel(data.getOrNull(9)) + if (strategy.market == "BINARY_OPTIONS") " • expiração " + (data.getOrNull(11) ?: "—") + " vela(s)" else ""); Text("Confirme todas as regras de entrada no fechamento da vela antes do sinal. Não antecipe condições ainda incompletas."); Text("Evidência", fontWeight = FontWeight.SemiBold); Text("Dentro da amostra ${pct(data.getOrNull(1))} • fora da amostra ${pct(data.getOrNull(2))} • robustez ${pct(data.getOrNull(6))}."); if (data.size > 20) Text("Monte Carlo: em 95% das simulações o resultado ficou acima de ${number(data.getOrNull(19)?.toDoubleOrNull() ?: Double.NaN)}; queda P95 ${number(data.getOrNull(20)?.toDoubleOrNull() ?: Double.NaN)}.") ; Text("Pontos de atenção", fontWeight = FontWeight.SemiBold); Text(if (data.getOrNull(8) == "true") "Há sinais de fragilidade ou sobreajuste. Exija mais dados e novas janelas." else "Sem alerta forte pelos critérios atuais; perdas e mudanças de regime continuam possíveis."); Text("Resultados históricos não garantem desempenho futuro.") } }, confirmButton = { TextButton(onClick = { details = false }) { Text("Fechar") } })
}

@Composable
private fun StrategyScriptActions(strategy: StrategyEntity, data: List<String>) {
    var format by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    Text("Exportar estratégia", fontWeight = FontWeight.SemiBold)
    Text("Gere uma base de implementação e copie para sua plataforma. Regras não convertidas ficam bloqueadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf("Lua", "MQL5", "TradingView")) { language ->
            AssistChip(onClick = { format = language }, label = { Text(language) }, leadingIcon = { Icon(Icons.Default.Code, null) })
        }
    }
    format?.let { language ->
        val script = strategyScript(strategy, data, language)
        AlertDialog(onDismissRequest = { format = null }, title = { Text("Script " + language) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Revise ativo, período, tamanho da posição e parâmetros da corretora antes de usar.", color = Pending, style = MaterialTheme.typography.bodySmall)
                if (script.contains("REVISÃO OBRIGATÓRIA")) Text("Código bloqueado para entrada automática: alguns filtros ainda precisam de conversão específica para esta linguagem.", color = Negative, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) { Text(script, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(script)); format = null }) { Text("Copiar script") } }, dismissButton = { TextButton(onClick = { format = null }) { Text("Fechar") } })
    }
}

internal fun strategyScript(strategy: StrategyEntity, data: List<String>, language: String): String {
    require(language in setOf("TradingView", "MQL5", "Lua")) { "Formato de script não suportado: $language" }
    val rules = data.getOrNull(12)?.split('&')?.filter { it.isNotBlank() }.orEmpty()
    val converted = rules.map { it to scriptRule(it, language, data.getOrNull(9)) }
    val expressions = converted.mapNotNull { it.second }
    val unsupported = converted.filter { it.second == null }.map { ruleDescription(it.first) }
    val direction = data.getOrNull(9)
    val invalidDirection = direction !in setOf("CALL", "PUT")
    val condition = if (expressions.isEmpty() || unsupported.isNotEmpty() || invalidDirection) "false" else expressions.joinToString(if (language == "MQL5") " && " else " and ")
    val issues = unsupported + if (invalidDirection) listOf("direção ausente ou inválida") else emptyList()
    val warning = if (issues.isEmpty()) "" else "REVISÃO OBRIGATÓRIA - " + issues.joinToString(", ") + "\n"
    val name = strategy.name.replace("\"", "")
    return when (language) {
        "TradingView" -> "// " + warning + "//@version=5\nstrategy(\"Alçada - " + name + "\", overlay=true)\nsignal = " + condition + "\nplotshape(signal, style=" + if (data.getOrNull(9) == "PUT") "shape.triangledown, location=location.abovebar, color=color.red)" else "shape.triangleup, location=location.belowbar, color=color.lime)" + "\nif signal\n    strategy.entry(\"Alçada\", " + if (data.getOrNull(9) == "PUT") "strategy.short)" else "strategy.long)"
        "MQL5" -> "// " + warning + "// Alçada - " + name + "\n// Gerador de sinal: não envia ordens ao mercado.\nvoid OnTick(){\n double o=iOpen(_Symbol,_Period,1),h=iHigh(_Symbol,_Period,1),l=iLow(_Symbol,_Period,1),c=iClose(_Symbol,_Period,1),pc=iClose(_Symbol,_Period,2);\n double range=MathMax(h-l,1e-12),body=MathMax(MathAbs(c-o),1e-12);\n double upperWick=h-MathMax(o,c),lowerWick=MathMin(o,c)-l;\n double wickBodyRatio=(upperWick+lowerWick)/body,bodyRangeRatio=MathAbs(c-o)/range,closeLocation=(c-l)/range;\n double momentumRangeRatio=MathAbs(c-pc)/range,gapRangeRatio=MathAbs(o-pc)/range,accelerationRangeRatio=MathAbs((c-pc)-(pc-iClose(_Symbol,_Period,3)))/range;\n double atrSum=0; for(int i=1;i<=14;i++){ double hi=iHigh(_Symbol,_Period,i),lo=iLow(_Symbol,_Period,i),prev=iClose(_Symbol,_Period,i+1); atrSum+=MathMax(hi-lo,MathMax(MathAbs(hi-prev),MathAbs(lo-prev))); } double atrRangeRatio=(atrSum/14.0)/range;\n int seq=0; int dir=(c>o?1:(c<o?-1:0)); for(int i=1;i<=8 && dir!=0;i++){ double oi=iOpen(_Symbol,_Period,i),ci=iClose(_Symbol,_Period,i); int d=(ci>oi?1:(ci<oi?-1:0)); if(d!=dir) break; seq++; } double candleSequence=MathAbs(seq); double extreme=" + if(data.getOrNull(9)=="PUT") "iLow(_Symbol,_Period,iLowest(_Symbol,_Period,MODE_LOW,20,1))" else "iHigh(_Symbol,_Period,iHighest(_Symbol,_Period,MODE_HIGH,20,1))" + "; double levelDistanceRatio=MathAbs(c-extreme)/range; MqlDateTime tm; TimeToStruct(iTime(_Symbol,_Period,1),tm); int utcHour=tm.hour;\n if(" + condition + ") Alert(\"Alçada: sinal " + if(data.getOrNull(9)=="PUT") "PUT" else "CALL" + " em \",_Symbol);\n}"
        "Lua" -> "-- " + warning + "-- Alçada - " + name + "\n-- atr14 deve ser a média simples de 14 True Ranges; sequence é a sequência consecutiva (máx. 8); extreme20 é resistência para CALL e suporte para PUT; utcHourValue deve estar em UTC.\nfunction sinal(o,h,l,c,pc,pc2,atr14,sequence,extreme20,utcHourValue)\n local range=math.max(h-l,0.000000000001)\n local body=math.max(math.abs(c-o),0.000000000001)\n local upperWick=h-math.max(o,c)\n local lowerWick=math.min(o,c)-l\n local wickBodyRatio=(upperWick+lowerWick)/body\n local bodyRangeRatio=math.abs(c-o)/range\n local closeLocation=(c-l)/range\n local momentumRangeRatio=math.abs(c-pc)/range\n local gapRangeRatio=math.abs(o-pc)/range\n local accelerationRangeRatio=pc2 and math.abs((c-pc)-(pc-pc2))/range or (0/0)\n local atrRangeRatio=atr14 and atr14/range or (0/0)\n local candleSequence=sequence and math.abs(sequence) or (0/0)\n local levelDistanceRatio=extreme20 and math.abs(c-extreme20)/range or (0/0)\n local utcHour=utcHourValue or -1\n return (" + condition + ")\nend"
    }
}

internal fun scriptRule(encoded: String, language: String, direction: String? = null): String? {
    val p = encoded.split(':')
    if (p.size != 3) return null
    val feature = p.getOrNull(0) ?: return null
    val op = p.getOrNull(1) ?: return null
    val numericValue = p.getOrNull(2)?.toDoubleOrNull() ?: return null
    if (!numericValue.isFinite() || op !in setOf(">=", "<=", ">", "<", "==")) return null
    val value = numericValue.toString()
    if (language == "TradingView" && feature == "sessionUtc") {
        if (op != "==" || numericValue !in listOf(1.0, 2.0, 3.0)) return null
        return when (numericValue.toInt()) {
            1 -> "(hour(time, \"UTC\") >= 0 and hour(time, \"UTC\") <= 6)"
            2 -> "(hour(time, \"UTC\") >= 7 and hour(time, \"UTC\") <= 12)"
            3 -> "(hour(time, \"UTC\") >= 13 and hour(time, \"UTC\") <= 20)"
            else -> null
        }
    }
    if (language != "TradingView") {
        val portable = setOf("wickBodyRatio","bodyRangeRatio","closeLocation","momentumRangeRatio","gapRangeRatio","accelerationRangeRatio","atrRangeRatio","candleSequence","levelDistanceRatio")
        if (feature in portable) return feature + " " + op + " " + value
        if (feature == "sessionUtc") {
            if (language == "MQL5" || op != "==" || numericValue !in listOf(1.0, 2.0, 3.0)) return null
            val join = " and "
            return when (numericValue.toInt()) {
                1 -> "(utcHour >= 0" + join + "utcHour <= 6)"
                2 -> "(utcHour >= 7" + join + "utcHour <= 12)"
                3 -> "(utcHour >= 13" + join + "utcHour <= 20)"
                else -> null
            }
        }
        return null
    }
    val expression = when (feature) {
        "wickBodyRatio" -> "(((high-math.max(open,close))+(math.min(open,close)-low))/math.max(math.abs(close-open),1e-12))"
        "bodyRangeRatio" -> "(math.abs(close-open)/math.max(high-low,1e-12))"
        "closeLocation" -> "((close-low)/math.max(high-low,1e-12))"
        "momentumRangeRatio" -> "(math.abs(close-close[1])/math.max(high-low,1e-12))"
        "gapRangeRatio" -> "(math.abs(open-close[1])/math.max(high-low,1e-12))"
        "accelerationRangeRatio" -> "(math.abs((close-close[1])-(close[1]-close[2]))/math.max(high-low,1e-12))"
        "atrRangeRatio" -> "(ta.sma(ta.tr(true),14)/math.max(high-low,1e-12))"
        "candleSequence" -> "math.abs(close>open ? (close[1]>open[1] ? (close[2]>open[2] ? (close[3]>open[3] ? (close[4]>open[4] ? (close[5]>open[5] ? (close[6]>open[6] ? (close[7]>open[7] ? 8.0 : 7.0) : 6.0) : 5.0) : 4.0) : 3.0) : 2.0) : 1.0) : (close<open ? (close[1]<open[1] ? (close[2]<open[2] ? (close[3]<open[3] ? (close[4]<open[4] ? (close[5]<open[5] ? (close[6]<open[6] ? (close[7]<open[7] ? -8.0 : -7.0) : -6.0) : -5.0) : -4.0) : -3.0) : -2.0) : -1.0) : 0.0))"
        "levelDistanceRatio" -> if (direction == "PUT") "(math.abs(close-ta.lowest(low,20))/math.max(high-low,1e-12))" else "(math.abs(ta.highest(high,20)-close)/math.max(high-low,1e-12))"
        else -> return null
    }
    return expression + " " + op + " " + value
}

private fun ruleDescription(encoded: String): String {
    val parts = encoded.split(':')
    val feature = when (parts.getOrNull(0)) {
        "wickBodyRatio" -> "pavio/corpo"
        "bodyRangeRatio" -> "corpo/amplitude"
        "closeLocation" -> "posição do fechamento"
        "spreadRangeRatio" -> "spread/amplitude"
        "sessionUtc" -> "sessão UTC"
        "atrRangeRatio" -> "volatilidade ATR/amplitude"
        "candleSequence" -> "sequência consecutiva de velas"
        "levelDistanceRatio" -> "distância até suporte/resistência"
        "accelerationRangeRatio" -> "aceleração/amplitude"
        "momentumRangeRatio" -> "movimento/amplitude"
        "gapRangeRatio" -> "gap/amplitude"
        else -> "condição quantitativa"
    }
    val operator = when (parts.getOrNull(1)) { ">=" -> "maior ou igual a"; "<=" -> "menor ou igual a"; ">" -> "maior que"; "<" -> "menor que"; else -> parts.getOrNull(1).orEmpty() }
    val threshold = parts.getOrNull(2)?.toDoubleOrNull()?.let { number(it) } ?: parts.getOrNull(2).orEmpty()
    return "$feature $operator $threshold"
}

@Composable
private fun HistoryBacktest(backtest: BacktestEntity) {
    var open by remember { mutableStateOf(false) }
    val data = backtest.metricsJson.split('|')
    val result = data.getOrNull(3)?.toDoubleOrNull()?.let(::number) ?: "—"
    ListItem(
        modifier = Modifier.clickable { open = true },
        headlineContent = { Text("Teste histórico " + marketLabel(backtest.market)) },
        supportingContent = { Text(date(backtest.createdAt) + " • " + (data.getOrNull(0) ?: "0") + " operações • resultado " + result + "\nToque para interpretar os números") },
        leadingContent = { Icon(Icons.Default.ShowChart, null) },
        trailingContent = { Icon(Icons.Default.Info, "Explicar resultado", tint = Analytic) },
    )
    if (open) AlertDialog(onDismissRequest={open=false}, title={Text("Interpretação do teste")}, text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Operações: " + (data.getOrNull(0) ?: "—"))
        Text("Taxa de acerto: " + pct(data.getOrNull(1)))
        Text("Fator de lucro: " + number(data.getOrNull(2)?.toDoubleOrNull() ?: Double.NaN))
        Text("Resultado: " + result)
        Text("Compare taxa de acerto, fator de lucro, resultado esperado e queda máxima em conjunto. Um único número alto não comprova estabilidade.", color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}, confirmButton={TextButton(onClick={open=false}){Text("Fechar")}})
}

@Composable private fun Hero(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)
                )
            ).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = Analytic.copy(alpha = .12f)) {
                    Text("ALÇADA • ANÁLISE LOCAL", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Analytic, style = MaterialTheme.typography.labelSmall)
                }
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.size(4.dp, 20.dp), shape = RoundedCornerShape(999.dp), color = Analytic) {}
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Metric(value: String, label: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(Modifier.size(7.dp), shape = RoundedCornerShape(999.dp), color = color) {}
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label.uppercase(brLocale), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
private fun metricExplanation(metric: String, metrics: BacktestMetrics): String = when (metric) {
    "Taxa de acerto" -> "Percentual de operações vencedoras: " + pct(metrics.winRate) + ". Em opções, compare com a taxa mínima de equilíbrio; taxa de acerto isolada não mede rentabilidade."
    "Resultado" -> "Soma do resultado das " + metrics.trades + " operações: " + number(metrics.netProfit) + ". Observe junto com queda máxima e distribuição."
    "Queda máxima" -> "Maior recuo acumulado desde um pico: " + number(metrics.maxDrawdown) + ". Ajuda a visualizar a pior sequência histórica e a necessidade de controle de risco."
    "Operações" -> "Amostra total: " + metrics.trades + " operações. Amostras pequenas têm maior incerteza; compare validação temporal e fora da amostra."
    "Fator de lucro" -> "Relação entre ganhos brutos e perdas brutas: " + number(metrics.profitFactor) + ". Acima de 1 indica vantagem histórica neste teste, sem garantir repetição futura."
    else -> "Média histórica por operação: " + number(metrics.expectancy) + ". Valor positivo indica resultado médio favorável nesta amostra; confirme estabilidade, custos e queda máxima."
}

@Composable private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = Analytic.copy(alpha = .10f)) { Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = Analytic) }
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
private fun pct(value: String?) = value?.toDoubleOrNull()?.let(::pct) ?: "—"
private val brLocale = Locale("pt", "BR")
private fun pct(value: Double): String = NumberFormat.getPercentInstance(brLocale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(value)
private fun number(value: Double): String = if (!value.isFinite()) "—" else NumberFormat.getNumberInstance(brLocale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
private fun date(value: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, brLocale).format(Date(value))
private fun directionLabel(value: String?): String = when (value) {
    "CALL" -> "CALL (alta)"
    "PUT" -> "PUT (baixa)"
    "LONG" -> "Compra"
    "SHORT" -> "Venda"
    else -> "Direção não informada"
}
private fun marketLabel(value: Any?): String = when (value?.toString()) {
    "BINARY_OPTIONS" -> "Opções binárias"
    "FOREX" -> "Forex"
    "CRYPTO" -> "Cripto"
    else -> value?.toString() ?: "—"
}
private fun profileLabel(value: Any?): String = when (value?.toString()) {
    "CONSERVATIVE" -> "Conservador"
    "MODERATE" -> "Moderado"
    "AGGRESSIVE" -> "Agressivo"
    "EXPERIMENTAL" -> "Experimental"
    else -> value?.toString() ?: "—"
}
private fun validationLabel(value: Any?): String = when (value?.toString()) {
    "VALIDATED" -> "validada"
    "FRAGILE" -> "frágil"
    "REJECTED" -> "rejeitada"
    "PENDING", null -> "pendente"
    else -> value.toString().lowercase(brLocale).replace('_', ' ')
}
private fun statusLabel(value: Any?): String = when (value?.toString()) {
    "RUNNING" -> "em andamento"
    "COMPLETED" -> "concluída"
    "CANCELLED", "CANCELED" -> "cancelada"
    "FAILED" -> "com falha"
    "PENDING" -> "pendente"
    else -> value?.toString()?.lowercase()?.replace('_', ' ') ?: "—"
}
