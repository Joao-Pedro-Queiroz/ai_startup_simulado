package ai.startup.simulado.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
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
        log.info("ModeloClient configurado - Base URL: {}, Adaptativo Path: {}, Original Path: {}", 
                base, adaptativoPath, originalPath);
    }

    /** Gera ~22 questões (módulo) – usado 2x no adaptativo */
    public Map<String,Object> gerarModuloAdaptativo(String userId) {
        String url = base + adaptativoPath;
        log.info("Chamando serviço de modelo para gerar módulo adaptativo - URL: {}, UserId: {}", url, userId);
        
        try {
            var headers = new HttpHeaders(); 
            headers.setContentType(MediaType.APPLICATION_JSON);
            var payload = Map.of("user_id", userId);
            log.debug("Payload enviado: {}", payload);
            
            long startTime = System.currentTimeMillis();
            var response = rt.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Resposta do serviço de modelo recebida com sucesso em {}ms. Status: {}", 
                    duration, response.getStatusCode());
            
            if (response.getBody() == null) {
                log.warn("Resposta do serviço de modelo está vazia");
            }
            
            return response.getBody();
        } catch (ResourceAccessException e) {
            String errorMsg = String.format(
                "Servidor temporariamente indisponível. Verifique se o serviço de modelo está rodando na porta 8085. " +
                "Erro de conexão: %s", e.getMessage());
            log.error("Erro ao conectar com o serviço de modelo em {}: {} - Causa: {}", 
                    url, e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "N/A");
            throw new RuntimeException(errorMsg, e);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            String errorMsg = String.format(
                "Erro no serviço de modelo (HTTP %s): %s", 
                e.getStatusCode(), 
                responseBody != null && !responseBody.isEmpty() ? responseBody : e.getMessage());
            log.error("Erro HTTP 5xx do serviço de modelo: {} - Body: {} - Headers: {}", 
                    e.getStatusCode(), responseBody, e.getResponseHeaders());
            throw new RuntimeException(errorMsg, e);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            String errorMsg = String.format(
                "Erro na requisição ao serviço de modelo (HTTP %s): %s", 
                e.getStatusCode(),
                responseBody != null && !responseBody.isEmpty() ? responseBody : e.getMessage());
            log.error("Erro HTTP 4xx do serviço de modelo: {} - Body: {}", e.getStatusCode(), responseBody);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("Erro ao gerar módulo adaptativo: %s", e.getMessage());
            log.error("Erro inesperado ao chamar serviço de modelo em {}: {} - Tipo: {}", 
                    url, e.getMessage(), e.getClass().getSimpleName(), e);
            throw new RuntimeException(errorMsg, e);
        }
    }

    /** Gera as ~44 questões do simulado normal em uma chamada */
    public Map<String,Object> gerarSimuladoOriginal(String userId) {
        String url = base + originalPath;
        log.info("Chamando serviço de modelo para gerar simulado original - URL: {}, UserId: {}", url, userId);
        
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            var payload = Map.of("user_id", userId); // <- sem topic
            log.debug("Payload enviado: {}", payload);
            
            long startTime = System.currentTimeMillis();
            var response = rt.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Resposta do serviço de modelo recebida com sucesso em {}ms. Status: {}", 
                    duration, response.getStatusCode());
            
            if (response.getBody() == null) {
                log.warn("Resposta do serviço de modelo está vazia");
            }
            
            return response.getBody();
        } catch (ResourceAccessException e) {
            String errorMsg = String.format(
                "Servidor temporariamente indisponível. Verifique se o serviço de modelo está rodando na porta 8085. " +
                "Erro de conexão: %s", e.getMessage());
            log.error("Erro ao conectar com o serviço de modelo em {}: {} - Causa: {}", 
                    url, e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "N/A");
            throw new RuntimeException(errorMsg, e);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            String errorMsg = String.format(
                "Erro no serviço de modelo (HTTP %s): %s", 
                e.getStatusCode(), 
                responseBody != null && !responseBody.isEmpty() ? responseBody : e.getMessage());
            log.error("Erro HTTP 5xx do serviço de modelo: {} - Body: {} - Headers: {}", 
                    e.getStatusCode(), responseBody, e.getResponseHeaders());
            throw new RuntimeException(errorMsg, e);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            String errorMsg = String.format(
                "Erro na requisição ao serviço de modelo (HTTP %s): %s", 
                e.getStatusCode(),
                responseBody != null && !responseBody.isEmpty() ? responseBody : e.getMessage());
            log.error("Erro HTTP 4xx do serviço de modelo: {} - Body: {}", e.getStatusCode(), responseBody);
            throw new RuntimeException(errorMsg, e);
        } catch (Exception e) {
            String errorMsg = String.format("Erro ao gerar simulado original: %s", e.getMessage());
            log.error("Erro inesperado ao chamar serviço de modelo em {}: {} - Tipo: {}", 
                    url, e.getMessage(), e.getClass().getSimpleName(), e);
            throw new RuntimeException(errorMsg, e);
        }
    }
}