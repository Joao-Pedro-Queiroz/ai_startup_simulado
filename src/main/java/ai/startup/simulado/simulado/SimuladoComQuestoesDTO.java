package ai.startup.simulado.simulado;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

public record SimuladoComQuestoesDTO(
        String id_simulado,
        String id_usuario,
        String tipo,
        LocalDateTime data,
        String status,
        Integer fatura_wins,
        List<Map<String,Object>> questoes // resposta “transparente” do serviço de questões
) {}
