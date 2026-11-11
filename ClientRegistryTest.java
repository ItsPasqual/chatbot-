package service;

import org.junit.jupiter.api.Test;
import support.FakeConexao;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRegistryTest {

    @Test
    void recusaNicknameDuplicadoIgnorandoMaiusculas() {
        ClientRegistry registry = new ClientRegistry();
        registry.adicionar(new FakeConexao("ana"));
        assertThrows(NicknameEmUsoException.class, () -> registry.adicionar(new FakeConexao("ANA")));
        assertEquals(1, registry.quantidade());
    }

    @Test
    void nicknameDuplicadoConcorrenteApenasUmVence() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    registry.adicionar(new FakeConexao("ana"));
                    sucessos.incrementAndGet();
                } catch (NicknameEmUsoException | InterruptedException ignorada) {
                    // esperado para os perdedores
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, sucessos.get());
        assertEquals(1, registry.quantidade());
    }

    @Test
    void snapshotNaoSofreModificacaoConcorrente() {
        ClientRegistry registry = new ClientRegistry();
        FakeConexao ana = new FakeConexao("ana");
        registry.adicionar(ana);
        var foto = registry.snapshot();
        registry.remover(ana);
        assertEquals(1, foto.size());
        assertEquals(0, registry.quantidade());
    }
}
