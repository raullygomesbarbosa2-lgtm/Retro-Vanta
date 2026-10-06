# Retro Vanta — projeto independente

O aplicativo e a interface são próprios; não usam Lemuroid, RetroArch ou Libretro como base. O menu integra Game Boy DMG e NES apenas neste ramo de desenvolvimento, com compatibilidade limitada. Os demais módulos listados abaixo estão isolados, ainda não foram integrados ao menu, e não devem ser anunciados como compatíveis com todos os jogos.

## Núcleos integrados no menu

- **Game Boy DMG:** CPU LR35902 parcial, renderização e carregamento `.gb`/ZIP; suporte inicial a ROM-only e alguns tipos de cartucho. Compatibilidade com jogos e hacks varia.
- **NES:** iNES 1.0, mapper 0 (NROM) e mapper 2 (UxROM); sem APU, PPU/timing aproximados e outros mappers recusados.

## Módulos isolados e limitações

- **Game Boy Color:** caminho DMG/CGB inicial, bancos de VRAM/WRAM e paletas; exige validação e integração.
- **SNES:** perfil LoROM simplificado, CPU parcial e BG1 Mode 0; sem sprites, áudio, DMA/HDMA ou coprocessadores.
- **Master System:** subconjunto de Z80/VDP e cartuchos com alguns bancos; recursos e temporização incompletos.
- **Atari 2600:** raw 2/4 KiB, subconjunto 6507 e TIA aproximada.
- **Game Boy Advance:** subset Thumb e modos bitmap 3/4/5. Sem ARM state, BIOS, tile/sprites, áudio, DMA, timers, save hardware ou timing ciclo a ciclo.
- **Mega Drive/Genesis:** subset MC68000, acesso básico ao VDP e plano A simples. Sem sprites, áudio, DMA, interrupções ou ampla compatibilidade.
- **Neo Geo Pocket:** o módulo presente é apenas um intérprete diagnóstico para testes gerados, não implementa a CPU TLCS-900H e **não roda ROMs comerciais normais**; NGPC também não tem cores implementadas.

## Pendências

- Reexecutar testes/compilação após corrigir o erro encontrado no núcleo Master System; validar cada módulo antes de qualquer integração ou APK.
- Implementar núcleos realmente funcionais para os demais sistemas pretendidos (incluindo GBA, Mega Drive, PC Engine, SNK e arcade) e definir escopo realista de cartuchos, mappers e placas.
- Compatibilidade com ROM hacks depende do hardware implementado; não há garantia universal.
- PS1, PSP e consoles modernos ficam fora do escopo.
- O app não inclui ROMs nem BIOS. Use somente arquivos que você tenha direito de usar.
