# Retro Vanta — implementação original

Aplicativo Android independente, escrito para este projeto e sem Lemuroid, RetroArch ou Libretro como base. A interface mostra os consoles planejados, mas esta versão só tem um núcleo de Game Boy original (DMG) em desenvolvimento; as outras opções estão marcadas como indisponíveis.

## Esta versão

- Menu com Game Boy/Color/Advance, SNES, Mega Drive, Master System/Mega System, Atari, PC Engine, Neo Geo Pocket/Color e arcade FBNeo/MAME. Só Game Boy DMG pode ser iniciado nesta build.
- Visual azul-marinho/neon, joystick virtual direcional, quatro botões A/B/X/Y espaçados (X replica A; Y replica B no hardware DMG) e Select/Start.
- Núcleo DMG próprio em Java, em progresso, com interpretação inicial da CPU LR35902, temporizador/interrupções básicas, memória, renderização de fundo e sprites, e suporte inicial para ROM-only/MBC1/MBC2/MBC3/MBC5.
- Pode abrir ROM `.gb` e ZIP contendo `.gb` ou `.gbc`; ROMs exclusivas de Game Boy Color são informadas como incompatíveis.
- O título no cabeçalho é mostrado quando disponível; tamanho, arquivo inválido e tipo de cartucho ainda não suportado geram aviso em vez de abrir silenciosamente.

## Limitações

- O emulador continua experimental: não há áudio, janela completa, relógio RTC, compatibilidade integral de instruções nem certificação de todos os jogos. Alguns títulos/hacks ainda podem falhar.
- Não dá para identificar com certeza toda ROM hack sem uma base de hashes. Hacks com cabeçalho/controlador compatível podem iniciar, mas não há garantia.
- Game Boy Color, GBA, NES, SNES, Mega Drive, Master System, Atari, PC Engine, sistemas SNK e arcade ainda não têm núcleo funcional nesta build.
- PS1 e PSP permanecem fora do escopo.

O app não distribui ROMs nem BIOS. Use somente arquivos que você tenha direito de utilizar.
