# Retro Vanta — projeto independente

O aplicativo e a interface são próprios; não usam Lemuroid, RetroArch ou Libretro como base. Este branch integra dois núcleos Java do projeto: Game Boy DMG (em progresso) e NES iNES 1.0 com mappers 0/2 (NROM/UxROM). Há também um protótipo SNES isolado, ainda sem integração no app e sem alegação de compatibilidade com jogos comerciais. O restante do catálogo não está implementado e não deve ser anunciado como funcional.

## Integração atual

- Menu de sistemas: Game Boy original e NES podem receber seleção de ROM; outros consoles são bloqueados até haver um núcleo real.
- Leitura de arquivos ZIP: procura `.gb`/`.gbc` para Game Boy ou `.nes` para NES; cada núcleo valida o cabeçalho do cartucho.
- Controles na tela: direcional, A/B, X/Y duplicando A/B no Game Boy, Select e Start; o mapeamento é adaptado ao NES.
- NES: leitura iNES 1.0, mapper 0 (NROM) e mapper 2 (UxROM), CPU oficial 6502 e renderização de quadro 256×240. Sem áudio/APU; temporização e renderização PPU são aproximadas. NES 2.0 e outros mappers são recusados com aviso.

## Ainda pendente

- O protótipo SNES isolado em `SnesCore.java` aceita somente imagens LoROM padrão em bancos de 32 KiB, sem chips especiais. Implementa um subconjunto de instruções 65C816 em modo emulação, leitura/escrita básica de WRAM e um plano BG1 Mode 0 com tiles 4bpp, mapa 32×32, seleção de paleta, scroll, CGRAM e registrador automático de joypad. Os testes JUnit incluem cartuchos gerados para backdrop, entrada, operações de CPU e upload/renderização/scroll de tiles. Não renderiza sprites e não implementa áudio, DMA/HDMA, interrupções, SRAM ou coprocessadores; a CPU e a PPU são apenas fatias simplificadas e isso não é compatibilidade geral com jogos SNES. Continua fora do menu e do `EmulatorCore`.
- Os testes SNES foram ampliados, mas ainda precisam ser executados em ambiente com JDK e Gradle/Android configurados; o ambiente de edição atual não tem Java nem Gradle disponíveis.
- Compatibilidade ampla com todos os jogos e ROM hacks não está garantida; depende do mapper e dos recursos de hardware implementados.
- Game Boy Color, GBA (além do protótipo SNES isolado), Mega Drive, Master System, Atari, PC Engine, sistemas SNK e arcade ainda precisam de implementação própria.
- PS1, PSP e consoles modernos ficam fora do escopo.
- O app não inclui ROMs nem BIOS. Use apenas arquivos que você tenha direito de usar.
