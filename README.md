# Chat Multiusuário com Histórico (Java 17 + Sockets + JDBC/H2)

## Pré-requisitos
- JDK 17 ou superior
- VS Code com **Extension Pack for Java** (inclui Maven for Java e Test Runner)

## Como rodar no VS Code
1. `File > Open Folder...` e abra a pasta `chat-multiusuario` (aguarde o Maven baixar H2 e JUnit).
2. Abra `src/main/java/server/Servidor.java` e clique em **Run** acima do `main`
   (cria o banco em `./data/chat` automaticamente).
3. Abra `src/main/java/client/ClienteChat.java` e clique em **Run** — repita 3 vezes
   (ou use 3 terminais). Em cada cliente: digite o nickname e depois as mensagens.
   `/quit` sai.
4. Feche um cliente (ou mate o terminal) e veja que os outros continuam.
   Entre com um novo cliente para ver o histórico.

## Pelo terminal (com Maven instalado)
```
mvn test                                              # testes
mvn compile exec:java -Dexec.mainClass=server.Servidor
mvn compile exec:java -Dexec.mainClass=client.ClienteChat      # em outros terminais
```
Cliente em outra máquina: `ClienteChat <ip> <porta>`.

## Cliente com janela temática + ChatBot
```
mvn compile exec:java -Dexec.mainClass=client.ClienteChatGui
```
Abre uma janela com balões de conversa, 3 temas (Noite, Pastel, Oceano, no canto superior direito)
e botões de pergunta rápida para o bot. No VS Code, abra `client/ClienteChatGui.java` e clique em **Run**.
Uma janela por usuário. Outra máquina: `-Dexec.args="<ip> 5000"`.

### ChatBot
O servidor tem um bot (`service/bot/BotProjeto`) que responde por palavras-chave sobre o projeto:
requisitos, arquitetura, concorrência, race conditions, broadcast, banco/JDBC, testes, protocolo,
desconexão, nickname, critérios de avaliação, cronograma, relatório e extras.
- Pergunte com `@bot ...` (ex.: `@bot como funciona a concorrência?`) ou use `@bot ajuda`.
- Ele também responde a saudações (`oi`) e a mensagens que citem esses assuntos.
- As respostas do bot são salvas no banco e transmitidas como as de qualquer usuário.
- O nickname `ChatBot` é reservado. Falha do bot nunca derruba o chat.
- Para desligá-lo, remova o último argumento (`new BotProjeto()`) do `ChatService` em `Servidor.main`.

### ChatBot gratuito (padrão, sem chave nem cadastro)
Sem `ANTHROPIC_API_KEY`, o servidor usa o `BotGratis`. Ele só age em mensagens com `@bot`:
- **Contas:** `@bot 12 * (3 + 4)`, `@bot quanto é 2^10?` (aceita + - * / ^ x e parênteses).
- **Hora e data:** `@bot que horas são?`, `@bot que dia é hoje?` (horário do servidor).
- **Definições (Wikipedia pt, gratuita):** `@bot o que é fotossíntese?`, `@bot quem foi Santos Dumont?`.
  Precisa de internet no servidor; se a Wikipedia falhar, o bot mostra uma dica de uso.
- **O projeto:** `@bot ajuda`, `@bot como funciona a concorrência?` (respostas do `BotProjeto`).

Não responde tudo: perguntas fora desses tipos recebem a dica de uso. Para respostas abertas, use a IA abaixo.
A consulta à Wikipedia roda em threads próprias (pool no `Servidor`), então o chat não trava enquanto espera.

### ChatBot com IA (opcional, pago: responde qualquer pergunta)
Com uma chave da API da Anthropic, o servidor troca o bot de palavras-chave por `BotIA`, que responde
**qualquer** pergunta iniciada com `@bot` e pode **pesquisar na web** (como o Google) quando precisa de
informação atual. Sem a chave, tudo continua funcionando com o `BotProjeto`.

1. Crie uma chave em https://console.anthropic.com (a API é paga por uso).
2. Defina a variável de ambiente **antes** de iniciar o servidor (nunca coloque a chave em arquivo do projeto):
   - Windows (PowerShell): `$env:ANTHROPIC_API_KEY="sk-ant-..."`
   - Linux/macOS: `export ANTHROPIC_API_KEY="sk-ant-..."`
   - VS Code: reinicie o VS Code depois de definir, ou use `"env"` no launch.json.
3. Rode o `Servidor`. Ele imprime `Bot com IA ativo` quando a chave foi encontrada.
4. No chat: `@bot qual a capital da Austrália?`, `@bot quem ganhou o jogo ontem?`, `@bot explique recursão`.

Ajustes em `chat.properties`: `ia.modelo` (padrão `claude-haiku-5-5`, rápido e barato) e `ia.busca-web`
(`false` desliga a pesquisa na web e reduz o custo).

Como funciona: `BotIA` (service/bot) lembra as últimas 8 falas, manda para um `ProvedorIA`
(`AnthropicProvedor` faz o HTTP) e devolve uma linha de até 500 caracteres. Se a IA falhar, responde com o
`BotProjeto`. A chamada demora segundos, por isso o `Servidor` passa um pool de threads ao `ChatService`:
o bot "pensa" fora do lock e só salvar + broadcast da resposta entra na seção crítica. Assim o chat não trava
enquanto a IA responde, e a ordem do banco continua igual à que todos veem.
Para usar outra IA, implemente `ProvedorIA`.

## Configuração
`src/main/resources/chat.properties` (porta, URL do banco, tamanho do histórico).
Sem o arquivo, valem os padrões: porta 5000, `jdbc:h2:./data/chat`, 20 mensagens.

## Acentos no Windows
Os streams de rede já usam UTF-8. Se o console exibir `?`, rode `chcp 65001`
antes de iniciar o cliente, ou use o terminal integrado do VS Code.

## Estrutura
- `model/` Usuario, Mensagem, MensagemInvalidaException
- `persistence/` MensagemRepository (interface), PersistenciaException
- `persistence/jdbc/` ConnectionFactory, JdbcMensagemRepository (único com java.sql)
- `service/` ChatService, ClientRegistry, ConexaoCliente (sem java.net/java.sql)
- `service/bot/` Bot (interface), BotProjeto (palavras-chave), BotGratis (+ Calculadora, Wikipedia), BotIA + ProvedorIA/AnthropicProvedor (IA)
- `server/` Servidor, ClienteHandler, Protocolo (único com Socket)
- `client/` ClienteChat
- `config/` Config
- `src/main/resources/schema.sql` script de criação da tabela

## Concorrência (resumo para o relatório)
- Uma thread por conexão (`ClienteHandler implements Runnable`).
- `ClientRegistry`: `CopyOnWriteArrayList`; broadcast itera sobre snapshot (sem lock durante o envio).
- `ClienteHandler.enviar()` é `synchronized`: evita linhas intercaladas no mesmo `PrintWriter`.
- Race condition 1: nickname duplicado (check-then-act) -> `adicionar()` atômico.
- Race condition 2: ordem salvar x broadcast -> `ChatService.enviarMensagem` é seção crítica única
  (trade-off: menos paralelismo; ordem do banco == ordem vista pelos clientes).
- Uma `Connection` JDBC por operação (try-with-resources); nunca compartilhada.
- Falha de envio a um cliente remove só ele; os demais continuam.
