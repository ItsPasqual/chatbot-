package service.bot;

import model.Usuario;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bot gratuito e sem chave: faz contas, diz hora/data, define coisas pela Wikipedia e, por fim,
 * responde sobre o projeto (BotProjeto). Só age sobre mensagens com "@bot"; sem "@bot" mantém o
 * comportamento do BotProjeto. Pode demorar (Wikipedia): use com executor assíncrono no ChatService.
 */
public class BotGratis implements Bot {

    private static final Logger LOG = Logger.getLogger(BotGratis.class.getName());

    private static final Pattern DEFINICAO = Pattern.compile(
            "^(?:o que (?:e|sao|significa|quer dizer)|que (?:e|sao)|quem (?:e|foi|era|sao|eram)"
                    + "|me (?:fale|fala|explique|explica) (?:sobre|de|do|da)?|fale sobre|fala sobre|explique|explica"
                    + "|defina|defina o|significado de|conceito de|onde fica|onde (?:e|fica) a?)\\s+"
                    + "(?:(?:um|uma|o|a|os|as|de|do|da)\\s+)?(.+)$");

    private static final String DICA = "Não sei responder isso. Eu sei: contas (@bot 12*(3+4)), hora e data, "
            + "definições (@bot o que é fotossíntese? / @bot quem foi Santos Dumont?) e o projeto (@bot ajuda).";

    private final BotProjeto projeto;
    private final FonteConhecimento fonte;
    private final Clock clock;

    public BotGratis(BotProjeto projeto, FonteConhecimento fonte, Clock clock) {
        this.projeto = projeto;
        this.fonte = fonte;
        this.clock = clock;
    }

    @Override
    public Usuario getUsuario() {
        return BotProjeto.USUARIO;
    }

    @Override
    public Optional<String> responder(Usuario autor, String texto) {
        if (texto == null || autor.equals(getUsuario())) {
            return Optional.empty();
        }
        if (!normalizar(texto).matches(".*@(chat)?bot.*")) {
            return projeto.responder(autor, texto);
        }
        String pergunta = texto.replaceAll("(?i)@(chat)?bot", "").trim();
        if (pergunta.isEmpty()) {
            return projeto.responder(autor, "ajuda");
        }

        Optional<String> conta = Calculadora.calcular(pergunta);
        if (conta.isPresent()) {
            return conta;
        }
        Optional<String> relogio = dataEHora(normalizar(pergunta));
        if (relogio.isPresent()) {
            return relogio;
        }
        Optional<String> termo = termoDeDefinicao(pergunta);
        if (termo.isPresent()) {
            Optional<String> definicao = buscar(termo.get());
            if (definicao.isPresent()) {
                return definicao;
            }
        }
        return Optional.of(projeto.responder(autor, pergunta).orElse(DICA));
    }

    @Override
    public String boasVindas(Usuario novo) {
        return "Oi, " + novo.nome() + "! Sou o ChatBot. Comece com @bot e pergunte: contas, hora, "
                + "\"o que é ...?\", \"quem foi ...?\" ou sobre o projeto (@bot ajuda).";
    }

    private Optional<String> dataEHora(String p) {
        boolean hora = p.matches(".*\\bque horas?\\b.*|.*\\bhoras? (sao|e)\\b.*|.*\\bhora atual\\b.*");
        boolean data = p.matches(".*\\bque dia\\b.*|.*\\bdata de hoje\\b.*|.*\\bqual (e )?a data\\b.*|.*\\bhoje\\b.*dia.*");
        LocalDateTime agora = LocalDateTime.now(clock);
        if (hora) {
            return Optional.of("Agora são " + agora.format(DateTimeFormatter.ofPattern("HH:mm"))
                    + " (horário do servidor).");
        }
        if (data) {
            return Optional.of("Hoje é " + agora.format(
                    DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))) + ".");
        }
        return Optional.empty();
    }

    private Optional<String> termoDeDefinicao(String pergunta) {
        String limpo = normalizar(pergunta).replaceAll("[?!.]+$", "").trim();
        Matcher m = DEFINICAO.matcher(limpo);
        if (!m.matches()) {
            return Optional.empty();
        }
        String termo = m.group(1).trim();
        return termo.isEmpty() || termo.length() > 80 ? Optional.empty() : Optional.of(termo);
    }

    private Optional<String> buscar(String termo) {
        try {
            return fonte.resumo(termo).map(r -> BotIA.limpar(r) + " (Wikipedia)").map(this::cabe);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Falha ao consultar a Wikipedia", e);
            return Optional.empty();
        }
    }

    /** Garante que o sufixo "(Wikipedia)" não estoure o limite da mensagem. */
    private String cabe(String s) {
        int max = model.Mensagem.TAMANHO_MAXIMO;
        return s.length() <= max ? s : BotIA.limpar(s.substring(0, max - 14)) + " (Wikipedia)";
    }

    /** Minúsculas e sem acentos. */
    private static String normalizar(String t) {
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
