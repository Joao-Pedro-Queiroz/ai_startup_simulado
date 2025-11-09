package ai.startup.simulado.custompractice;

import lombok.Data;

/**
 * DTO no formato esperado pelo approva-descartes (/v1/custom_exam).
 * Cada item representa um conjunto de questões de uma estrutura específica.
 */
@Data
public class CustomPracticeItemDTO {
    
    private String topic;      // "algebra", "geometry_and_trigonometry"
    private String subskill;   // "absolute_value", "circles"
    private String structure;  // "identifying_solutions", "area_circumference"
    private String difficulty; // "Easy", "Medium", "Hard"
    private Integer count;     // quantidade de questões
}
