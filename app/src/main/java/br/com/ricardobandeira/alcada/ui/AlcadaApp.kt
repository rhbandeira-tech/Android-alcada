package br.com.ricardobandeira.alcada.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.ricardobandeira.alcada.domain.Profile
import br.com.ricardobandeira.alcada.ui.theme.*

private enum class Page(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Home), DISCOVER("Descobrir", Icons.Default.Search), BACKTEST("Backtest", Icons.Default.ShowChart),
    DATA("Dados", Icons.Default.Storage), HISTORY("Histórico", Icons.Default.History), STRATEGY("Estratégia", Icons.Default.Tune), SETTINGS("Configurações", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlcadaApp() {
    var page by rememberSaveable { mutableStateOf(Page.DASHBOARD) }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("ALÇADA", fontWeight = FontWeight.Black); Text("Pesquisa quantitativa local • Ricardo Bandeira", style = MaterialTheme.typography.labelSmall, color = Analytic) } }) },
        bottomBar = { NavigationBar { Page.entries.forEach { item -> NavigationBarItem(page == item, { page = item }, { Icon(item.icon, item.title) }, label = { Text(item.title, maxLines = 1) }) } } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { when (page) {
        Page.DASHBOARD -> Dashboard(onNavigate = { page = it })
        Page.DISCOVER -> Discover()
        Page.BACKTEST -> Placeholder("Backtest", "Teste Forex com SL/TP/trailing/custos ou opções com expiração e payout.")
        Page.DATA -> Placeholder("Dados", "Importe CSV em streaming ou configure a fonte histórica Dukascopy. Cache bruto expira em 7 dias.")
        Page.HISTORY -> Placeholder("Histórico", "Pesquisas, estratégias e validações permanecem após a limpeza dos dados brutos.")
        Page.STRATEGY -> Explanation()
        Page.SETTINGS -> Settings()
    } } }
}

@Composable private fun Dashboard(onNavigate: (Page) -> Unit) {
    val profiles = Profile.entries
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Laboratório", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Resultados históricos e experimentais — não constituem promessa de retorno.", color = Pending) }
        item { MetricStrip() }
        item { Text("Melhores estratégias por perfil", style = MaterialTheme.typography.titleLarge) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(profiles) { ProfileColumn(it, onNavigate) } } }
        item { Text("Visão de robustez", style = MaterialTheme.typography.titleLarge); MiniChart() }
    }
}

@Composable private fun MetricStrip() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("12" to "selecionadas", "0" to "em execução", "LOCAL" to "motor").forEach { (v,l) -> Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text(v, color = Analytic, fontWeight = FontWeight.Bold); Text(l, style = MaterialTheme.typography.labelSmall) } } } } }

@Composable private fun ProfileColumn(profile: Profile, onNavigate: (Page) -> Unit) {
    val title = when(profile) { Profile.CONSERVATIVE -> "Conservador"; Profile.MODERATE -> "Moderado"; Profile.AGGRESSIVE -> "Agressivo"; Profile.EXPERIMENTAL -> "Experimental" }
    Column(Modifier.width(280.dp)) { Text(title, color = when(profile){ Profile.CONSERVATIVE -> Positive; Profile.MODERATE -> Analytic; Profile.AGGRESSIVE -> Pending; else -> Negative }, fontWeight = FontWeight.Bold)
        repeat(3) { rank -> Card(onClick = { onNavigate(Page.STRATEGY) }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("#${rank+1} Aguardando pesquisa", fontWeight = FontWeight.SemiBold); AssistChip(onClick = {}, label = { Text("Pendente") }) }
            Text("Execute Descobrir para avaliar sem look-ahead", style = MaterialTheme.typography.bodySmall); HorizontalDivider(); Text("Amostra —  •  OOS —  •  PF —", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        } } }
    }
}

@Composable private fun Discover() { var running by remember { mutableStateOf(false) }; var progress by remember { mutableFloatStateOf(0f) }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Descoberta automática", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text("Geração → avaliação rápida → poda → mutação → validação", color = Analytic) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Pesquisa intensiva", fontWeight = FontWeight.Bold); Text("2 threads • 256 MB • seed 42 • orçamento 10.000"); LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth()); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { running = !running; progress = if (running) .08f else progress }) { Icon(if(running) Icons.Default.Pause else Icons.Default.PlayArrow, null); Text(if(running) "Pausar" else "Iniciar") }; OutlinedButton(onClick = { running=false; progress=0f }) { Text("Cancelar") } }; Text("A execução real é agendada em trabalho persistente; cancelamento é cooperativo.", style = MaterialTheme.typography.bodySmall) } } }
        item { Text("Validação: divisão temporal IS/OOS, walk-forward, Monte Carlo, custos, múltiplos períodos e ativos. Ranking combina expectativa, drawdown e robustez — nunca apenas win rate.") }
    }
}

@Composable private fun Placeholder(title: String, text: String) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Card { Text(text, Modifier.padding(20.dp)) }; Text("Módulo preparado para evolução incremental.", color = Analytic) } }
@Composable private fun Explanation() { LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Explicar esta estratégia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }; items(listOf("Regras e entradas" to "Condições serializadas com timeframe, direção, filtros e saída.", "Evidência histórica" to "Amostra, trades, custos, expectativa e períodos desfavoráveis.", "Validação OOS" to "Dados posteriores e nunca vistos são comparados ao período de ajuste.", "Fragilidades e riscos" to "Sensibilidade de parâmetros, drawdown, estabilidade e possível overfitting.")) { (a,b) -> Card { Column(Modifier.padding(16.dp)) { Text(a, fontWeight = FontWeight.Bold); Text(b) } } } } }
@Composable private fun Settings() = Placeholder("Configurações", "Threads, orçamento, memória aproximada, bateria e limites térmicos serão aplicados ao agendamento WorkManager.")
@Composable private fun MiniChart() { Card(Modifier.fillMaxWidth().height(150.dp)) { Canvas(Modifier.fillMaxSize().padding(18.dp)) { val points = listOf(.2f,.35f,.3f,.55f,.5f,.72f,.68f,.82f); points.zipWithNext().forEachIndexed { i,(a,b) -> drawLine(Positive, Offset(size.width*i/(points.size-1), size.height*(1-a)), Offset(size.width*(i+1)/(points.size-1), size.height*(1-b)), 5f) } } } }
