package ai.startup.simulado.custompractice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Client para comunicação com o serviço approva-descartes (porta 8085).
 * Responsável por enviar as especificações do custom practice e receber
 * as questões geradas pela IA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomPracticeClient {

    private final RestTemplate restTemplate;
    
    @Value("${api.modelo.base}")
    private String modeloBase;
    
    @Value("${api.modelo.custom-exam-path:/v1/custom_exam}")
    private String customExamPath;

    /**
     * Chama o approva-descartes para gerar questões customizadas.
     * 
     * @param planItems Lista de itens com topic, subskill, structure, difficulty, count
     * @return Map com "questions": [array de questões]
     * @throws RuntimeException se houver erro na comunicação ou resposta inválida
     */
    public Map<String, Object> gerarCustomExam(List<CustomPracticeItemDTO> planItems) {
        String url = modeloBase + customExamPath;
        
        log.info("[CUSTOM CLIENT] ========================================");
        log.info("[CUSTOM CLIENT] Chamando approva-descartes: {}", url);
        log.info("[CUSTOM CLIENT] Total de plan items: {}", planItems.size());
        
        for (CustomPracticeItemDTO item : planItems) {
            log.info("[CUSTOM CLIENT]   - {} / {} / {} / {} (count: {})", 
                item.getTopic(), item.getSubskill(), item.getStructure(), 
                item.getDifficulty(), item.getCount());
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<List<CustomPracticeItemDTO>> request = 
            new HttpEntity<>(planItems, headers);
        
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            
            if (body != null && body.containsKey("questions")) {
                List<?> questions = (List<?>) body.get("questions");
                log.info("[CUSTOM CLIENT] ✅ Questões geradas com sucesso: {}", questions.size());
                log.info("[CUSTOM CLIENT] ========================================");
                return body;
            } else {
                log.error("[CUSTOM CLIENT] ❌ Resposta inválida do approva-descartes");
                log.error("[CUSTOM CLIENT] Body recebido: {}", body);
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Resposta inválida do serviço de geração de questões"
                );
            }
            
        } catch (HttpClientErrorException e) {
            log.error("[CUSTOM CLIENT] ❌ Erro HTTP 4xx do approva-descartes: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            log.error("[CUSTOM CLIENT] ========================================");
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Erro ao comunicar com serviço de geração de questões: " + e.getMessage(),
                e
            );
        } catch (HttpServerErrorException e) {
            log.error("[CUSTOM CLIENT] ❌ Erro HTTP 5xx do approva-descartes: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            log.error("[CUSTOM CLIENT] ========================================");
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Serviço de geração de questões retornou erro: " + e.getMessage(),
                e
            );
        } catch (ResourceAccessException e) {
            log.error("[CUSTOM CLIENT] ❌ Erro de conexão com approva-descartes: {}", e.getMessage());
            log.error("[CUSTOM CLIENT] URL tentada: {}", url);
            log.error("[CUSTOM CLIENT] ========================================");
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Não foi possível conectar ao serviço de geração de questões. Verifique se o serviço está rodando.",
                e
            );
        } catch (RestClientException e) {
            log.error("[CUSTOM CLIENT] ❌ Erro do RestTemplate ao chamar approva-descartes: {}", e.getMessage());
            log.error("[CUSTOM CLIENT] ========================================");
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Erro ao comunicar com serviço de geração de questões: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            log.error("[CUSTOM CLIENT] ❌ Erro inesperado ao chamar approva-descartes: {}", e.getMessage(), e);
            log.error("[CUSTOM CLIENT] ========================================");
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro inesperado ao gerar questões customizadas: " + e.getMessage(),
                e
            );
        }
    }
}

