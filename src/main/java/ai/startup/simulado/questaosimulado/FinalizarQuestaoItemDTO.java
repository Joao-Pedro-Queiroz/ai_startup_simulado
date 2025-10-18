package ai.startup.simulado.questaosimulado;

import java.util.List;
import java.util.Map;

public record FinalizarQuestaoItemDTO(
        String id,                // <-- NECESSÁRIO
        String id_formulario,
        String id_usuario,
        String topic,
        String subskill,
        String difficulty,
        String question,
        Map<String,String> options,
        Object correct_option,
        List<String> solution,
        String structure,
        String format,
        String representation,
        String hint,
        List<String> target_mistakes,
        String source,

        List<String> solution_english,
        List<String> solution_portugues,
        String hint_english,
        String hint_portugues,
        Map<String,Object> figure,

        String alternativa_marcada,
        Boolean dica,
        Boolean solucao,
        Integer modulo
) {}