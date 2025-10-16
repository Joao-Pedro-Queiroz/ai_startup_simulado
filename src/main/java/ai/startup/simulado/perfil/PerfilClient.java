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

    // (opcional, manter) POST /perfis -> upsert por user_id
    public void criarOuAtualizarPerfil(String bearerToken, PerfilCreateDTO item) {
        var url = base + "/perfis";
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        rt.exchange(url, HttpMethod.POST, new HttpEntity<>(item, headers), Void.class);
    }

    // NOVO: PUT /perfis/by-usuario/{userId} -> upsert/merge garantido por userId
    public void atualizarPerfilPorUsuario(String bearerToken, String userId, PerfilCreateDTO item) {
        var url = base + "/perfis/by-usuario/" + userId;
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", bearerToken);
        rt.exchange(url, HttpMethod.PUT, new HttpEntity<>(item, headers), Void.class);
    }
}