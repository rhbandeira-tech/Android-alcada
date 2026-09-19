# Alçada

Aplicativo Android de pesquisa quantitativa e backtesting local, por Ricardo Bandeira.

O motor de pesquisa roda no próprio Android. GitHub Actions é usado somente para testar e compilar o APK.

## Base entregue
- Jetpack Compose, tema escuro e navegação principal.
- Quatro perfis: Conservador, Moderado, Agressivo e Experimental.
- Núcleo de geração combinatória de estratégias por pavio/corpo, direção e expiração.
- Backtest de opções binárias com payout configurável no motor.
- Importador CSV inicial OHLC.
- Testes unitários de payout e features de candle.
- Workflow GitHub Actions para gerar APK automaticamente.

> Resultados de backtest são históricos e não garantem desempenho futuro.
