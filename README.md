# RetroRaiz

Aplicativo Android de emulação retrô derivado do projeto aberto [Lemuroid](https://github.com/Swordfish90/Lemuroid), com cores Libretro e interface para organizar e jogar arquivos de jogos do próprio usuário.

## Sistemas incluídos

- Atari 2600, Atari 7800 e Atari Lynx
- NES e Super Nintendo
- Game Boy, Game Boy Color e Game Boy Advance
- Master System, Game Gear, Mega Drive/Genesis e Sega CD
- PC Engine
- Neo Geo Pocket e Neo Geo Pocket Color
- WonderSwan e WonderSwan Color
- Arcade por FBNeo e MAME 2003 Plus (o suporte a jogos Neo Geo depende do formato e do conjunto de arquivos aceito pelo core)

PlayStation 1, PSP, Nintendo 64, Nintendo DS/3DS e DOS ficam fora da versão RetroRaiz.

## ROMs

O aplicativo não inclui ROMs, BIOS ou jogos. O usuário seleciona os próprios arquivos. ROMs comuns e ROM hacks podem funcionar quando usam um formato aceito pelo core correspondente; a compatibilidade de cada hack depende do jogo-base e da alteração feita.

## APK

O fluxo do GitHub Actions compila um APK Android de teste e publica builds em Releases. A compilação usa o código do Lemuroid fixado no commit `93e321d08f3dc0776bb0545da975d2cd7cd1f78f` e o submódulo público de cores. O APK inicial é assinado com a chave de depuração do Android, apropriado para instalação manual e teste; não é uma versão da Play Store.

## Licença e créditos

RetroRaiz é uma adaptação do Lemuroid e mantém a licença GNU GPL v3 e os avisos de copyright aplicáveis. Consulte `COPYING`, o repositório upstream e as licenças dos cores de terceiros. RetroRaiz não é afiliado à Nintendo, Sony, Sega ou SNK.
