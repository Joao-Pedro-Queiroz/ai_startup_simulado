package ai.startup.simulado.questaosimulado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class QuestaoClient {
    private final RestTemplate rt;
    private final String base;

    public QuestaoClient(RestTemplate rt, @Value("${api.questao.base}") String base) {
        this.rt = rt;
        this.base = base;
    }

    // ===== Helpers =====
    private HttpHeaders bearerHeaders(String bearerToken) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", bearerToken);
        return h;
    }
    private HttpHeaders jsonBearerHeaders(String bearerToken) {
        HttpHeaders h = bearerHeaders(bearerToken);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ===== Já existentes =====
    public List<Map<String,Object>> criarQuestoes(String bearerToken, List<QuestoesCreateItemDTO> lista) {
        var url = base + "/questoes";
        var headers = jsonBearerHeaders(bearerToken);
        var resp = rt.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(lista, headers),
                new ParameterizedTypeReference<List<Map<String,Object>>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao criar questões.");
        }
        return resp.getBody();
    }

    public List<Map<String,Object>> listarPorSimulado(String bearerToken, String idSimulado) {
        var url = base + "/questoes/by-simulado/" + idSimulado;
        var headers = bearerHeaders(bearerToken);
        var resp = rt.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String,Object>>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao listar questões por simulado.");
        }
        return resp.getBody();
    }

    public void deletar(String bearerToken, String idQuestao) {
        var url = base + "/questoes/" + idQuestao;
        var headers = bearerHeaders(bearerToken);
        var resp = rt.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        if (!resp.getStatusCode().is2xxSuccessful() && resp.getStatusCode() != HttpStatus.NO_CONTENT) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao deletar questão.");
        }
    }

    // ===== NOVOS =====

    /** Atualiza uma questão existente (PUT /questoes/{id}) */
    public Map<String,Object> atualizar(String bearerToken, String idQuestao, QuestaoUpdateDTO dto) {
        var url = base + "/questoes/" + idQuestao;
        var headers = jsonBearerHeaders(bearerToken);
        var resp = rt.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(dto, headers),
                new ParameterizedTypeReference<Map<String,Object>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao atualizar questão " + idQuestao + ".");
        }
        return resp.getBody();
    }

    /** Atualização em lote de questões (PUT /questoes/bulk-update) - OTIMIZAÇÃO */
    public List<Map<String,Object>> atualizarEmLote(String bearerToken, List<Map<String,Object>> questoes) {
        var url = base + "/questoes/bulk-update";
        var headers = jsonBearerHeaders(bearerToken);
        var payload = Map.of("questoes", questoes);
        var resp = rt.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(payload, headers),
                new ParameterizedTypeReference<List<Map<String,Object>>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao atualizar questões em lote.");
        }
        return resp.getBody();
    }

    /** Lista todas as questões de um usuário (GET /questoes/by-usuario/{idUsuario}) */
    public List<Map<String,Object>> listarPorUsuario(String bearerToken, String idUsuario) {
        var url = base + "/questoes/by-usuario/" + idUsuario;
        var headers = bearerHeaders(bearerToken);
        var resp = rt.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<Map<String,Object>>>() {}
        );
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(resp.getStatusCode(), "Falha ao listar questões por usuário.");
        }
        return resp.getBody();
    }
}
