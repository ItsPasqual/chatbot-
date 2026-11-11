package server;

import model.MensagemInvalidaException;
import model.Usuario;
import persistence.PersistenciaException;
import service.ChatService;
import service.ConexaoCliente;
import service.FalhaEnvioException;
import service.NicknameEmUsoException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Atende UMA conexão (uma thread). Não contém SQL nem regras de negócio. */
public class ClienteHandler implements Runnable, ConexaoCliente {

    private final Socket socket;
    private final ChatService chatService;
    private volatile Usuario usuario;
    private BufferedReader entrada;
    private PrintWriter saida;

    public ClienteHandler(Socket socket, ChatService chatService) {
        this.socket = socket;
        this.chatService = chatService;
    }

    @Override
    public Usuario getUsuario() {
        return usuario;
    }

    /** Chamado por OUTRAS threads durante o broadcast: escrita serializada. */
    @Override
    public synchronized void enviar(String linha) {
        if (saida == null) {
            throw new FalhaEnvioException("Conexão não inicializada");
        }
        saida.println(linha);
        if (saida.checkError()) {
            throw new FalhaEnvioException("Falha ao escrever para " + usuario);
        }
    }

    @Override
    public void run() {
        boolean registrado = false;
        try {
            entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            registrado = identificar();
            if (!registrado) {
                return;
            }
            conversar();
        } catch (IOException | FalhaEnvioException e) {
            System.out.println("[servidor] Conexão encerrada (" + descrever() + "): " + e.getMessage());
        } catch (RuntimeException e) {
            // Nenhuma exceção de um cliente pode escapar e afetar o servidor
            System.err.println("[servidor] Erro inesperado com " + descrever() + ": " + e);
        } finally {
            if (registrado) {
                chatService.desconectar(this);
            }
            fecharSocket();
        }
    }

    /** Handshake: a 1ª linha é o nickname. Repete até ser válido e livre. */
    private boolean identificar() throws IOException {
        enviarDireto(Protocolo.sistema("Bem-vindo! Digite seu nickname:"));
        String linha;
        while ((linha = entrada.readLine()) != null) {
            try {
                this.usuario = new Usuario(linha);
                chatService.conectar(this);
                System.out.println("[servidor] " + usuario + " conectou-se (" + socket.getRemoteSocketAddress() + ")");
                return true;
            } catch (IllegalArgumentException | NicknameEmUsoException e) {
                this.usuario = null;
                enviarDireto(Protocolo.sistema("ERRO: " + e.getMessage() + " Tente outro nickname:"));
            }
        }
        return false;
    }

    private void conversar() throws IOException {
        enviar(Protocolo.sistema("Digite suas mensagens. Use /quit para sair."));
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (Protocolo.ehSair(linha)) {
                return;
            }
            if (Protocolo.ehComando(linha)) {
                enviar(Protocolo.sistema("Comando desconhecido. Use /quit para sair."));
                continue;
            }
            try {
                chatService.enviarMensagem(usuario, linha);
            } catch (MensagemInvalidaException e) {
                enviar(Protocolo.sistema("ERRO: " + e.getMessage()));
            } catch (PersistenciaException e) {
                System.err.println("[servidor] Falha de banco: " + e.getMessage());
                enviar(Protocolo.sistema("ERRO: não foi possível salvar a mensagem."));
            }
        }
    }

    private void enviarDireto(String linha) {
        synchronized (this) {
            saida.println(linha);
        }
    }

    private String descrever() {
        return usuario != null ? usuario.nome() : String.valueOf(socket.getRemoteSocketAddress());
    }

    private void fecharSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("[servidor] Erro ao fechar socket: " + e.getMessage());
        }
    }
}
