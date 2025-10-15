package ai.startup.simulado.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UsuarioClient {
    private final RestTemplate rt;
    private final String base;
    public UsuarioClient(RestTemplate rt, @Value("${api.usuario.base}") String base) {
        this.rt = rt; this.base = base;
    }

    public UsuarioDTO me(String bearerToken) {
        String url = base + "/users/me";
        var headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        var resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), UsuarioDTO.class);
        var body = resp.getBody();
        if (body == null) throw new RuntimeException("Resposta vazia do serviço de usuário");
        return body;
    }
}