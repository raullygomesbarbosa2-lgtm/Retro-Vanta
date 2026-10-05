# Retro Vanta — implementação original

Aplicativo Android independente, escrito para este projeto e sem Lemuroid, RetroArch ou Libretro como base. Esta versão é um protótipo experimental do Game Boy original (DMG); ainda não é o emulador multissistema planejado.

## O que esta versão faz

- Interface azul-marinho/neon, seletor de ROM e controles táteis desenhados do zero: alavanca virtual com retorno direcional, botões A/B espaçados e Select/Start.
- Núcleo DMG próprio em Java, com interpretação inicial da CPU LR35902, memória, cartuchos ROM-only/MBC1 e renderização básica de tiles de fundo.
- Mostra o título do cartucho quando existe no cabeçalho e dá mensagens para arquivos inválidos ou controles de cartucho não suportados.
- Limite de arquivo: 8 MB. ROMs ZIP não são descompactadas nesta versão.

## Limitações importantes

- Os controles traduzem o movimento da alavanca em direções digitais, pois o Game Boy original não tem analógico.
- Compatibilidade ainda é incompleta: sem áudio, sprites e interrupções completos, Game Boy Color, SNES, arcade/SNK ou outros sistemas.
- Não é possível identificar com certeza se um arquivo é ROM hack sem uma base de dados de hashes; hacks que preservam hardware e cabeçalho compatíveis podem funcionar, mas não há garantia.
- ROMs com controladores de cartucho diferentes de ROM-only e MBC1 são recusadas com aviso, em vez de tentar iniciar e falhar silenciosamente.
- PS1 e PSP continuam fora do escopo.

O app não distribui ROMs nem BIOS. Use apenas arquivos que você tenha direito de utilizar.

## Build

GitHub Actions compila APK de depuração e publica releases de teste. Este APK é experimental e não deve ser anunciado como compatível com todo o catálogo.
