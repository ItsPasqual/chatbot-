package service;

import model.Mensagem;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.bot.Bot;
import service.bot.BotProjeto;
import support.FakeConexao;
import support.InMemoryMensagemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceBotTest {

    private InMemoryMensagemRepository repository;
    private ClientRegistry registry;
    private ChatService service;
    private final Usuario ana = new Usuario("ana");
    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-06T21:15:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void preparar() {
        repository = new InMemoryMensagemRepository();
        registry = new ClientRegistry();
        service = new ChatService(repository, registry, relogio, 10, new BotProjeto());
    }

    @Test
    void botResponderEhSalvoETransmitidoATodos() {
        FakeConexao a = new FakeConexao("ana");
        FakeConexao b = new FakeConexao("bruno");
        service.conectar(a);
        service.conectar(b);

        service.enviarMensagem(ana, "@bot como funciona a concorrência?");

        assertEquals(2, repository.total()); // pergunta + resposta
        for (FakeConexao f : new FakeConexao[]{a, b}) {
            assertTrue(f.recebidas().stream().anyMatch(l -> l.contains("] ChatBot: Uma thread por cliente")));
        }
    }

    @Test
    void respostaDoBotVemDepoisDaPergunta() {
        FakeConexao a = new FakeConexao("ana");
        service.conectar(a);
        service.enviarMensagem(ana, "@bot ajuda");

        var linhas = a.recebidas();
        int pergunta = -1;
        int resposta = -1;
        for (int i = 0; i < linhas.size(); i++) {
            if (linhas.get(i).contains("ana: @bot ajuda")) {
                pergunta = i;
            }
            if (linhas.get(i).contains("ChatBot: Pergunte sobre")) {
                resposta = i;
            }
        }
        assertTrue(pergunta >= 0 && resposta == pergunta + 1);
    }

    @Test
    void nomeDoBotEhReservado() {
        assertThrows(NicknameEmUsoException.class, () -> service.conectar(new FakeConexao("chatbot")));
        assertEquals(0, registry.quantidade());
    }

    @Test
    void boasVindasDoBotVaoSoParaQuemEntrouENaoSaoPersistidas() {
        FakeConexao a = new FakeConexao("ana");
        service.conectar(a);
        FakeConexao b = new FakeConexao("bruno");
        service.conectar(b);

        assertTrue(b.recebidas().stream().anyMatch(l -> l.contains("ChatBot: Oi, bruno!")));
        assertFalse(a.recebidas().stream().anyMatch(l -> l.contains("Oi, bruno!")));
        assertEquals(0, repository.total());
    }

    @Test
    void falhaNoBotNaoDerrubaOChat() {
        Bot quebrado = new Bot() {
            public Usuario getUsuario() { return new Usuario("Quebrado"); }
            public Optional<String> responder(Usuario autor, String texto) { throw new IllegalStateException("boom"); }
            public String boasVindas(Usuario novo) { return "oi"; }
        };
        ChatService s = new ChatService(repository, registry, relogio, 10, quebrado);
        FakeConexao a = new FakeConexao("ana");
        s.conectar(a);

        Mensagem salva = s.enviarMensagem(ana, "olá");

        assertEquals("olá", salva.getConteudo());
        assertTrue(a.recebidas().stream().anyMatch(l -> l.contains("ana: olá")));
    }

    @Test
    void botLentoNaoTravaOChat() throws Exception {
        var iniciou = new java.util.concurrent.CountDownLatch(1);
        var liberar = new java.util.concurrent.CountDownLatch(1);
        Bot lento = new Bot() {
            public Usuario getUsuario() { return BotProjeto.USUARIO; }
            public Optional<String> responder(Usuario autor, String texto) {
                iniciou.countDown();
                try { liberar.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return Optional.of("pronto");
            }
            public String boasVindas(Usuario novo) { return "oi"; }
        };
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            ChatService s = new ChatService(repository, registry, relogio, 10, lento, pool);
            FakeConexao a = new FakeConexao("ana");
            s.conectar(a);

            s.enviarMensagem(ana, "@bot pergunta demorada");
            assertTrue(iniciou.await(2, java.util.concurrent.TimeUnit.SECONDS));

            // com o bot ainda "pensando", outra mensagem passa na hora
            s.enviarMensagem(new Usuario("bruno"), "oi pessoal");
            assertTrue(a.recebidas().stream().anyMatch(l -> l.contains("bruno: oi pessoal")));
            assertFalse(a.recebidas().stream().anyMatch(l -> l.contains("ChatBot: pronto")));

            liberar.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(a.recebidas().stream().anyMatch(l -> l.contains("ChatBot: pronto")));
        } finally {
            liberar.countDown();
            pool.shutdownNow();
        }
    }
}
