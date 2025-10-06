package ai.startup.simulado.perfil;

import java.util.Map;

public record PerfilCreateDTO(
  String user_id,
  Map<String, TopicDTO> topics
) {}
