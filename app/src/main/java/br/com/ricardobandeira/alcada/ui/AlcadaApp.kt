package br.com.ricardobandeira.alcada.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
    val strategies by vm.strategies.collectAsState(); val backtests by vm.backtests.collectAsState(); val datasets by vm.datasets.collectAsState()
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

@Composable private fun DiscoverScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { val state by vm.researchState.collectAsState(); val strategies by vm.strategies.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Hero("Descobrir padrões", "Pavios, movimento e validação dentro e fora da amostra") }; item { DatasetSelector(datasets, selected, vm::selectDataset) }; item { ActionPanel(state, "Pesquisa local", "2.000 candidatos • análise local • filtro por amostra e fator de lucro", { vm.runResearch() }, vm::cancelResearch) }; if(strategies.isNotEmpty()) { item { SectionTitle("Descobertas persistidas", "Classificação considera robustez e expectativa") }; items(strategies) { StrategyCard(it) } } else item { EmptyState(Icons.Default.AutoGraph, "Ainda sem descobertas", "A busca mantém somente candidatos com amostra mínima e validação OOS.") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BacktestScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { var market by rememberSaveable { mutableStateOf(Market.BINARY_OPTIONS) }; var payout by rememberSaveable { mutableFloatStateOf(.8f) }; var expiration by rememberSaveable { mutableFloatStateOf(3f) }; val state by vm.backtestState.collectAsState(); val result by vm.result.collectAsState(); val analysis by vm.analysis.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Hero("Teste histórico", "Sinais por assimetria de pavios • sem execução de ordens") }; item { DatasetSelector(datasets, selected, vm::selectDataset) }; item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { Market.entries.take(2).forEachIndexed { i, item -> SegmentedButton(market==item,{market=item},SegmentedButtonDefaults.itemShape(i,2)){Text(if(item==Market.BINARY_OPTIONS) "Opções" else "Forex")}} }; if(market==Market.BINARY_OPTIONS){ Text("Retorno ${(payout*100).toInt()}% • taxa mínima para equilíbrio ${pct(1.0/(1+payout))}"); Slider(payout,{payout=it}, valueRange=.5f..1f); Text("Expiração ${expiration.toInt()} velas"); Slider(expiration,{expiration=it},valueRange=1f..10f,steps=8) } else Text("Forex: limite de perda (SL) 0,002 • objetivo de ganho (TP) 0,004 • proteção móvel 0,0015 • saída em 20 velas") } } }; item { ActionPanel(state,"Executar teste","Custos, retorno e direção são calculados pelo aplicativo",{ vm.runBacktest(BacktestOptions(market=market, expiration=expiration.toInt(), payout=payout.toDouble())) },vm::cancelBacktest) }; result?.let { value -> item { Metrics(value.metrics) }; item { ResultCharts(value, analysis) } } ?: item { EmptyState(Icons.Default.QueryStats,"Sem resultado nesta sessão","Execute o teste; o resultado também será salvo no histórico.") } }
}

@Composable private fun MoreScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){ uris -> vm.importFiles(uris) }; val importState by vm.importState.collectAsState(); val backtests by vm.backtests.collectAsState(); val runs by vm.runs.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) { item { Hero("Dados e histórico","Tudo permanece no dispositivo") }; item { SectionTitle("Dados locais","Selecione um ou vários arquivos CSV/ZIP. Os dados são processados somente no aparelho.") }; item { Button(onClick={launcher.launch(arrayOf("text/csv","text/comma-separated-values","application/zip","application/x-zip-compressed","text/plain"))},Modifier.fillMaxWidth(), enabled=!importState.running){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("Selecionar CSV ou ZIP")}; if(importState.running){ LinearProgressIndicator(progress={importState.progress},modifier=Modifier.fillMaxWidth()); OutlinedButton(onClick=vm::cancelImport){Text("Cancelar importação")} }; Status(importState) }; items(datasets){ d -> DatasetRow(d,d.id==selected){vm.selectDataset(d.id)} }; item { SectionTitle("Histórico","Resultados persistem após a limpeza do cache bruto") }; if(backtests.isEmpty()&&runs.isEmpty()) item { EmptyState(Icons.Default.History,"Histórico vazio","Backtests e pesquisas concluídos aparecerão aqui.") }; items(backtests){ HistoryBacktest(it) }; items(runs){ run -> ListItem(headlineContent={Text("Pesquisa ${statusLabel(run.status)}")},supportingContent={Text("${run.progress}% • semente ${run.seed} • ${date(run.startedAt)}")},leadingContent={Icon(Icons.Default.Science,null)}) }; item { SectionTitle("Configurações e privacidade","Cálculos locais • cache bruto expira após 7 dias"); Text("Dukascopy e agenda econômica permanecem desativados até uma fonte confiável ser configurada. Nenhuma ordem real é executada.",color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun DatasetRow(dataset: DatasetEntity, selected: Boolean, onSelect: () -> Unit) {
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
        }
    }
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric(pct(metrics.winRate), "taxa de acerto", Analytic, Modifier.weight(1f))
        Metric(number(metrics.netProfit), "resultado", if (metrics.netProfit >= 0) Positive else Negative, Modifier.weight(1f))
        Metric(number(metrics.maxDrawdown), "queda máxima", Negative, Modifier.weight(1f))
    }
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
            BarChart("Mapa de desempenho: dia × hora", it.heatmap)
            BarChart("Dentro da amostra × fora da amostra", it.isOos)
            BarChart("Período gráfico × expiração", it.timeframeExpiration)
            BarChart("Desempenho por ativo", it.assets)
            BarChart("Proporção do pavio × movimento futuro", it.wick)
        }
    }
}

