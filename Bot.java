package service.bot;

import model.Usuario;

import java.util.Optional;

/** Participante automático do chat. Não conhece rede nem banco. */
public interface Bot {

    Usuario getUsuario();

    /** Resposta (uma única linha, até 500 caracteres) ou vazio se não tiver o que dizer. */
    Optional<String> responder(Usuario autor, String texto);

    /** Mensagem enviada apenas a quem acabou de entrar (não é salva no banco). */
    String boasVindas(Usuario novo);
}
