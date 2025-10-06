package ai.startup.simulado.perfil;

import java.util.Map;

public record SubskillDTO(
        Long attempts_s,
        Long correct_s,
        Double hints_rate_s,
        Double solutions_rate_s,
        String last_seen_at_s,
        Boolean easy_seen_s,
        Boolean medium_seen_s,
        Boolean hard_seen_s,
        Long estruturas_vistas_s,
        Long total_estruturas_s,
        Map<String, StructureDTO> structures
) {}