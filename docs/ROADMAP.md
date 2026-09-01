# Roadmap â ZalithLauncher3

Cada fase vive na sua branch; a principal recebe merges estabilizados.

## Fase 0 â APK principal (branch main)
- [x] Projeto Android (Gradle + Kotlin) mÃ­nimo viÃ¡vel
- [x] Estrutura de telas e navegaÃ§Ã£o base
- [x] PermissÃµes, armazenamento scoped e diretÃ³rio de dados
- [x] Build debug/release assinado

## Fase 1 â Multiplayer (branch multiplayer)
- [x] Gerenciador de contas: offline (nome/UUID/skin local) e Microsoft
- [x] Login offline sem depender de serviÃ§o externo
- [x] Lista de servidores com ping, versÃ£o e favoritos
- [x] SessÃ£o persistente e troca rÃ¡pida de perfil

## Fase 2 â Minecraft Java (branch minecraft-java)
- [x] Leitura do version manifest (Mojang) e download de versÃµes
- [x] Loaders por instÃ¢ncia: Fabric, Forge, Quilt
- [x] .minecraft isolado por instÃ¢ncia (libs e config separados)
- [x] Runtime Java por versÃ£o do jogo (8 / 17 / 21)

## Fase 3 â Melhorias e otimizaÃ§Ã£o (branch melhorias)
- [x] Presets de flags JVM conforme a RAM do aparelho
- [x] Ajuste automÃ¡tico de -Xmx e GC (G1GC / ZGC)
- [x] Download paralelo + verificaÃ§Ã£o incremental de bibliotecas
- [x] Cache de assets/shaders e diagnÃ³stico local de FPS/latÃªncia
