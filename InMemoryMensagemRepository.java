package support;

import model.Mensagem;
import persistence.MensagemRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Repository em memória para testar o service sem banco. */
public class InMemoryMensagemRepository implements MensagemRepository {

    private final List<Mensagem> mensagens = new ArrayList<>();
    private final AtomicLong sequencia = new AtomicLong();

    @Override
    public synchronized Mensagem salvar(Mensagem mensagem) {
        Mensagem salva = mensagem.comId(sequencia.incrementAndGet());
        mensagens.add(salva);
        return salva;
    }

    @Override
    public synchronized List<Mensagem> buscarUltimas(int quantidade) {
        int inicio = Math.max(0, mensagens.size() - quantidade);
        return new ArrayList<>(mensagens.subList(inicio, mensagens.size()));
    }

    public synchronized int total() {
        return mensagens.size();
    }
}
