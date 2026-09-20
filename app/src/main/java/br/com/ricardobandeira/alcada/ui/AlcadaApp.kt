package br.com.ricardobandeira.alcada.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.ricardobandeira.alcada.data.*
import br.com.ricardobandeira.alcada.domain.*
import br.com.ricardobandeira.alcada.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

private enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home), DISCOVER("Analisar", Icons.Default.AutoGraph), BACKTEST("Backtest", Icons.Default.ShowChart), MORE("Mais", Icons.Default.MoreHoriz)
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

@Composable private fun Brand() { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Logo(30.dp); Column { Text("ALÇADA", fontWeight = FontWeight.Black, letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing); Text("QUANT LAB", style = MaterialTheme.typography.labelSmall, color = Analytic) } } }
@Composable private fun Logo(dimension: androidx.compose.ui.unit.Dp) { Canvas(Modifier.size(dimension)) { val p = Path().apply { moveTo(size.width*.08f,size.height*.78f); lineTo(size.width*.35f,size.height*.5f); lineTo(size.width*.54f,size.height*.64f); lineTo(size.width*.9f,size.height*.18f) }; drawPath(p, Positive, style=androidx.compose.ui.graphics.drawscope.Stroke(width=size.width*.12f)); drawLine(Analytic, Offset(size.width*.62f,size.height*.18f), Offset(size.width*.9f,size.height*.18f), size.width*.09f); drawLine(Analytic, Offset(size.width*.9f,size.height*.18f), Offset(size.width*.9f,size.height*.46f), size.width*.09f) } }

@Composable private fun HomeScreen(vm: AlcadaViewModel) {
    val strategies by vm.strategies.collectAsState(); val backtests by vm.backtests.collectAsState(); val datasets by vm.datasets.collectAsState()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Hero("Laboratório quantitativo", "Processamento local • resultados históricos, nunca promessas") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("${datasets.size}", "datasets", Analytic, Modifier.weight(1f)); Metric("${strategies.size}", "estratégias", Positive, Modifier.weight(1f)); Metric("${backtests.size}", "backtests", Pending, Modifier.weight(1f)) } }
        item { SectionTitle("Estratégias por perfil", "Top 3 por robustez quando validadas") }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(Profile.entries) { profile -> ProfilePanel(profile, strategies.filter { it.profile == profile.name }.sortedByDescending { it.definitionJson.split('|').getOrNull(6)?.toDoubleOrNull() ?: 0.0 }.take(3)) } } }
        if (strategies.isEmpty()) item { EmptyState(Icons.Default.Science, "Nenhuma estratégia validada", "Importe dados e execute uma pesquisa. Métricas não serão inventadas.") }
    }
}

@Composable private fun ProfilePanel(profile: Profile, values: List<StrategyEntity>) { val color = when(profile){ Profile.CONSERVATIVE->Positive; Profile.MODERATE->Analytic; Profile.AGGRESSIVE->Pending; Profile.EXPERIMENTAL->Negative }; val title=profile.name.lowercase().replaceFirstChar { it.uppercase() }
    Card(Modifier.width(292.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title, color=color, fontWeight=FontWeight.Bold); if(values.isEmpty()) Text("Em validação • sem resultados suficientes", color=MaterialTheme.colorScheme.onSurfaceVariant, style=MaterialTheme.typography.bodySmall) else values.forEach { strategy -> val data=strategy.definitionJson.split('|'); Surface(shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(12.dp)) { Text(strategy.name, fontWeight=FontWeight.Bold); Text("${strategy.symbol} • ${strategy.market}", style=MaterialTheme.typography.labelSmall); Text("OOS ${pct(data.getOrNull(2))}  •  robustez ${pct(data.getOrNull(6))}", color=color) } } } } }
}

