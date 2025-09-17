package ai.startup.simulado.simulado;

/** payload para iniciar simulado adaptativo/original */
public record StartAdaptativoDTO(
        String topic,
        Integer fatura_wins
) {}