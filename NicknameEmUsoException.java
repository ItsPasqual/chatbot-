package service;

public class NicknameEmUsoException extends RuntimeException {
    public NicknameEmUsoException(String nome) {
        super("O nome '" + nome + "' já está em uso.");
    }
}
