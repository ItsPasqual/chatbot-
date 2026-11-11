package persistence;

import model.Mensagem;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import persistence.jdbc.ConnectionFactory;
import persistence.jdbc.JdbcMensagemRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste de INTEGRAÇÃO: usa H2 em memória (não é teste unitário). */
class JdbcMensagemRepositoryIntegracaoTest {

    private JdbcMensagemRepository repository;
    private final Usuario ana = new Usuario("ana");
    private final LocalDateTime hora = LocalDateTime.of(2026, 10, 6, 21, 15);

    @BeforeEach
    void preparar() {
        ConnectionFactory factory = new ConnectionFactory(
                "jdbc:h2:mem:teste" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        repository = new JdbcMensagemRepository(factory);
        repository.inicializarSchema();
    }

    @Test
    void salvarDevolveMensagemComIdGerado() {
        Mensagem salva = repository.salvar(Mensagem.nova(ana, "olá", hora));
        assertTrue(salva.getId() != null && salva.getId() > 0);
        assertEquals("olá", salva.getConteudo());
    }

    @Test
    void buscarUltimasDevolveNMaisRecentesEmOrdemCronologica() {
        for (int i = 1; i <= 5; i++) {
            repository.salvar(Mensagem.nova(ana, "msg" + i, hora.plusMinutes(i)));
        }
        List<Mensagem> ultimas = repository.buscarUltimas(3);
        assertEquals(List.of("msg3", "msg4", "msg5"),
                ultimas.stream().map(Mensagem::getConteudo).toList());
    }

    @Test
    void preservaRemetenteHorarioEAcentos() {
        repository.salvar(Mensagem.nova(ana, "coração ção ñ", hora));
        Mensagem lida = repository.buscarUltimas(1).get(0);
        assertEquals("coração ção ñ", lida.getConteudo());
        assertEquals(ana, lida.getRemetente());
        assertEquals(hora, lida.getHorario());
    }
}
