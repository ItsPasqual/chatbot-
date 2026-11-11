package service.bot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON mínimo (sem dependências): suficiente para montar e ler as respostas da API de IA. */
final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /** Devolve Map, List, String, Double, Boolean ou null. */
    static Object parse(String texto) {
        Json p = new Json(texto);
        p.espacos();
        Object v = p.valor();
        p.espacos();
        if (p.i != p.s.length()) {
            throw new IllegalArgumentException("JSON com lixo no final, posição " + p.i);
        }
        return v;
    }

    /** Coloca entre aspas e escapa. */
    static String aspas(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private Object valor() {
        if (i >= s.length()) {
            throw erro("fim inesperado");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{': return objeto();
            case '[': return lista();
            case '"': return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default: return numero();
        }
    }

    private Map<String, Object> objeto() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        espacos();
        if (s.charAt(i) == '}') {
            i++;
            return m;
        }
        while (true) {
            espacos();
            String chave = string();
            espacos();
            esperar(':');
            espacos();
            m.put(chave, valor());
            espacos();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                esperar('}');
                return m;
            }
        }
    }

    private List<Object> lista() {
        List<Object> l = new ArrayList<>();
        i++; // [
        espacos();
        if (s.charAt(i) == ']') {
            i++;
            return l;
        }
        while (true) {
            espacos();
            l.add(valor());
            espacos();
            if (s.charAt(i) == ',') {
                i++;
            } else {
                esperar(']');
                return l;
            }
        }
    }

    private String string() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw erro("string não terminada");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> sb.append(e); // " \ /
            }
        }
    }

    private Double numero() {
        int ini = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (ini == i) {
            throw erro("valor inválido");
        }
        return Double.valueOf(s.substring(ini, i));
    }

    private void literal(String l) {
        if (!s.startsWith(l, i)) {
            throw erro("esperava " + l);
        }
        i += l.length();
    }

    private void esperar(char c) {
        if (i >= s.length() || s.charAt(i) != c) {
            throw erro("esperava '" + c + "'");
        }
        i++;
    }

    private void espacos() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private IllegalArgumentException erro(String msg) {
        return new IllegalArgumentException("JSON inválido (" + msg + ") na posição " + i);
    }
}
