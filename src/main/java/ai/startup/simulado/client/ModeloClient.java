package ai.startup.simulado.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ModeloClient {
    private final RestTemplate rt;
    private final String base;
    private final String adaptativoPath;
    private final String originalPath;

    public ModeloClient(RestTemplate rt,
                        @Value("${api.modelo.base}") String base,
                        @Value("${api.modelo.adaptativo-path:/generateModule}") String adaptativoPath,
                        @Value("${api.modelo.original-path:/generateFullExam}") String originalPath) {
        this.rt = rt;
        this.base = base;
        this.adaptativoPath = adaptativoPath;
        this.originalPath = originalPath;
    }

    /** Gera ~22 questões (módulo) – usado 2x no adaptativo */
    public Map<String,Object> gerarModuloAdaptativo(String userId) {
        String url = base + adaptativoPath;
        var headers = new HttpHeaders(); 
        headers.setContentType(MediaType.APPLICATION_JSON);
        var payload = Map.of("user_id", userId);
        return rt.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class).getBody();
    }

    /** Gera as ~44 questões do simulado normal em uma chamada */
    public Map<String,Object> gerarSimuladoOriginal(String userId) {
        String url = base + originalPath;
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var payload = Map.of("user_id", userId); // <- sem topic
        return rt.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class).getBody();
    }
}