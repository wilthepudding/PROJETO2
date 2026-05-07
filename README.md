# PROJETO2

Projeto Android: **Piano MIDI Overlay**.

O app permite importar arquivos MIDI/MID, pesquisar músicas no Online Sequencer, salvar músicas em uma biblioteca local e tocar notas por meio de uma janela flutuante com coordenadas calibradas.

## Recursos

- Importação de arquivos MIDI/MID pelo seletor do Android.
- Pesquisa e download de MIDIs do Online Sequencer.
- Biblioteca local de músicas baixadas.
- Janela flutuante com controles de tocar, pausar, parar, selecionar música, calibrar e fechar.
- Serviço de acessibilidade usado para enviar toques apenas nas coordenadas calibradas das teclas.

## Permissões usadas

- Internet: pesquisar e baixar MIDIs.
- Janela flutuante: mostrar os controles por cima de outros apps.
- Serviço em primeiro plano: manter a janela flutuante ativa enquanto o usuário usa o app.
- Acessibilidade: simular toques nas teclas calibradas.
- Notificações: exibir a notificação do serviço em primeiro plano em Android 13+.

## Segurança e limites

- O WebView de busca é limitado ao domínio `onlinesequencer.net`.
- Downloads MIDI são validados antes de entrar na biblioteca.
- Arquivos MIDI muito grandes são recusados.
- O parser MIDI rejeita arquivos inválidos, truncados ou incompatíveis.

## Como compilar

Abra o projeto no Android Studio e rode o módulo `app`.

Configuração atual:

- Gradle Android Plugin 8.7.3
- compileSdk 35
- minSdk 26
- targetSdk 35
