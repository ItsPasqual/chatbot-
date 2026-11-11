package service.bot;

import model.Mensagem;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotIATest {

    private final Usuario ana = new Usuario("ana");

    /** Provedor falso: guarda o que recebeu e devolve uma resposta fixa (ou falha). */
    private static class Falso implements ProvedorIA {
        final List<List<Turno>> chamadas = new ArrayList<>();
        String resposta = "Brasília.";
        boolean falhar;

        public String responder(String instrucoes, List<Turno> conversa) throws IOException {
            chamadas.add(new ArrayList<>(conversa));
            if (falhar) {
                throw new IOException("sem rede");
            }
            return resposta;
        }
    }

    @Test
    void comArrobaBotPerguntaParaAIA() {
        Falso ia = new Falso();
        BotIA bot = new BotIA(ia, new BotProjeto());

        String r = bot.responder(ana, "@bot qual a capital do Brasil?").orElseThrow();

        assertEquals("Brasília.", r);
        assertEquals("ana: qual a capital do Brasil?", ia.chamadas.get(0).get(0).texto());
    }

    @Test
    void semArrobaBotNaoGastaIA() {
        Falso ia = new Falso();
        BotIA bot = new BotIA(ia, new BotProjeto());

        assertFalse(bot.responder(ana, "vamos almoçar?").isPresent());
        assertTrue(ia.chamadas.isEmpty());
    }

    @Test
    void lembraAConversaAnterior() {
        Falso ia = new Falso();
        BotIA bot = new BotIA(ia, new BotProjeto());
        bot.responder(ana, "@bot capital do Brasil?");
        bot.responder(ana, "@bot e a população?");

        List<ProvedorIA.Turno> segunda = ia.chamadas.get(1);
        assertEquals(3, segunda.size()); // pergunta, resposta, nova pergunta
        assertEquals("assistant", segunda.get(1).papel());
        assertEquals("user", segunda.get(0).papel());
    }

    @Test
    void seAIAFalharUsaRespostaLocal() {
        Falso ia = new Falso();
        ia.falhar = true;
        BotIA bot = new BotIA(ia, new BotProjeto());

        assertTrue(bot.responder(ana, "@bot como funciona a concorrência?").orElseThrow().contains("thread"));
        assertTrue(bot.responder(ana, "@bot capital da França?").orElseThrow().contains("sem acesso à IA"));
    }

    @Test
    void respostaViraUmaLinhaQueCabeNaMensagem() {
        Falso ia = new Falso();
        ia.resposta = "**Olá**\n\n" + "palavra ".repeat(200);
        BotIA bot = new BotIA(ia, new BotProjeto());

        String r = bot.responder(ana, "@bot fale muito").orElseThrow();

        assertFalse(r.contains("\n"));
        assertFalse(r.contains("*"));
        assertTrue(r.length() <= Mensagem.TAMANHO_MAXIMO);
    }

    @Test
    void naoRespondeAElePropriio() {
        BotIA bot = new BotIA(new Falso(), new BotProjeto());
        assertFalse(bot.responder(BotProjeto.USUARIO, "@bot oi").isPresent());
    }

    @Test
    void lePrimeiroTextoIgnorandoBlocosDeBusca() throws IOException {
        String json = "{\"content\":[{\"type\":\"server_tool_use\",\"name\":\"web_search\",\"input\":{\"query\":\"x\"}},"
                + "{\"type\":\"web_search_tool_result\",\"content\":[{\"title\":\"t\"}]},"
                + "{\"type\":\"text\",\"text\":\"Ol\\u00e1, \"},{\"type\":\"text\",\"text\":\"\\\"mundo\\\"\"}]}";
        assertEquals("Olá, \"mundo\"", AnthropicProvedor.extrairTexto(json));
    }

    @Test
    void corpoDaRequisicaoEhJsonValidoComEscapes() {
        AnthropicProvedor p = new AnthropicProvedor("k", "modelo-x", true);
        String corpo = p.montarCorpo("regra \"a\"\nb", List.of(new ProvedorIA.Turno("user", "ana: oi\\")));
        var mapa = (java.util.Map<?, ?>) Json.parse(corpo);
        assertEquals("modelo-x", mapa.get("model"));
        assertEquals("regra \"a\"\nb", mapa.get("system"));
        assertTrue(mapa.containsKey("tools"));
    }
}
