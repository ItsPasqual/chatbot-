package model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensagemTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 6, 21, 15);
    private final Usuario ana = new Usuario("ana");

    @Test
    void rejeitaConteudoVazioOuSoEspacos() {
        assertThrows(MensagemInvalidaException.class, () -> Mensagem.nova(ana, "", HORA));
        assertThrows(MensagemInvalidaException.class, () -> Mensagem.nova(ana, "   ", HORA));
        assertThrows(MensagemInvalidaException.class, () -> Mensagem.nova(ana, null, HORA));
    }

    @Test
    void rejeitaConteudoAcimaDoLimite() {
        String grande = "x".repeat(Mensagem.TAMANHO_MAXIMO + 1);
        assertThrows(MensagemInvalidaException.class, () -> Mensagem.nova(ana, grande, HORA));
    }

    @Test
    void formatarProduzPadraoEsperado() {
        assertEquals("[06/10 21:15] ana: olá", Mensagem.nova(ana, "  olá  ", HORA).formatar());
    }

    @Test
    void comIdDevolveNovaInstanciaSemAlterarOriginal() {
        Mensagem original = Mensagem.nova(ana, "oi", HORA);
        Mensagem salva = original.comId(7L);
        assertNull(original.getId());
        assertEquals(7L, salva.getId().longValue());
    }
}
