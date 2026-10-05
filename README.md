# Retro Vanta — implementação original

Este repositório inicia uma implementação Android independente, sem usar Lemuroid, RetroArch ou Libretro como base. A primeira compilação é um protótipo experimental para Game Boy original (DMG), escrito para este projeto. Ainda não representa o emulador multissistema completo: novos consoles serão implementados e testados por etapas.

## Estado desta versão

- App Android próprio com seletor de ROM local.
- Núcleo DMG inicial escrito em Java, com CPU LR35902, memória/cartridge ROM e desenho básico do plano de fundo por tiles.
- Suporte inicial a cartuchos ROM-only e MBC1; compatibilidade ainda limitada e não validada com todo o catálogo. Game Boy Color ainda não é emulado.
- Controles virtuais simples; áudio, salvamento, Game Boy Color, sprites avançados, SNES e arcade/SNK ainda não implementados.
- PS1 e PSP ficam fora do escopo.

O projeto não distribui ROMs, BIOS nem jogos. Use apenas arquivos que você tenha direito de usar.

## Build

O GitHub Actions compila um APK Android de depuração e o publica como release de teste. Como é um protótipo, alguns jogos podem não iniciar ou apresentar gráficos/controles incompletos.

## Licença

O código original deste protótipo está sob MIT. Consulte `LICENSE`.
