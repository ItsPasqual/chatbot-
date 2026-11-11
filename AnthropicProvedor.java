package service.bot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Chama a API de mensagens da Anthropic (Claude) usando só o java.net.http do JDK.
 * Com buscaWeb=true a IA pode pesquisar na internet (como o Google) antes de responder.
 */
public class AnthropicProvedor implements ProvedorIA {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String apiKey;
    private final String modelo;
    private final boolean buscaWeb;

    public AnthropicProvedor(String apiKey, String modelo, boolean buscaWeb) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.buscaWeb = buscaWeb;
    }

    @Override
    public String responder(String instrucoes, List<Turno> conversa) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(montarCorpo(instrucoes, conversa), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("API de IA respondeu HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return extrairTexto(resp.body());
    }

    String montarCorpo(String instrucoes, List<Turno> conversa) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"model\":").append(Json.aspas(modelo));
        sb.append(",\"max_tokens\":500");
        sb.append(",\"system\":").append(Json.aspas(instrucoes));
        sb.append(",\"messages\":[");
        for (int k = 0; k < conversa.size(); k++) {
            Turno t = conversa.get(k);
            if (k > 0) {
                sb.append(',');
            }
            sb.append("{\"role\":").append(Json.aspas(t.papel()))
                    .append(",\"content\":").append(Json.aspas(t.texto())).append('}');
        }
        sb.append(']');
        if (buscaWeb) {
            sb.append(",\"tools\":[{\"type\":\"web_search_20250305\",\"name\":\"web_search\",\"max_uses\":3}]");
        }
        return sb.append('}').toString();
    }

    /** Junta os blocos de texto da resposta (ignora blocos de busca e de ferramenta). */
    @SuppressWarnings("unchecked")
    static String extrairTexto(String json) throws IOException {
        Object raiz = Json.parse(json);
        if (!(raiz instanceof Map<?, ?> mapa) || !(mapa.get("content") instanceof List<?> blocos)) {
            throw new IOException("Resposta da IA em formato inesperado");
        }
        StringBuilder sb = new StringBuilder();
        for (Object b : blocos) {
            Map<String, Object> bloco = (Map<String, Object>) b;
            if ("text".equals(bloco.get("type")) && bloco.get("text") instanceof String t) {
                sb.append(t);
            }
        }
        return sb.toString();
    }
}
