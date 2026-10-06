# Retro Vanta — projeto independente

O aplicativo e a interface são próprios e não usam Lemuroid, RetroArch ou Libretro como base. O app atualmente integra Game Boy DMG e NES com suporte limitado; os outros sistemas abaixo têm apenas módulos de código ainda sem integração ou validação geral. Nenhum deles deve ser anunciado como compatível com todos os jogos.

## Núcleos integrados no menu

- **Game Boy DMG:** CPU LR35902 parcial, temporização, interrupções, renderização de fundo/sprites e carregamento de `.gb`/ZIP; suporte inicial a ROM-only, MBC1/2/3/5. Sem áudio/RTC completo e sem garantia para todas as ROMs/hacks.
- **NES:** iNES 1.0, mapper 0 (NROM) e mapper 2 (UxROM), CPU 6502 e quadro 256×240. Sem áudio/APU; PPU/timing aproximados; outros mappers são recusados.

## Módulos presentes no código, ainda não disponíveis no menu

- **Game Boy Color:** execução DMG/CGB inicial, VRAM/WRAM bancados e paletas CGB, com testes gerados; ainda precisa validação ampla e integração.
- **SNES:** somente perfil LoROM padrão sem coprocessadores; CPU 65C816 parcial e um plano BG1 Mode 0; sem sprites, áudio, DMA/HDMA ou integração.
- **Master System:** subset Z80/VDP para cartuchos pequenos e alguns bancos; temporização/áudio e instruções ainda incompletos.
- **Atari 2600:** cartuchos raw 2/4 KiB, subset 6507 e TIA aproximada; sem garantia de compatibilidade ampla.

## Pendências

- Executar testes/compilação dos módulos recentes, corrigir falhas e integrá-los somente após validação.
- Implementar GBA, SNES completo, Mega Drive, Master System completo, demais Atari, PC Engine, SNK e arcade; definir o escopo para mappers/placas.
- Compatibilidade com ROM hacks depende do cartucho e recursos de hardware implementados; não há garantia universal.
- PS1, PSP e consoles modernos ficam fora do escopo.
- O app não inclui ROMs nem BIOS. Use somente arquivos que você tenha direito de usar.
