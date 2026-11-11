package service;

import model.Mensagem;
import model.MensagemInvalidaException;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.FakeConexao;
import support.InMemoryMensagemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatServiceTest {

    private InMemoryMensagemRepository repository;
    private ClientRegistry registry;
    private ChatService service;
    private final Usuario ana = new Usuario("ana");

    @BeforeEach
    void preparar() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-06T21:15:00Z"), ZoneId.of("UTC"));
        repository = new InMemoryMensagemRepository();
        registry = new ClientRegistry();
        service = new ChatService(repository, registry, relogio, 10);
    }

    @Test
    void enviarMensagemSalvaNoRepositoryComId() {
        Mensagem salva = service.enviarMensagem(ana, "olá");
        assertEquals(1, repository.total());
        assertEquals(1L, salva.getId().longValue());
    }

    @Test
    void mensagemInvalidaNaoEhSalvaNemTransmitida() {
        FakeConexao bruno = new FakeConexao("bruno");
        service.conectar(bruno);
        int antes = bruno.recebidas().size();

        assertThrows(MensagemInvalidaException.class, () -> service.enviarMensagem(ana, "   "));
        assertEquals(0, repository.total());
        assertEquals(antes, bruno.recebidas().size());
    }

    @Test
    void broadcastEntregaATodosOsRegistrados() {
        FakeConexao a = new FakeConexao("ana");
        FakeConexao b = new FakeConexao("bruno");
        FakeConexao c = new FakeConexao("carlos");
        service.conectar(a);
        service.conectar(b);
        service.conectar(c);

        service.enviarMensagem(ana, "olá");

        for (FakeConexao f : List.of(a, b, c)) {
            assertTrue(f.recebidas().contains("[06/10 21:15] ana: olá"), f.getUsuario() + " não recebeu");
        }
    }

    @Test
    void clienteRemovidoNaoRecebeMaisMensagens() {
        FakeConexao a = new FakeConexao("ana");
        FakeConexao b = new FakeConexao("bruno");
        service.conectar(a);
        service.conectar(b);
        service.desconectar(b);

        service.enviarMensagem(ana, "só para quem ficou");

        assertTrue(a.recebidas().contains("[06/10 21:15] ana: só para quem ficou"));
        assertFalse(b.recebidas().contains("[06/10 21:15] ana: só para quem ficou"));
        assertTrue(a.recebidas().contains("* bruno saiu do chat"));
    }

    @Test
    void historicoDevolveUltimasEmOrdemCronologica() {
        for (int i = 1; i <= 5; i++) {
            service.enviarMensagem(ana, "msg" + i);
        }
        List<Mensagem> ultimas = service.buscarHistorico(3);
        assertEquals(List.of("msg3", "msg4", "msg5"),
                ultimas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void novoClienteRecebeHistoricoAoConectar() {
        service.enviarMensagem(ana, "antiga1");
        service.enviarMensagem(ana, "antiga2");

        FakeConexao novo = new FakeConexao("bruno");
        service.conectar(novo);

        assertEquals("[06/10 21:15] ana: antiga1", novo.recebidas().get(0));
        assertEquals("[06/10 21:15] ana: antiga2", novo.recebidas().get(1));
    }

    @Test
    void falhaEmUmaConexaoNaoImpedeAsDemais() {
        FakeConexao boa1 = new FakeConexao("ana");
        FakeConexao quebrada = FakeConexao.quebrada("zeca");
        FakeConexao boa2 = new FakeConexao("carlos");
        service.conectar(boa1);
        // registra a quebrada diretamente (ela passou a falhar depois de conectar)
        registry.adicionar(quebrada);
        service.conectar(boa2);

        service.enviarMensagem(ana, "oi");

        assertTrue(boa1.recebidas().contains("[06/10 21:15] ana: oi"));
        assertTrue(boa2.recebidas().contains("[06/10 21:15] ana: oi"));
        assertEquals(2, registry.quantidade(), "a conexão quebrada deveria ter sido removida");
    }

    @Test
    void broadcastConcorrenteNaoPerdeNemDuplicaMensagensENaoDivergeDaOrdem() throws Exception {
        int remetentes = 8;
        int porRemetente = 50;
        List<FakeConexao> receptores = new ArrayList<>();
        for (int i = 0; i < remetentes; i++) {
            FakeConexao f = new FakeConexao("user" + i);
            receptores.add(f);
            service.conectar(f);
        }

        ExecutorService pool = Executors.newFixedThreadPool(remetentes);
        CountDownLatch largada = new CountDownLatch(1);
        for (int i = 0; i < remetentes; i++) {
            Usuario u = new Usuario("user" + i);
            pool.submit(() -> {
                try {
                    largada.await();
                    for (int j = 0; j < porRemetente; j++) {
                        service.enviarMensagem(u, "m" + j);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        int esperado = remetentes * porRemetente;
        assertEquals(esperado, repository.total());

        List<String> referencia = mensagensDe(receptores.get(0));
        assertEquals(esperado, referencia.size());
        for (FakeConexao f : receptores) {
            assertEquals(referencia, mensagensDe(f), "ordem divergente em " + f.getUsuario());
        }
        // a ordem vista pelos clientes é a mesma gravada no banco
        List<String> doBanco = repository.buscarUltimas(esperado).stream().map(Mensagem::formatar).toList();
        assertEquals(doBanco, referencia);
    }

    private static List<String> mensagensDe(FakeConexao f) {
        return f.recebidas().stream().filter(l -> !l.startsWith("* ")).toList();
    }
}
