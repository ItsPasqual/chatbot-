package server;

import config.Config;
import persistence.jdbc.ConnectionFactory;
import persistence.jdbc.JdbcMensagemRepository;
import service.ChatService;
import service.ClientRegistry;
import service.bot.AnthropicProvedor;
import service.bot.Bot;
import service.bot.BotGratis;
import service.bot.BotIA;
import service.bot.BotProjeto;
import service.bot.Wikipedia;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Aceita conexões e cria uma thread por cliente. main() é o ponto de montagem. */
public class Servidor {

    private final int porta;
    private final ChatService chatService;
    private volatile ServerSocket serverSocket;

    public Servidor(int porta, ChatService chatService) {
        this.porta = porta;
        this.chatService = chatService;
    }

    /** Bloqueia no laço de accept() até stop() ser chamado. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(porta);
        System.out.println("[servidor] Escutando na porta " + serverSocket.getLocalPort());
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                Thread t = new Thread(new ClienteHandler(socket, chatService),
                        "cliente-" + socket.getRemoteSocketAddress());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (serverSocket.isClosed()) {
                    break;
                }
                System.err.println("[servidor] Erro no accept(): " + e.getMessage());
            }
        }
    }

    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[servidor] Erro ao fechar o ServerSocket: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        Config config = Config.carregar();

        ConnectionFactory factory = new ConnectionFactory(
                config.dbUrl(), config.dbUsuario(), config.dbSenha());
        JdbcMensagemRepository repository = new JdbcMensagemRepository(factory);
        repository.inicializarSchema();

        BotProjeto botProjeto = new BotProjeto();
        // Os dois bots consultam a internet (demoram): pensam em threads próprias para não travar o chat.
        Executor botExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "bot");
            t.setDaemon(true);
            return t;
        });
        Bot bot;
        if (!config.iaApiKey().isEmpty()) {
            bot = new BotIA(new AnthropicProvedor(config.iaApiKey(), config.iaModelo(), config.iaBuscaWeb()),
                    botProjeto);
            System.out.println("[servidor] Bot com IA ativo (modelo " + config.iaModelo()
                    + ", busca web: " + config.iaBuscaWeb() + ")");
        } else {
            bot = new BotGratis(botProjeto, new Wikipedia(), Clock.systemDefaultZone());
            System.out.println("[servidor] Bot gratuito ativo (contas, hora, Wikipedia e projeto).");
        }

        ChatService chatService = new ChatService(
                repository, new ClientRegistry(), Clock.systemDefaultZone(), config.tamanhoHistorico(),
                bot, botExecutor);

        Servidor servidor = new Servidor(config.porta(), chatService);
        Runtime.getRuntime().addShutdownHook(new Thread(servidor::stop));
        servidor.start();
    }
}
