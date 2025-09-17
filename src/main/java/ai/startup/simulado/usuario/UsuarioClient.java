package ai.startup.simulado.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class UsuarioClient {
    private final RestTemplate rt;
    private final String base;

    public UsuarioClient(RestTemplate rt, @Value("${api.usuario.base}") String base) {
        this.rt = rt; this.base = base;
    }

    public String getUserIdByEmail(String email, String bearerToken) {
        String url = base + "/users/by-email?email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
        var headers = new HttpHeaders(); headers.set("Authorization", bearerToken);
        var resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<?,?> body = resp.getBody();
        if (body == null) throw new RuntimeException("Resposta vazia do serviço de usuário");
        Object id = body.get("id");
        if (id == null) throw new RuntimeException("Usuário não encontrado por e-mail");
        return id.toString();
    }
}
