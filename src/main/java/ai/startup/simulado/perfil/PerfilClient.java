package ai.startup.simulado.perfil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class PerfilClient {
    private final RestTemplate rt;
    private final String base;

    public PerfilClient(RestTemplate rt, @Value("${api.perfil.base}") String base) {
        this.rt = rt; this.base = base;
    }

    public void criarPerfis(String bearerToken, List<PerfilCreateDTO> itens) {
        var url = base + "/perfis";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        rt.exchange(url, HttpMethod.POST, new HttpEntity<>(itens, headers), List.class);
    }
}
