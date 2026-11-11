package persistence;

import model.Mensagem;

import java.util.List;

public interface MensagemRepository {

    /** Persiste a mensagem e devolve uma nova instância com o id preenchido. */
    Mensagem salvar(Mensagem mensagem);

    /** Últimas N mensagens, em ordem cronológica (mais antiga primeiro). */
    List<Mensagem> buscarUltimas(int quantidade);
}
