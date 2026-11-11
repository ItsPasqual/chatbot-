package service.bot;

import model.Mensagem;
import model.Usuario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotGratisTest {

    private final Usuario ana = new Usuario("ana");
    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-06T21:15:00Z"), ZoneId.of("UTC"));
    private final List<String> buscas = new ArrayList<>();
    private boolean wikiFora;

    private BotGratis bot() {
        FonteConhecimento wiki = termo -> {
            buscas.add(termo);
            if (wikiFora) {
                throw new IOException("sem internet");
            }
            return termo.contains("xyz") ? Optional.empty()
                    : Optional.of("Resumo sobre " + termo + ".\n\nSegunda frase.");
        };
        return new BotGratis(new BotProjeto(), wiki, relogio);
    }

    private String ask(String texto) {
        return bot().responder(ana, texto).orElseThrow();
    }

    @Test
    void fazContas() {
        assertEquals("12 * (3 + 4) = 84", ask("@bot 12 * (3 + 4)"));
        assertEquals("2^10 = 1024", ask("@bot quanto é 2^10?"));
        assertEquals("3*4 = 12", ask("@bot 3x4"));
        assertTrue(ask("@bot 10 / 4").endsWith("= 2,5"));
        assertTrue(ask("@bot 1 / 0").contains("Não dá para calcular"));
        assertTrue(ask("@bot 2 + (3").contains("Não consegui entender"));
    }

    @Test
    void naoConfundeConversaComConta() {
        assertTrue(Calculadora.calcular("estou no 3o andar").isEmpty());
        assertTrue(Calculadora.calcular("2026").isEmpty());
    }

    @Test
    void dizHoraEData() {
        assertEquals("Agora são 21:15 (horário do servidor).", ask("@bot que horas são?"));
        assertTrue(ask("@bot que dia é hoje?").contains("2026"));
    }

    @Test
    void defineComAWikipedia() {
        String r = ask("@bot o que é fotossíntese?");
        assertTrue(r.startsWith("Resumo sobre fotossintese."));
        assertTrue(r.endsWith("(Wikipedia)"));
        assertFalse(r.contains("\n"));
        assertTrue(ask("@bot quem foi Santos Dumont?").contains("santos dumont"));
    }

    @Test
    void respostaDaWikipediaSemprecabeNaMensagem() {
        BotGratis b = new BotGratis(new BotProjeto(), t -> Optional.of("palavra ".repeat(300)), relogio);
        String r = b.responder(ana, "@bot o que é teste").orElseThrow();
        assertTrue(r.length() <= Mensagem.TAMANHO_MAXIMO, "tamanho " + r.length());
        assertTrue(r.endsWith("(Wikipedia)"));
    }

    @Test
    void semWikipediaOuSemResultadoMostraDica() {
        assertTrue(ask("@bot o que é xyzabc?").startsWith("Não sei responder isso"));
        wikiFora = true;
        assertTrue(ask("@bot quem foi Pelé?").startsWith("Não sei responder isso"));
    }

    @Test
    void perguntasSobreOProjetoContinuamFuncionando() {
        assertTrue(ask("@bot como funciona a concorrência?").contains("thread"));
        assertTrue(ask("@bot ajuda").startsWith("Pergunte sobre"));
        assertTrue(buscas.isEmpty());
    }

    @Test
    void semArrobaBotNaoBuscaNada() {
        assertFalse(bot().responder(ana, "vamos almoçar?").isPresent());
        assertFalse(bot().responder(ana, "quem foi Pelé").isPresent());
        assertTrue(buscas.isEmpty());
    }

    @Test
    void lePrimeiroArtigoDaRespostaDaWikipedia() {
        String json = "{\"batchcomplete\":true,\"query\":{\"pages\":[{\"pageid\":1,\"title\":\"Pelé\","
                + "\"extract\":\"Edson Arantes do Nascimento foi um futebolista.\"}]}}";
        assertEquals("Edson Arantes do Nascimento foi um futebolista.", Wikipedia.extrair(json).orElseThrow());
        assertTrue(Wikipedia.extrair("{\"batchcomplete\":true}").isEmpty());
    }
}
