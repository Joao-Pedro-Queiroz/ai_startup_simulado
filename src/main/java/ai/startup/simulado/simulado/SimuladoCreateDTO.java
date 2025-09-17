package ai.startup.simulado.simulado;

import java.time.LocalDateTime;

public record SimuladoCreateDTO(
        String id_usuario,
        String tipo,
        LocalDateTime data,
        String status,
        Integer fatura_wins
) {}