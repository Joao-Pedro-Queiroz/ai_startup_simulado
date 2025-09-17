package ai.startup.simulado.perfil;

public record PerfilCreateDTO(
        String user_id,
        String topic,
        String subskill,
        Long acertos,
        Long erros,
        Double acuracia
) {}
