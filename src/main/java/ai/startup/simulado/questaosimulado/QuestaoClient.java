package ai.startup.simulado.questaosimulado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class QuestaoClient {
    private final RestTemplate rt;
    private final String base;

    public QuestaoClient(RestTemplate rt, @Value("${api.questao.base}") String base) {
        this.rt = rt; this.base = base;
    }

    public List<Map<String,Object>> criarQuestoes(String bearerToken, List<QuestoesCreateItemDTO> lista) {
        var url = base + "/questoes";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        var resp = rt.exchange(url, HttpMethod.POST, new HttpEntity<>(lista, headers), List.class);
        return (List<Map<String,Object>>) resp.getBody();
    }

    public List<Map<String,Object>> listarPorSimulado(String bearerToken, String idSimulado) {
        var url = base + "/questoes/by-simulado/" + idSimulado;
        var headers = new HttpHeaders(); headers.set("Authorization", bearerToken);
        var resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class);
        return (List<Map<String,Object>>) resp.getBody();
    }

    public void deletar(String bearerToken, String idQuestao) {
        var url = base + "/questoes/" + idQuestao;
        var headers = new HttpHeaders(); headers.set("Authorization", bearerToken);
        rt.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }
}
