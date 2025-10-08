package ai.startup.simulado.perfil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PerfilClient {
    private final RestTemplate rt;
    private final String base;

    public PerfilClient(RestTemplate rt, @Value("${api.perfil.base}") String base) {
        this.rt = rt; this.base = base;
    }

    public void criarPerfil(String bearerToken, PerfilCreateDTO item) {
        var url = base + "/perfis"; // endpoint agora aceita UM objeto
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        rt.exchange(url, HttpMethod.POST, new HttpEntity<>(item, headers), PerfilDTO.class);
        // Se não precisa da resposta, pode usar Void.class
    }
}