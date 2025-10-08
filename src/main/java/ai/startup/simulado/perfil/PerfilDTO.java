package ai.startup.simulado.perfil;

import java.util.Map;

public record PerfilDTO(
        String id,
        String user_id,
        Map<String, TopicDTO> topics
) {}