@Composable private fun DiscoverScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { val state by vm.researchState.collectAsState(); val strategies by vm.strategies.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Hero("Descobrir padrões", "Pavios, momentum e validação temporal IS/OOS") }; item { DatasetSelector(datasets, selected, vm::selectDataset) }; item { ActionPanel(state, "Pesquisa local", "2.000 candidatos • seed 42 • pruning por amostra e PF", { vm.runResearch() }, vm::cancelResearch) }; if(strategies.isNotEmpty()) { item { SectionTitle("Descobertas persistidas", "Classificação considera robustez e expectativa") }; items(strategies) { StrategyCard(it) } } else item { EmptyState(Icons.Default.AutoGraph, "Ainda sem descobertas", "A busca mantém somente candidatos com amostra mínima e validação OOS.") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BacktestScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { var market by rememberSaveable { mutableStateOf(Market.BINARY_OPTIONS) }; var payout by rememberSaveable { mutableFloatStateOf(.8f) }; var expiration by rememberSaveable { mutableFloatStateOf(3f) }; val state by vm.backtestState.collectAsState(); val result by vm.result.collectAsState(); val analysis by vm.analysis.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { Hero("Backtest", "Sinais por assimetria de pavios • sem execução de ordens") }; item { DatasetSelector(datasets, selected, vm::selectDataset) }; item { Card { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) { Market.entries.take(2).forEachIndexed { i, item -> SegmentedButton(market==item,{market=item},SegmentedButtonDefaults.itemShape(i,2)){Text(if(item==Market.BINARY_OPTIONS) "Opções" else "Forex")}} }; if(market==Market.BINARY_OPTIONS){ Text("Payout ${(payout*100).toInt()}% • break-even ${pct(1.0/(1+payout))}"); Slider(payout,{payout=it}, valueRange=.5f..1f); Text("Expiração ${expiration.toInt()} velas"); Slider(expiration,{expiration=it},valueRange=1f..10f,steps=8) } else Text("Forex: SL 0,002 • TP 0,004 • trailing 0,0015 • saída 20 velas") } } }; item { ActionPanel(state,"Executar backtest","Custos, payout e direção são calculados financeiramente",{ vm.runBacktest(BacktestOptions(market=market, expiration=expiration.toInt(), payout=payout.toDouble())) },vm::cancelBacktest) }; result?.let { value -> item { Metrics(value.metrics) }; item { ResultCharts(value, analysis) } } ?: item { EmptyState(Icons.Default.QueryStats,"Sem resultado nesta sessão","Execute o backtest; o resultado também será salvo no histórico.") } }
}

