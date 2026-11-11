package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Lê chat.properties do classpath; se faltar arquivo ou chave, usa valores padrão. */
public class Config {

    private final Properties props;

    public Config(Properties props) {
        this.props = props;
    }

    public static Config carregar() {
        Properties p = new Properties();
        try (InputStream in = Config.class.getResourceAsStream("/chat.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            System.err.println("Aviso: não foi possível ler chat.properties; usando padrões.");
        }
        return new Config(p);
    }

    public int porta() { return inteiro("porta", 5000); }
    public String dbUrl() { return props.getProperty("db.url", "jdbc:h2:./data/chat"); }
    public String dbUsuario() { return props.getProperty("db.usuario", "sa"); }
    public String dbSenha() { return props.getProperty("db.senha", ""); }
    public int tamanhoHistorico() { return inteiro("historico.tamanho", 20); }

    /** A chave NUNCA fica no chat.properties (iria para o git): vem da variável de ambiente. */
    public String iaApiKey() {
        String k = System.getenv("ANTHROPIC_API_KEY");
        return k == null ? "" : k.trim();
    }
    public String iaModelo() { return props.getProperty("ia.modelo", "claude-haiku-5-5").trim(); }
    public boolean iaBuscaWeb() { return Boolean.parseBoolean(props.getProperty("ia.busca-web", "true").trim()); }

    private int inteiro(String chave, int padrao) {
        try {
            return Integer.parseInt(props.getProperty(chave, String.valueOf(padrao)).trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }
}
