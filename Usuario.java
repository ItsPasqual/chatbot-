package model;

import java.util.Locale;

/** Usuário do chat. Imutável. Igualdade por nome, sem distinguir maiúsculas. */
public record Usuario(String nome) {

    public static final int TAMANHO_MAXIMO = 30;

    public Usuario {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome não pode ser vazio.");
        }
        nome = nome.trim();
        if (nome.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "O nome pode ter no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        if (nome.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("O nome não pode conter espaços.");
        }
        if (nome.startsWith("/") || nome.startsWith("*")) {
            throw new IllegalArgumentException("O nome não pode começar com '/' ou '*'.");
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Usuario outro && nome.equalsIgnoreCase(outro.nome);
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