@Composable private fun MoreScreen(vm: AlcadaViewModel, datasets: List<DatasetEntity>, selected: String?) { val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ it?.let(vm::importCsv) }; val importState by vm.importState.collectAsState(); val backtests by vm.backtests.collectAsState(); val runs by vm.runs.collectAsState()
    LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) { item { Hero("Dados e histórico","Tudo permanece no dispositivo") }; item { SectionTitle("Dados locais","CSV: timestamp,open,high,low,close[,volume,spread]") }; item { Button(onClick={launcher.launch(arrayOf("text/csv","text/comma-separated-values","text/plain"))},Modifier.fillMaxWidth()){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("Importar CSV")}; Status(importState) }; items(datasets){ d -> DatasetRow(d,d.id==selected){vm.selectDataset(d.id)} }; item { SectionTitle("Histórico","Resultados persistem após a limpeza do cache bruto") }; if(backtests.isEmpty()&&runs.isEmpty()) item { EmptyState(Icons.Default.History,"Histórico vazio","Backtests e pesquisas concluídos aparecerão aqui.") }; items(backtests){ HistoryBacktest(it) }; items(runs){ run -> ListItem(headlineContent={Text("Pesquisa ${run.status}")},supportingContent={Text("${run.progress}% • seed ${run.seed} • ${date(run.startedAt)}")},leadingContent={Icon(Icons.Default.Science,null)}) }; item { SectionTitle("Configurações e privacidade","Cálculos locais • cache bruto expira após 7 dias"); Text("Dukascopy e agenda econômica permanecem desativados até um provider confiável ser configurado. Nenhuma ordem real é executada.",color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable private fun DatasetSelector(values:List<DatasetEntity>,selected:String?,select:(String)->Unit){ Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Dataset",fontWeight=FontWeight.Bold); if(values.isEmpty()) Text("Importe um CSV em Mais › Dados",color=Pending) else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(values){d->FilterChip(d.id==selected,{select(d.id)},{Text("${d.name} • ${d.rowCount}")})}} } } }
@Composable private fun ActionPanel(state:OperationState,title:String,subtitle:String,start:()->Unit,cancel:()->Unit){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(state.running)LinearProgressIndicator({state.progress},Modifier.fillMaxWidth());Status(state);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(start,enabled=!state.running){Icon(Icons.Default.PlayArrow,null);Text("Iniciar")};if(state.running)OutlinedButton(cancel){Icon(Icons.Default.Stop,null);Text("Cancelar")}}}}}
@Composable private fun Status(state:OperationState){state.message?.let{Text(it,color=if(state.running)Analytic else Positive,style=MaterialTheme.typography.bodySmall)};state.error?.let{Text(it,color=Negative,style=MaterialTheme.typography.bodySmall)}}
@Composable private fun Metrics(m:BacktestMetrics){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric(pct(m.winRate),"win rate",Analytic,Modifier.weight(1f));Metric("%.2f".format(m.netProfit),"resultado",if(m.netProfit>=0)Positive else Negative,Modifier.weight(1f));Metric("%.2f".format(m.maxDrawdown),"drawdown",Negative,Modifier.weight(1f))}}
@Composable private fun ResultCharts(result:BacktestResult,analysis:QuantAnalysis?){Column(verticalArrangement=Arrangement.spacedBy(12.dp)){LineChart("Equity / lucro acumulado",result.equity,Positive);LineChart("Drawdown",result.drawdown,Negative);LineChart("Rolling win rate",result.rollingWinRate,Analytic);BarChart("Distribuição dos trades",ChartAnalytics.pnlDistribution(result.trades));BarChart("Desempenho por hora (UTC)",ChartAnalytics.performanceByHour(result.trades));BarChart("Desempenho por dia",ChartAnalytics.performanceByDay(result.trades));analysis?.let{BarChart("Heatmap dia × hora",it.heatmap);BarChart("In-sample × out-of-sample",it.isOos);BarChart("Timeframe × expiração",it.timeframeExpiration);BarChart("Desempenho por ativo",it.assets);BarChart("Proporção do pavio × movimento futuro",it.wick)}}}
@Composable private fun LineChart(title:String,values:List<Double>,color:Color){ChartCard(title){if(values.size<2)Text("Amostra insuficiente",color=Pending)else Canvas(Modifier.fillMaxWidth().height(130.dp)){val min=values.min();val range=max(1e-9,values.max()-min);values.zipWithNext().forEachIndexed{i,(a,b)->drawLine(color,Offset(size.width*i/(values.size-1),size.height*(1-((a-min)/range)).toFloat()),Offset(size.width*(i+1)/(values.size-1),size.height*(1-((b-min)/range)).toFloat()),4f)}}}}
@Composable private fun BarChart(title:String,values:List<Bucket>){ChartCard(title){if(values.isEmpty())Text("Amostra insuficiente",color=Pending)else {val maxValue=max(1e-9,values.maxOf{ kotlin.math.abs(it.value)});Row(Modifier.fillMaxWidth().height(120.dp),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.Bottom){values.forEach{b->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.fillMaxWidth().height((90*kotlin.math.abs(b.value)/maxValue).toFloat().dp),contentAlignment=Alignment.BottomCenter){Surface(Modifier.fillMaxSize(),color=if(b.value>=0)Positive else Negative){}};Text(b.label.take(4),style=MaterialTheme.typography.labelSmall)}}}}}}
@Composable private fun ChartCard(title:String,content:@Composable ColumnScope.()->Unit){Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,fontWeight=FontWeight.Bold);content()}}}
@Composable private fun StrategyCard(s:StrategyEntity){val d=s.definitionJson.split('|');Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(s.name,fontWeight=FontWeight.Bold);Text(s.profile.lowercase(),color=Analytic)};Text("${s.symbol} • ${s.market}");Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Text("IS ${pct(d.getOrNull(1))}");Text("OOS ${pct(d.getOrNull(2))}");Text("PF ${d.getOrNull(3)?.toDoubleOrNull()?.let{"%.2f".format(it)}?:"—"}")};Text("Robustez ${pct(d.getOrNull(6))} • ${d.getOrNull(7)?:"pendente"}",color=if(d.getOrNull(8)=="true")Negative else Positive)}}
@Composable private fun HistoryBacktest(b:BacktestEntity){val d=b.metricsJson.split('|');ListItem(headlineContent={Text("Backtest ${b.market}")},supportingContent={Text("${date(b.createdAt)} • ${d.getOrNull(0)?:0} trades • resultado ${d.getOrNull(3)?.toDoubleOrNull()?.let{"%.2f".format(it)}?:"—"}")},leadingContent={Icon(Icons.Default.ShowChart,null)})}
@Composable private fun Hero(title:String,subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun SectionTitle(title:String,subtitle:String){Column{Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun Metric(value:String,label:String,color:Color,modifier:Modifier=Modifier){Card(modifier){Column(Modifier.padding(12.dp)){Text(value,color=color,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleLarge);Text(label,style=MaterialTheme.typography.labelSmall)}}}
@Composable private fun EmptyState(icon:ImageVector,title:String,body:String){Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(24.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,tint=Analytic);Text(title,fontWeight=FontWeight.Bold);Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
private fun pct(value:String?)=value?.toDoubleOrNull()?.let(::pct)?:"—"
private fun pct(value:Double)="%.1f%%".format(value*100)
private fun date(value:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(value))
