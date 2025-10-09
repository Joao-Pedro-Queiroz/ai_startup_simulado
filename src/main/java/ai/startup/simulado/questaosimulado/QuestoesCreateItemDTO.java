package ai.startup.simulado.questaosimulado;

import java.util.List;
import java.util.Map;

/** Payload enviado do serviço de Simulado -> serviço de Questões */
public record QuestoesCreateItemDTO(
        String id_formulario,
        String id_usuario,

        String topic,
        String subskill,
        String difficulty,
        String question,

        Map<String,String> options,     // pode ser vazio/null no free_response
        Object correct_option,          // "A"/"B"/... ou -1 (free_response)

        // legado (se o modelo ainda enviar)
        List<String> solution,

        String structure,
        String format,
        String representation,

        // legado (se vier)
        String hint,

        List<String> target_mistakes,
        String source,

        // novos campos bilíngues/figura
        List<String> solution_english,
        List<String> solution_portugues,
        String hint_english,
        String hint_portugues,
        Map<String,Object> figure,

        // campos do app
        String alternativa_marcada,
        Boolean dica,
        Boolean solucao,
        Integer modulo   // 1 ou 2 (módulo adaptativo); no original use 1
) {}