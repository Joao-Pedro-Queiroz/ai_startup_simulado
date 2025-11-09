package ai.startup.simulado.custompractice;

import lombok.Data;

/**
 * DTO que representa uma seleção do usuário no MindMap.
 * Contém informações sobre o skill, subskill e structure escolhidos.
 */
@Data
public class CustomPracticeSelectionDTO {
    
    private String skillName;      // ex: "Algebra"
    private String subskillName;   // ex: "Absolute Value"
    private String structureName;  // ex: "Identifying Solutions"
    
    // IDs para rastreamento (formato snake_case usado pelo MongoDB)
    private String skillId;        // ex: "algebra"
    private String subskillId;     // ex: "absolute_value"
    private String structureId;    // ex: "identifying_solutions"
}
