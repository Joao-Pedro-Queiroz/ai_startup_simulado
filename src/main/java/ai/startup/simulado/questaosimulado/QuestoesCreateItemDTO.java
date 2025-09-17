package ai.startup.simulado.questaosimulado;

import java.util.List;
import java.util.Map;

/** item para POST /questoes do serviço de questões (snake_case) */
public record QuestoesCreateItemDTO(
        String id_formulario,
        String id_usuario,
        String topic,
        String subskill,
        String difficulty,
        String question,
        Map<String,String> options,
        String correct_option,
        List<String> solution,
        String structure,
        String format,
        String representation,
        String hint,
        java.util.List<String> target_mistakes,
        String source,
        String alternativa_marcada,
        Boolean dica,
        Boolean solucao,
        Integer modulo
) {}
