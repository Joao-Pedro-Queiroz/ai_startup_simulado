package ai.startup.simulado.simulado;

/**
 * DTO para estatísticas do usuário (best score, total simulados)
 * Otimiza performance ao evitar buscar todas as questões
 */
public record UserStatsDTO(
    Integer bestScore,      // melhor score em porcentagem (0-100)
    Integer totalSimulados  // total de simulados finalizados
) {}

