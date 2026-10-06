# Retro Vanta — projeto independente

O aplicativo e a interface são próprios; não usam Lemuroid, RetroArch ou Libretro como base. Este branch de desenvolvimento integra dois núcleos Java escritos para o projeto: Game Boy DMG (em progresso) e NES iNES 1.0 com mappers 0/2 (NROM/UxROM). O restante do catálogo ainda não está implementado e não deve ser anunciado como funcional.

## Integração atual

- Menu de sistemas: Game Boy original e NES podem receber seleção de ROM; outros consoles são bloqueados até haver um núcleo real.
- Leitura de arquivos ZIP: procura `.gb`/`.gbc` para Game Boy ou `.nes` para NES; cada núcleo valida o cabeçalho do cartucho.
- Controles na tela: direcional, A/B, X/Y duplicando A/B no Game Boy, Select e Start; o mapeamento é adaptado ao NES.
- NES: leitura iNES 1.0, mapper 0 (NROM) e mapper 2 (UxROM), CPU oficial 6502 e renderização de quadro 256×240. Sem áudio/APU; temporização e renderização PPU são aproximadas. NES 2.0 e outros mappers são recusados com aviso.

## Ainda pendente

- A compilação/testes desta integração precisam passar antes de considerar os núcleos integrados.
- Compatibilidade ampla com todos os jogos e ROM hacks não está garantida; depende do mapper e dos recursos de hardware implementados.
- Game Boy Color, GBA, SNES, Mega Drive, Master System, Atari, PC Engine, sistemas SNK e arcade ainda precisam de implementação própria.
- PS1, PSP e consoles modernos ficam fora do escopo.
- O app não inclui ROMs nem BIOS. Use apenas arquivos que você tenha direito de usar.
