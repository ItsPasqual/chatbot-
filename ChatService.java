package service;

import model.Mensagem;
import model.Usuario;
import persistence.MensagemRepository;
import service.bot.Bot;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Regras do chat. Não conhece sockets nem SQL.
 *
 * Concorrência: conectar, desconectar e enviarMensagem usam o mesmo lock (this).
 * Assim "salvar + broadcast" é uma seção crítica única: a ordem gravada no banco é
 * a mesma que todos os clientes veem, e um cliente que entra não perde nem recebe
 * fora de ordem mensagens enviadas entre o histórico e o registro.
 * Trade-off: perde-se paralelismo no envio (um cliente lento pode atrasar os demais).
 *
 * O bot (opcional) com executor direto responde na mesma seção crítica (logo após a pergunta).
 * Com um executor assíncrono a resposta chega quando ficar pronta, mas continua sendo salva e
 * transmitida de forma atômica, então a ordem do banco ainda é a que todos veem.
 */
public class ChatService {

    private static final Logger LOG = Logger.getLogger(ChatService.class.getName());

    private final MensagemRepository repository;
    private final ClientRegistry registry;
    private final Clock clock;
    private final int tamanhoHistorico;
    private final Bot bot; // pode ser null
    private final Executor botExecutor;

    public ChatService(MensagemRepository repository, ClientRegistry registry,
                       Clock clock, int tamanhoHistorico) {
        this(repository, registry, clock, tamanhoHistorico, null);
    }

    public ChatService(MensagemRepository repository, ClientRegistry registry,
                       Clock clock, int tamanhoHistorico, Bot bot) {
        this(repository, registry, clock, tamanhoHistorico, bot, Runnable::run);
    }

    /**
     * botExecutor decide ONDE o bot pensa. Com Runnable::run (padrão) ele responde na própria
     * thread, dentro do lock: previsível e bom para testes. Com um pool, bots lentos (IA via rede)
     * não travam o chat; só o "salvar + broadcast" da resposta entra na seção crítica.
     */
    public ChatService(MensagemRepository repository, ClientRegistry registry,
                       Clock clock, int tamanhoHistorico, Bot bot, Executor botExecutor) {
        this.repository = repository;
        this.registry = registry;
        this.clock = clock;
        this.tamanhoHistorico = tamanhoHistorico;
        this.bot = bot;
        this.botExecutor = botExecutor;
    }

    /** Registra a conexão, envia o histórico e avisa a entrada. */
    public synchronized void conectar(ConexaoCliente conexao) {
        if (bot != null && conexao.getUsuario().equals(bot.getUsuario())) {
            throw new NicknameEmUsoException(conexao.getUsuario().nome()); // nome reservado
        }
        registry.adicionar(conexao); // pode lançar NicknameEmUsoException
        try {
            for (Mensagem m : buscarHistorico(tamanhoHistorico)) {
                conexao.enviar(m.formatar());
            }
        } catch (FalhaEnvioException e) {
            registry.remover(conexao);
            throw e;
        }
        broadcast("* " + conexao.getUsuario().nome() + " entrou no chat");
        saudarComBot(conexao);
    }

    /** Idempotente: o aviso de saída só é enviado se a conexão ainda estava registrada. */
    public synchronized void desconectar(ConexaoCliente conexao) {
        if (registry.remover(conexao)) {
            broadcast("* " + conexao.getUsuario().nome() + " saiu do chat");
        }
    }

    /** Cria e valida → salva → broadcast → (bot responde). */
    public synchronized Mensagem enviarMensagem(Usuario remetente, String texto) {
        Mensagem nova = Mensagem.nova(remetente, texto, LocalDateTime.now(clock));
        Mensagem salva = repository.salvar(nova);
        broadcast(salva.formatar());
        botResponde(remetente, salva.getConteudo());
        return salva;
    }

    public List<Mensagem> buscarHistorico(int quantidade) {
        return repository.buscarUltimas(quantidade);
    }

    public List<Usuario> usuariosOnline() {
        return registry.usuariosOnline();
    }

    private void saudarComBot(ConexaoCliente conexao) {
        if (bot == null) {
            return;
        }
        try {
            Mensagem boasVindas = Mensagem.nova(bot.getUsuario(),
                    bot.boasVindas(conexao.getUsuario()), LocalDateTime.now(clock));
            conexao.enviar(boasVindas.formatar()); // só para quem entrou, não é persistida
        } catch (FalhaEnvioException e) {
            LOG.log(Level.FINE, "Não foi possível saudar " + conexao.getUsuario(), e);
        }
    }

    /** Falha do bot nunca derruba o chat: é registrada e ignorada. */
    private void botResponde(Usuario autor, String texto) {
        if (bot == null || autor.equals(bot.getUsuario())) {
            return;
        }
        try {
            botExecutor.execute(() -> {
                try {
                    bot.responder(autor, texto).ifPresent(this::publicarRespostaDoBot);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Falha ao responder com o bot", e);
                }
            });
        } catch (RuntimeException e) { // ex.: pool lotado
            LOG.log(Level.WARNING, "Bot não pôde ser agendado", e);
        }
    }

    /** Reentrante: funciona tanto dentro do lock (executor direto) quanto de outra thread. */
    private synchronized void publicarRespostaDoBot(String resposta) {
        Mensagem salva = repository.salvar(
                Mensagem.nova(bot.getUsuario(), resposta, LocalDateTime.now(clock)));
        broadcast(salva.formatar());
    }

    /**
     * Envia a linha a todos usando um SNAPSHOT. Quem falhar é removido, sem
     * interromper a entrega aos demais.
     */
    void broadcast(String linha) {
        List<ConexaoCliente> falhas = new ArrayList<>();
        for (ConexaoCliente c : registry.snapshot()) {
            try {
                c.enviar(linha);
            } catch (FalhaEnvioException e) {
                falhas.add(c);
            }
        }
        for (ConexaoCliente c : falhas) {
            if (registry.remover(c)) {
                broadcast("* " + c.getUsuario().nome() + " saiu do chat");
            }
        }
    }
}
