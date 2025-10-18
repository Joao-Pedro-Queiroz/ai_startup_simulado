package ai.startup.simulado.simulado;

import ai.startup.simulado.questaosimulado.FinalizarQuestaoItemDTO;
import java.time.LocalDateTime;
import java.util.List;

public record FinalizarSimuladoRequestFlat(
    String id_simulado,
    String id_usuario,
    String tipo,
    LocalDateTime data,
    String status,          // será ignorado e forçado p/ FINALIZADO
    Integer fatura_wins,
    List<FinalizarQuestaoItemDTO> questoes
) {}
