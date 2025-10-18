package ai.startup.simulado.perfil;

import ai.startup.simulado.support.TemplateLoader;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PerfilTemplateProvider {
    private final TemplateLoader loader;

    public PerfilTemplateProvider(TemplateLoader loader) {
        this.loader = loader;
    }

    /** Retorna apenas o mapa de topics já convertido/zerado */
    public Map<String, TopicDTO> getTopicsTemplate(String userId) {
        Map<String,Object> raw = loader.loadProfileTemplate(userId);
        Map<String,Object> topics = (Map<String,Object>) raw.get("topics");
        // Converta para DTOs; se já possui mappers prontos, reusa-os.
        return PerfilTemplateMapper.toTopicsDTOZeroed(topics); // implemente conforme seus DTOs
    }
}
