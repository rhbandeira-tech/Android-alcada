# Alçada

**Pesquisa quantitativa e backtesting local para Android, por Ricardo Bandeira.**

Alçada é um laboratório mobile, não um robô de execução de ordens. Todo cálculo quantitativo acontece no aparelho; a automação do GitHub somente testa e produz o APK. Resultados são históricos/experimentais e **não garantem retorno futuro**.

## Estado funcional

- Aplicativo Kotlin/Compose Material 3, tema grafite, navegação Dashboard/Descobrir/Backtest/Dados/Histórico/Estratégia/Configurações e painel com 3 posições em cada um dos quatro perfis.
- Domínio independente do Android para candles, features, regras serializáveis, opções binárias e Forex (SL, TP, trailing, saída temporal, spread/custo).
- Pesquisa evolucionária determinística por seed e em `Flow`: gera e avalia candidatos de forma incremental, poda por requisitos mínimos e mantém somente o melhor, sem materializar o espaço combinatório.
- Features iniciais: geometria OHLC, corpo, pavios, razões pavio/corpo e pavio/range, range, ATR, volatilidade, gap e momentum. O limite temporal exclusivo das janelas evita leitura do futuro.
- CSV processado como `Sequence` de chunks; metadados de dataset separados do cache bruto.
- Room para pesquisas, estratégias, backtests, validações, datasets, eventos econômicos e configurações. A limpeza WorkManager remove apenas arquivos brutos sem apagar resultados.
- Interfaces explícitas para Dukascopy e agenda econômica. Nenhuma URL, chave ou API não confirmada foi inventada.
- Testes unitários para features, fronteira temporal, payout/break-even e importação em chunks.

## Arquitetura

```text
ui/       Compose e identidade visual; nunca executa loops quantitativos na main thread
domain/   modelos, features, backtest e pesquisa (`Dispatchers.Default` + cancelamento cooperativo)
data/     Room, CSV e contratos de provedores externos
work/     manutenção persistente do cache local
```

A estratégia contém mercado, ativo, timeframe, direção, regras de entrada, regra de saída e seed. Resultados distinguem win rate, expectativa, profit factor, drawdown, break-even e robustez OOS. O ranking planejado por perfil pondera robustez e risco; jamais deve ordenar somente pela taxa de acerto.

## Dados e reprodução

O formato CSV esperado é `timestamp,open,high,low,close[,volume,spread]`, com timestamp Unix em milissegundos. A leitura é incremental (padrão 4.096 linhas). Dados brutos não usados há sete dias são removidos, mas metadados e resultados permanecem. “Reproduzir pesquisa” deve resolver o dataset pelo metadado e baixá-lo novamente pelo `HistoricalDataProvider`.

### Integrações ainda externas

O usuário/integrador deve configurar uma implementação documentada de `HistoricalDataProvider` para Dukascopy e de `EconomicCalendarProvider` para NFP, CPI, juros, FOMC, ECB, PIB, PMI, desemprego e discursos. O modelo da agenda já prevê moeda, impacto, instante, actual, forecast, previous e surpresa; o repositório não contém credenciais nem supõe um fornecedor.

## Build

Requisitos: Android SDK 35 e JDK 17.

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`. O repositório não versiona o Gradle Wrapper porque o seu JAR é um binário; `.github/workflows/android.yml` provisiona declarativamente o Gradle 8.9, executa os mesmos comandos e publica `alcada-debug-apk` como artefato instalável do workflow, sem adicioná-lo ao Git.

## Próximas evoluções sem romper o núcleo

- Cliente Dukascopy escolhido/configurado pelo responsável pelo produto e provider de calendário econômico confiável.
- UI do seletor de documento e persistência do pipeline completo de pesquisa em foreground WorkManager.
- Walk-forward, Monte Carlo, múltiplos ativos/timeframes e gráficos detalhados (equity, drawdown, distribuição, heatmap, rolling win rate e matrizes).
- Instrumentação de bateria/thermal status e benchmark em aparelhos representativos para calibrar automaticamente threads e memória.