@Composable
private fun LineChart(title: String, values: List<Double>, color: Color) {
    ChartCard(title) {
        if (values.size < 2) {
            Text("Dados insuficientes para este gráfico", color = Pending)
        } else {
            Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                val minimum = values.min()
                val range = max(1e-9, values.max() - minimum)
                values.zipWithNext().forEachIndexed { index, (start, end) ->
                    drawLine(
                        color = color,
                        start = Offset(size.width * index / (values.size - 1), size.height * (1 - ((start - minimum) / range)).toFloat()),
                        end = Offset(size.width * (index + 1) / (values.size - 1), size.height * (1 - ((end - minimum) / range)).toFloat()),
                        strokeWidth = 4f,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChart(title: String, values: List<Bucket>) {
    ChartCard(title) {
        if (values.isEmpty()) {
            Text("Amostra insuficiente", color = Pending)
        } else {
            val maxValue = max(1e-9, values.maxOf { kotlin.math.abs(it.value) })
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                values.forEach { bucket ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height((90 * kotlin.math.abs(bucket.value) / maxValue).toFloat().dp),
                            color = if (bucket.value >= 0) Positive else Negative,
                        ) {}
                        Text(bucket.label.take(4), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, fontWeight = FontWeight.Bold); content() } }
}

@Composable
private fun StrategyCard(strategy: StrategyEntity) {
    val data = strategy.definitionJson.split('|')
    val profitFactor = data.getOrNull(3)?.toDoubleOrNull()?.let(::number) ?: "—"
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(strategy.name, fontWeight = FontWeight.Bold)
                Text(profileLabel(strategy.profile), color = Analytic)
            }
            Text("${strategy.symbol} • ${marketLabel(strategy.market)}")
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Dentro ${pct(data.getOrNull(1))}")
                Text("Fora ${pct(data.getOrNull(2))}")
                Text("Fator de lucro $profitFactor")
            }
            Text(
                "Robustez ${pct(data.getOrNull(6))} • ${validationLabel(data.getOrNull(7))}",
                color = if (data.getOrNull(8) == "true") Negative else Positive,
            )
        }
    }
}

@Composable
private fun HistoryBacktest(backtest: BacktestEntity) {
    val data = backtest.metricsJson.split('|')
    val result = data.getOrNull(3)?.toDoubleOrNull()?.let(::number) ?: "—"
    ListItem(
        headlineContent = { Text("Teste histórico ${marketLabel(backtest.market)}") },
        supportingContent = { Text("${date(backtest.createdAt)} • ${data.getOrNull(0) ?: 0} operações • resultado $result") },
        leadingContent = { Icon(Icons.Default.ShowChart, null) },
    )
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
@Composable private fun Metric(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(Modifier.size(7.dp), shape = RoundedCornerShape(999.dp), color = color) {}
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(label.uppercase(brLocale), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
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
private fun number(value: Double): String = NumberFormat.getNumberInstance(brLocale).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)
private fun date(value: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, brLocale).format(Date(value))
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
