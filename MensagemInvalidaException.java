package model;

/** Lançada quando o conteúdo de uma mensagem é inválido (vazio ou grande demais). */
public class MensagemInvalidaException extends RuntimeException {
    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}
