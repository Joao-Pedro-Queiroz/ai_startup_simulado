package ai.startup.simulado.perfil;

import java.util.HashMap;
import java.util.Map;

public final class PerfilTemplateMapper {

    private PerfilTemplateMapper() {}

    /** Converte o mapa "topics" do template para DTOs zerados (contadores/rates/flags). */
    @SuppressWarnings("unchecked")
    public static Map<String, TopicDTO> toTopicsDTOZeroed(Map<String, Object> rawTopics) {
        Map<String, TopicDTO> topics = new HashMap<>();
        if (rawTopics == null) return topics;

        rawTopics.forEach((topicName, topicObj) -> {
            Map<String, Object> topicMap = (Map<String, Object>) topicObj;
            Map<String, Object> rawSubskills = (Map<String, Object>) topicMap.get("subskills");

            Map<String, SubskillDTO> subskills = new HashMap<>();
            if (rawSubskills != null) {
                rawSubskills.forEach((subName, subObj) -> {
                    Map<String, Object> subMap = (Map<String, Object>) subObj;
                    Map<String, Object> rawStructs = (Map<String, Object>) subMap.get("structures");

                    Map<String, StructureDTO> structures = new HashMap<>();
                    int totalStructs = 0;
                    if (rawStructs != null) {
                        for (var e : rawStructs.entrySet()) {
                            String stName = e.getKey();
                            totalStructs++;
                            // ZERADO por padrão (P_sc neutro 50; last_level "easy")
                            structures.put(stName, new StructureDTO(
                                    50, 0L, 0L, 0.0, 0.0,
                                    false, false, false,
                                    0L, 0L,
                                    "easy", 0, null
                            ));
                        }
                    }

                    subskills.put(subName, new SubskillDTO(
                            0L, 0L, 0.0, 0.0, "",
                            null,               // missed_two_sessions não recalculado aqui
                            false, false, false,
                            0L, (long) totalStructs,
                            structures
                    ));
                });
            }

            topics.put(topicName, new TopicDTO(subskills));
        });

        return topics;
    }
}