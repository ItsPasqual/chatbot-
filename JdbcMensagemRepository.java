package persistence.jdbc;

import model.Mensagem;
import model.Usuario;
import persistence.MensagemRepository;
import persistence.PersistenciaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Único lugar do sistema (junto com ConnectionFactory) que conhece java.sql. */
public class JdbcMensagemRepository implements MensagemRepository {

    private static final String INSERT =
            "INSERT INTO mensagem (remetente, conteudo, horario) VALUES (?, ?, ?)";
    private static final String SELECT_ULTIMAS =
            "SELECT id, remetente, conteudo, horario FROM mensagem ORDER BY id DESC LIMIT ?";

    private final ConnectionFactory connectionFactory;

    public JdbcMensagemRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /** Executa o schema.sql do classpath (idempotente: usa IF NOT EXISTS). */
    public void inicializarSchema() {
        String script = lerSchema();
        try (Connection conn = connectionFactory.getConnection();
             Statement st = conn.createStatement()) {
            for (String comando : script.split(";")) {
                if (!comando.isBlank()) {
                    st.execute(comando.trim());
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao criar o schema do banco", e);
        }
    }

    private String lerSchema() {
        try (InputStream in = getClass().getResourceAsStream("/schema.sql")) {
            if (in == null) {
                throw new PersistenciaException("schema.sql não encontrado no classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenciaException("Falha ao ler schema.sql", e);
        }
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mensagem.getRemetente().nome());
            ps.setString(2, mensagem.getConteudo());
            ps.setTimestamp(3, Timestamp.valueOf(mensagem.getHorario()));
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (chaves.next()) {
                    return mensagem.comId(chaves.getLong(1));
                }
                throw new PersistenciaException("O banco não devolveu o id da mensagem");
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao salvar a mensagem", e);
        }
    }

    @Override
    public List<Mensagem> buscarUltimas(int quantidade) {
        List<Mensagem> resultado = new ArrayList<>();
        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ULTIMAS)) {
            ps.setInt(1, quantidade);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(new Mensagem(
                            rs.getLong("id"),
                            new Usuario(rs.getString("remetente")),
                            rs.getString("conteudo"),
                            rs.getTimestamp("horario").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar o histórico", e);
        }
        Collections.reverse(resultado); // veio do mais novo para o mais antigo
        return resultado;
    }
}
