package server;

/** Constantes e parsing das linhas do protocolo (texto, uma mensagem por linha, UTF-8). */
public final class Protocolo {

    public static final String COMANDO_SAIR = "/quit";
    public static final String PREFIXO_SISTEMA = "* ";

    private Protocolo() {
    }

    public static boolean ehComando(String linha) {
        return linha.startsWith("/");
    }

    public static boolean ehSair(String linha) {
        return linha.trim().equalsIgnoreCase(COMANDO_SAIR);
    }

    public static String sistema(String texto) {
        return PREFIXO_SISTEMA + texto;
    }
}
