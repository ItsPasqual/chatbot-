package service;

import model.Usuario;

/** Abstração de "alguém conectado que pode receber texto". Permite testar sem rede. */
public interface ConexaoCliente {

    Usuario getUsuario();

    /** @throws FalhaEnvioException se não foi possível entregar a linha. */
    void enviar(String linha);
}
