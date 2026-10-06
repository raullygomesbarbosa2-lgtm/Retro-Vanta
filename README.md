# Retro Vanta — projeto independente

O aplicativo e os núcleos são implementação própria, sem Lemuroid, RetroArch ou Libretro. Este ramo de desenvolvimento já expõe no menu Game Boy DMG, GBC, NES, GBA, Mega Drive, Master System e Atari 2600. **A etiqueta “experimental/limitado” é importante:** testes de fumaça e compilação não comprovam compatibilidade ampla com jogos comerciais ou ROM hacks; ainda faltam recursos de hardware em vários núcleos. Nenhuma ROM ou BIOS é incluída.

## Sistemas selecionáveis neste ramo

- **Game Boy DMG:** CPU LR35902 parcial e suporte limitado a tipos de cartucho; sem áudio/RTC completo.
- **Game Boy Color:** caminho DMG/CGB inicial, bancos de VRAM/WRAM e paletas; compatibilidade geral ainda não validada.
- **NES:** iNES 1.0, mapper 0 (NROM) e mapper 2 (UxROM); sem APU e com PPU/timing aproximados.
- **Game Boy Advance:** subset Thumb e bitmap modes 3/4/5; sem ARM state/BIOS, sprites, áudio, DMA, timers ou saves.
- **Mega Drive/Genesis:** subset MC68000, acesso básico ao VDP e plano A; sem sprites, áudio, DMA, interrupções e vários recursos.
- **Master System:** subset Z80/VDP e mapeamento de cartucho incompleto.
- **Atari 2600:** cartuchos raw de 2/4 KiB, subset 6507 e TIA aproximada.

## Ainda indisponíveis

- **SNES:** há um protótipo LoROM simplificado fora do menu; sem compatibilidade geral.
- **Neo Geo Pocket/Color:** o módulo existente é apenas intérprete diagnóstico de ROMs geradas; não implementa a CPU TLCS-900H nem roda ROMs comuns.
- **PC Engine e arcade (FBNeo/MAME):** sem núcleos integrados.
- **Atari 7800/Lynx, demais cartuchos, coprocessadores e placas arcade:** fora dos módulos atuais.

ZIP seleciona a ROM pela extensão compatível com o sistema escolhido. Suporte a ROM hacks varia de acordo com o mapper e os recursos do hardware implementados; incompatibilidades devem mostrar erro em vez de prometer funcionamento universal.
