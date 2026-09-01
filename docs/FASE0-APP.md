# Fase 0 — App (branch main)

Casca Android do launcher: telas, tema preto/vermelho, ícone novo,
tempo de jogo e visualizador de logs ao vivo. O deploy desta fase
também une as três branchs (multiplayer, minecraft-java, melhorias)
na main.

## Telas

| tela | papel |
|------|-------|
| INÍCIO (HomeFragment) | conta ativa, tempo hoje/total, HUD de jank, turbo, instâncias, iniciar rápido |
| CONTAS (AccountsFragment) | lista, criar conta offline, login Microsoft por device code, troca com toque e remoção com toque longo |
| LOGS (LogsFragment) | latest.log em tempo real, filtros (tudo/aviso/erro), autoscroll que pausa quando você sobe para ler |

## Por que não trava

- Views clássicas + RecyclerView + DiffUtil (sem Compose: APK menor,
  zero overhead de recomposição)
- fragments escondidos/mostrados — trocar de aba nunca recria tela
- IO em threads de fundo; a UI só recebe listas prontas
- itemAnimator nulo (animações de insert/remove não engolem frames)
- buffer de log limitado a 2000 linhas
- windowBackground preto: overdraw zero
- Choreographer monitora frames engolidos e mostra no HUD

## Modo turbo

Ligado por padrão (otimização máxima). Na integração de launch, força
o preset PERFORMANCE do JvmFlags (fase 3) e mantém o HUD ativo.

## O que foi inventado a mais

1. iniciar rápido (última instância com um toque)
2. HUD de jank do próprio launcher (fps + frames engolidos)
3. autoscroll do log que pausa quando você sobe para ler
4. filtros de nível no log ao vivo
5. estatísticas de tempo de jogo (hoje/total/por instância)
6. trocar conta com toque, remover com toque longo

## Build

Abrir o projeto no Android Studio (JDK 17), sincronizar o Gradle e
gerar o APK. minify + shrinkResources ligados no release. O gradle
wrapper não vem no repo: o Studio gera na primeira abertura, ou rode
"gradle wrapper" na raiz.

## Pendências (próxima entrega)

- ponte JNI + JREs empacotados (jre8/16/17/21) → INICIAR fica funcional
- agente Java que escreve zl3-perf.json (HUD de FPS do jogo)
- processors do Forge
- MicrosoftAuth.clientId (portal.azure.com)

## Integração com as fases (mescladas na main)

- contas: com.zalith.launcher.multiplayer.accounts
- instâncias: com.zalith.launcher.instances
- otimização: com.zalith.launcher.perf (turbo → preset PERFORMANCE)
