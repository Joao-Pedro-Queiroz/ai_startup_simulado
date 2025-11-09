package ai.startup.simulado.simulado;

import java.util.List;
import java.util.Map;

/**
 * DTO de resposta que combina um simulado com suas questões.
 * Usado ao criar simulados (adaptativo, original, custom practice).
 */
public record SimuladoComQuestoesDTO(
        SimuladoDTO simulado,
        List<Map<String, Object>> questoes
) {}
