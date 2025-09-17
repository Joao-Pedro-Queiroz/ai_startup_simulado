package ai.startup.simulado.simulado;

import java.time.LocalDateTime;

public record SimuladoUpdateDTO(
        String id_usuario,
        String tipo,
        LocalDateTime data,
        String status,
        Integer fatura_wins
) {}