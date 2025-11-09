package ai.startup.simulado.custompractice;

import lombok.Data;
import java.util.List;

/**
 * DTO de requisição para criar um Custom Practice.
 * Contém o usuário, as seleções feitas no MindMap e o total de questões desejado.
 */
@Data
public class CustomPracticeRequestDTO {
    
    private String usuarioId;
    private List<CustomPracticeSelectionDTO> selections;
    private Integer totalQuestions; // ex: 10, 20, 30
}
