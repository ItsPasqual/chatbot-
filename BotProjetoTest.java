package service.bot;

import model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotProjetoTest {

    private final BotProjeto bot = new BotProjeto();
    private final Usuario ana = new Usuario("ana");

    @Test
    void respondeSobreRequisitos() {
        String r = bot.responder(ana, "@bot quais são os requisitos?").orElseThrow();
        assertTrue(r.contains("ServerSocket"));
    }

    @Test
    void respondeSobreConcorrenciaComOuSemAcento() {
        assertTrue(bot.responder(ana, "como funciona a concorrência?").orElseThrow().contains("thread"));
        assertTrue(bot.responder(ana, "COMO FUNCIONA A CONCORRENCIA").orElseThrow().contains("thread"));
    }

    @Test
    void ignoraConversaSemRelacaoComOProjeto() {
        assertFalse(bot.responder(ana, "vamos almoçar?").isPresent());
    }

    @Test
    void saudaPeloNome() {
        assertTrue(bot.responder(ana, "oi").orElseThrow().startsWith("Oi, ana!"));
    }

    @Test
    void quandoChamadoSemEntenderMostraAjuda() {
        assertTrue(bot.responder(ana, "@bot blablabla").orElseThrow().startsWith("Não entendi"));
    }

    @Test
    void naoRespondeAElePropriio() {
        assertFalse(bot.responder(BotProjeto.USUARIO, "requisitos").isPresent());
    }

    @Test
    void todasAsRespostasCabemEmUmaMensagemDeUmaLinha() {
        for (String pergunta : new String[]{"requisitos", "arquitetura", "concorrência", "race", "broadcast",
                "banco", "testes", "protocolo", "desconexão", "nickname", "critérios", "cronograma",
                "relatório", "extras", "ajuda", "você"}) {
            String r = bot.responder(ana, pergunta).orElseThrow();
            assertTrue(r.length() <= model.Mensagem.TAMANHO_MAXIMO, pergunta + " tem " + r.length());
            assertFalse(r.contains("\n"), pergunta);
        }
        assertEquals(true, bot.boasVindas(ana).length() < 500);
    }
}
