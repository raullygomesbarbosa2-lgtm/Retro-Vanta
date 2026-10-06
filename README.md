# Retro Vanta — projeto independente

O app e a interface são próprios; não usam Lemuroid, RetroArch ou Libretro como base. A integração de seleção de ROM e controle na tela atualmente reconhece Game Boy DMG e NES (iNES 1.0, mappers 0/2). Este branch interno também contém núcleos isolados para GBC, Master System e Atari 2600, além de um protótipo SNES. Esses módulos isolados ainda não estão integrados ao menu e não devem ser tratados como suporte pronto.

## Módulos escritos para o projeto

- Game Boy DMG: implementação inicial em Java, com carregamento de ZIP `.gb/.gbc`, mas compatibilidade ampla ainda não comprovada.
- NES: iNES 1.0, NROM/UxROM (mappers 0/2), CPU 6502 e frame de 256×240. Sem APU; temporização/renderização aproximadas.
- Game Boy Color: CGB separado, com bancos de memória, paletas e composição básica de BG/window/sprites; sem áudio, DMA completo, RTC MBC3, MBC2, velocidade dupla ou compatibilidade comercial comprovada.
- Master System: cartucho cru pequeno e alguns bancos Sega, Z80/VDP aproximados; faltam instruções/opcodes, timing, áudio e alguns mapeadores.
- Atari 2600: imagens de cartucho cru de 2 ou 4 KiB, subconjunto do 6507 e renderização TIA aproximada; sem áudio, colisões ou bancos com troca.
- SNES: protótipo isolado LoROM/Mode 0, poucas instruções 65C816 e fundo BG1 4bpp; sem sprites, áudio/APU, DMA/HDMA, interrupções, SRAM ou coprocessadores.

## Estado e validação

- Há testes JUnit com cartuchos gerados para os módulos. Só os testes executados em CI contam como verificados; protótipos sem teste concluído continuam não validados.
- Game Boy Color, SNES, Master System e Atari ainda não estão selecionáveis no app. Mega Drive, GBA, outros sistemas Atari, PC Engine, SNK/Neo Geo e arcade continuam sem núcleo implementado.
- Nenhuma compatibilidade universal com ROM hacks é prometida: formatos, mapeadores e recursos do hardware variam.
- PS1, PSP e consoles modernos ficam fora do escopo. O app não inclui ROMs nem BIOS; use apenas arquivos que você tenha direito de usar.
