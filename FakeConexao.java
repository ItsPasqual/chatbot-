package support;

import model.Usuario;
import service.ConexaoCliente;
import service.FalhaEnvioException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** ConexaoCliente de teste: acumula as linhas recebidas, sem rede. */
public class FakeConexao implements ConexaoCliente {

    private final Usuario usuario;
    private final List<String> recebidas = new CopyOnWriteArrayList<>();
    private volatile boolean falhar;

    public FakeConexao(String nome) {
        this.usuario = new Usuario(nome);
    }

    public static FakeConexao quebrada(String nome) {
        FakeConexao f = new FakeConexao(nome);
        f.falhar = true;
        return f;
    }

    @Override
    public Usuario getUsuario() {
        return usuario;
    }

    @Override
    public void enviar(String linha) {
        if (falhar) {
            throw new FalhaEnvioException("conexão quebrada");
        }
        recebidas.add(linha);
    }

    public List<String> recebidas() {
        return recebidas;
    }
}
