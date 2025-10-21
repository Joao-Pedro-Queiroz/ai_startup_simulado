package ai.startup.simulado.usuario;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    public UsuarioDTO atualizar(String bearerToken, String idUsuario, UsuarioUpdateDTO dto) {
        String url = base + "/users/" + idUsuario;
        var headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<UsuarioDTO> resp =
                    rt.exchange(url, HttpMethod.PUT, new HttpEntity<>(dto, headers), UsuarioDTO.class);

            UsuarioDTO body = resp.getBody();
            if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta vazia ao atualizar usuário");
            return body;
        } catch (HttpStatusCodeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Falha ao atualizar usuário: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao atualizar usuário.", e);
        }
    }
}