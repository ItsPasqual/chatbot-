package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Mensagem de chat. Imutável: nasce com id == null e o repository devolve
 * uma NOVA instância com o id gerado (comId). O horário vem de fora para
 * manter os testes determinísticos.
 */
public final class Mensagem {

    public static final int TAMANHO_MAXIMO = 500;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final Long id;
    private final Usuario remetente;
    private final String conteudo;
    private final LocalDateTime horario;

    public Mensagem(Long id, Usuario remetente, String conteudo, LocalDateTime horario) {
        this.remetente = Objects.requireNonNull(remetente, "remetente");
        this.horario = Objects.requireNonNull(horario, "horario");
        if (conteudo == null || conteudo.isBlank()) {
            throw new MensagemInvalidaException("A mensagem não pode ser vazia.");
        }
        String limpo = conteudo.trim();
        if (limpo.length() > TAMANHO_MAXIMO) {
            throw new MensagemInvalidaException(
                    "A mensagem pode ter no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        this.id = id;
        this.conteudo = limpo;
    }

    /** Cria uma mensagem ainda não persistida (id nulo). */
    public static Mensagem nova(Usuario remetente, String conteudo, LocalDateTime horario) {
        return new Mensagem(null, remetente, conteudo, horario);
    }

    public Mensagem comId(Long novoId) {
        return new Mensagem(novoId, remetente, conteudo, horario);
    }

    /** Ex.: "[06/10 21:15] ana: olá" */
    public String formatar() {
        return "[" + horario.format(FORMATO) + "] " + remetente.nome() + ": " + conteudo;
    }

    public Long getId() { return id; }
    public Usuario getRemetente() { return remetente; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getHorario() { return horario; }
}
