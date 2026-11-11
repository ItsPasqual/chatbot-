package service;

import model.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dono da coleção compartilhada de conexões.
 * CopyOnWriteArrayList: muitas leituras (broadcast), poucas escritas (entrar/sair).
 * adicionar() é synchronized para tornar atômico o "nickname existe? então adiciona".
 */
public class ClientRegistry {

    private final CopyOnWriteArrayList<ConexaoCliente> conexoes = new CopyOnWriteArrayList<>();

    /** Check-then-act atômico. @throws NicknameEmUsoException se o nome já existe. */
    public synchronized void adicionar(ConexaoCliente conexao) {
        if (buscarPorUsuario(conexao.getUsuario()).isPresent()) {
            throw new NicknameEmUsoException(conexao.getUsuario().nome());
        }
        conexoes.add(conexao);
    }

    /** @return true se a conexão estava registrada (garante aviso de saída único). */
    public synchronized boolean remover(ConexaoCliente conexao) {
        return conexoes.remove(conexao);
    }

    /** Cópia imutável: o broadcast itera sobre ela sem segurar lock. */
    public List<ConexaoCliente> snapshot() {
        return List.copyOf(conexoes);
    }

    public int quantidade() {
        return conexoes.size();
    }

    public Optional<ConexaoCliente> buscarPorUsuario(Usuario usuario) {
        return conexoes.stream().filter(c -> c.getUsuario().equals(usuario)).findFirst();
    }

    public List<Usuario> usuariosOnline() {
        return conexoes.stream().map(ConexaoCliente::getUsuario).toList();
    }
}
