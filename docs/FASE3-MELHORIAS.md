# Fase 3 — Melhorias e otimização (branch melhorias)

Camada de desempenho: flags de JVM por aparelho, verificação
incremental, cache de shaders com cota, coleta de assets órfãos e
diagnóstico local. Zero dependências (org.json nativo). Módulos
autocontidos: esta branch compila sozinha, sem importar código das
branchs multiplayer e minecraft-java.

## Módulos

| caminho | papel |
|---------|-------|
| perf/DeviceProfile.kt | RAM/núcleos/tier via /proc/meminfo, sem API Android |
| perf/JvmFlags.kt | presets economy/balanced/performance/zgc + heap automático |
| perf/IncrementalVerifier.kt | pula re-hash com size+mtime; milhares de arquivos em ms |
| perf/AssetGarbageCollector.kt | apaga objetos de assets que ninguém referencia |
| perf/ShaderCache.kt | shaderpacks guardados 1x por conteúdo, cota LRU 2GB |
| perf/LaunchStats.kt | tempos de resolve/download/verify/launch por sessão |
| perf/GameLogAnalyzer.kt | digest do latest.log: níveis, pausas de GC, lag |
| perf/FpsProbe.kt | lê zl3-perf.json (contrato com o agente Java da fase 0) |
| perf/PerfReport.kt | relatório consolidado (texto + json) para a UI |

Pacote: com.zalith.launcher.perf
Fonte: core/src/main/java/com/zalith/launcher/perf/…

## Flags por aparelho

Tier por RAM: LOW <4GB · MID 4-6GB · HIGH 8GB+.

| preset | gc | heap | para quem |
|--------|----|------|-----------|
| economy | SerialGC | <=1024MB | aparelho fraco |
| balanced | G1GC | 2048MB | padrão |
| performance | G1GC | 4096MB | aparelho forte |
| zgc | ZGC | 4096MB | experimental |

- heap nunca passa de 60% da RAM total (o Android precisa do resto)
- boost por mods: +128MB a cada 8 mods, teto +768MB
- flags estilo Aikar adaptadas a cliente com mods: MaxGCPauseMillis,
  ParallelRefProcEnabled, UseStringDeduplication, G1NewSizePercent,
  G1ReservePercent, G1HeapRegionSize (heap >= 3GB), DisableExplicitGC,
  PerfDisableSharedMem, metaspace com teto
- ZGC é opt-in e precisa de JRE que o inclua (jre21): validar antes
  de expor na UI; o padrão fica no G1

## Verificação incremental

O Downloader (fase 2) já evita re-baixar o íntegro; o gargalo
restante era conferir SHA-1 de milhares de arquivos a cada launch. O
IncrementalVerifier grava size+mtime de cada arquivo conferido:
bateram, hash pulado. Re-hash só quando muda de verdade.

Uso: load() → verify(tasks) → commit(). repair=true apaga corrompidos
para o Downloader re-baixar.

## Espaço em disco

- AssetGarbageCollector: objetos órfãos (de versões desinstaladas)
  saem com collect(referenced). Dry-run para a UI mostrar quanto vai
  liberar antes de apagar.
- ShaderCache: zip guardado uma vez por hash; cota 2GB com LRU;
  activate() copia para o shaderpacks/ da instância.

## Diagnóstico

- LaunchStats: por sessão, tempos de resolve/download/verify/launch
  (histórico de 30) em stats.json
- GameLogAnalyzer: níveis, pausas de GC (com -Xlog:gc), marcadores de
  lag no latest.log
- FpsProbe: contrato com o lado Java — o agente do runtime (fase 0)
  escreve zl3-perf.json no game dir {fps, frameMs, heapUsedMb,
  heapMaxMb}; enquanto não existe, read() devolve null
- PerfReport: junta tudo (texto/json) para o painel da UI

## Integração (quando as branchs se encontrarem na main)

- GameSetup (fase 2) chama IncrementalVerifier entre downloads e launch
- LaunchArguments (fase 2) recebe JvmFlags.recommend().flags no topo
  dos jvmArgs: flags de perf primeiro, depois as da versão
- a UI (fase 0) expõe preset automático + override manual de heap

## Pendências honestas

- agente Java do FpsProbe (fase 0/runtime)
- validar ZGC no JRE empacotado antes de liberar o preset
- forge processors continuam na fase de runtime (já sinalizado na
  documentação da fase 2)
