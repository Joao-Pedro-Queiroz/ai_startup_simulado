package ai.startup.simulado.perfil;

import ai.startup.simulado.support.TemplateLoader;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PerfilTemplateProvider {
    private final TemplateLoader templateLoader;

    public PerfilTemplateProvider(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }

    /** Retorna o mapa completo de topics/subskills/structures do template, como DTOs zerados */
    public Map<String, TopicDTO> getTopicsTemplate(String userId) {
        Map<String, Object> raw = templateLoader.loadProfileTemplate(userId);
        // raw.get("topics") é um Map<String,Object> com a árvore do template
        Map<String, Object> topicsRaw = (Map<String, Object>) raw.get("topics");
        return mapRawTopicsToDTO(topicsRaw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, TopicDTO> mapRawTopicsToDTO(Map<String, Object> topicsRaw) {
        Map<String, TopicDTO> out = new HashMap<>();
        if (topicsRaw == null) return out;

        topicsRaw.forEach((topicName, topicObj) -> {
            Map<String, Object> topicMap = (Map<String, Object>) topicObj;
            Map<String, Object> subsRaw  = (Map<String, Object>) topicMap.get("subskills");

            Map<String, SubskillDTO> subsDTO = new HashMap<>();
            if (subsRaw != null) {
                subsRaw.forEach((subName, subObj) -> {
                    Map<String, Object> subMap  = (Map<String, Object>) subObj;
                    Map<String, Object> structs = (Map<String, Object>) subMap.get("structures");

                    Map<String, StructureDTO> stDTOs = new HashMap<>();
                    if (structs != null) {
                        structs.forEach((stName, __) -> {
                            stDTOs.put(stName, new StructureDTO(
                                    50, 0L, 0L, 0.0, 0.0,
                                    false, false, false,
                                    0L, 0L,
                                    "easy", 0, null
                            ));
                        });
                    }

                    Long total = subMap.get("total_estruturas_s") instanceof Number n ? n.longValue() : (long)stDTOs.size();
                    subsDTO.put(subName, new SubskillDTO(
                            0L, 0L, 0.0, 0.0, null,
                            null,
                            false, false, false,
                            0L, total,
                            stDTOs
                    ));
                });
            }
            out.put(topicName, new TopicDTO(subsDTO));
        });
        return out;
    }
}
