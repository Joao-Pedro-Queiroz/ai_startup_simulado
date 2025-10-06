package ai.startup.simulado.perfil;

public record StructureDTO(
        Integer P_sc,
        Long attempts_sc,
        Long correct_sc,
        Double hints_rate_sc,
        Double solutions_rate_sc,
        Boolean easy_seen_sc,
        Boolean medium_seen_sc,
        Boolean hard_seen_sc,
        Long medium_exposures_sc,
        Long hard_exposures_sc,
        String last_level_applied_sc,
        Integer cooldown_sc,
        String last_seen_at_sc
) {}