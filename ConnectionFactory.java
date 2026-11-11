package persistence.jdbc;

import persistence.PersistenciaException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Cria uma Connection NOVA a cada chamada (nunca compartilhada entre threads). */
public class ConnectionFactory {

    private final String url;
    private final String usuario;
    private final String senha;

    public ConnectionFactory(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível conectar ao banco: " + url, e);
        }
    }
}
