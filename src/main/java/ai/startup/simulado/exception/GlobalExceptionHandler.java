package ai.startup.simulado.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler global para capturar exceções e retornar respostas HTTP apropriadas.
 * Garante que erros sejam logados e que mensagens úteis sejam retornadas ao cliente.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura ResponseStatusException e retorna a resposta HTTP apropriada.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString());
        body.put("status", ex.getStatusCode().value());
        
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Captura exceções de comunicação com serviços externos (RestTemplate).
     */
    @ExceptionHandler({ResourceAccessException.class, RestClientException.class})
    public ResponseEntity<Map<String, Object>> handleRestClientException(RestClientException ex) {
        log.error("Erro ao comunicar com serviço externo: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Falha ao comunicar com serviço externo. Verifique se o serviço está disponível.");
        body.put("status", HttpStatus.BAD_GATEWAY.value());
        body.put("message", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * Captura IllegalArgumentException e retorna 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("IllegalArgumentException: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Captura IllegalStateException e retorna 409 Conflict ou 402 Payment Required.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.error("IllegalStateException: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        
        // Se a mensagem menciona saldo/wins, retorna 402, senão 409
        HttpStatus status = ex.getMessage().toLowerCase().contains("saldo") || 
                           ex.getMessage().toLowerCase().contains("wins")
                           ? HttpStatus.PAYMENT_REQUIRED 
                           : HttpStatus.CONFLICT;
        body.put("status", status.value());
        
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Captura todas as outras exceções não tratadas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Erro não tratado: {}", ex.getMessage(), ex);
        
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Erro interno do servidor. Tente novamente mais tarde.");
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("message", ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}

