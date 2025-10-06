package ai.startup.simulado.perfil;

import java.util.Map;

public record TopicDTO(
  Map<String, SubskillDTO> subskills
) {}
