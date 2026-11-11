package service.bot;

import java.io.IOException;
import java.util.List;

/** Porta para qualquer serviço de IA. Quem implementa conhece HTTP; o bot não. */
public interface ProvedorIA {

    /** Uma fala da conversa. papel: "user" ou "assistant". */
    record Turno(String papel, String texto) {
    }

    /** Devolve o texto da resposta. A conversa começa sempre com um turno "user". */
    String responder(String instrucoes, List<Turno> conversa) throws IOException, InterruptedException;
}
