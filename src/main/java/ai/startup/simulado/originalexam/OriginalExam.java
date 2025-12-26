package ai.startup.simulado.originalexam;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "original_exams")
public class OriginalExam {
    
    @Id
    private String id;
    
    @Field("exam_id")
    private String examId;
    
    private Integer version;
    private String name;
    
    @Field("difficulty_level")
    private String difficultyLevel;
    
    @Field("is_active")
    private Boolean isActive;
    
    @Field("is_adaptive")
    private Boolean isAdaptive;
    
    @Field("created_at")
    private LocalDateTime createdAt;
    
    private Metadata metadata;
    
    // Para simulados adaptativos (isAdaptive = true)
    @Field("module_1")
    private List<ExamQuestion> module1;
    
    @Field("module_2_easy")
    private List<ExamQuestion> module2Easy;
    
    @Field("module_2_hard")
    private List<ExamQuestion> module2Hard;
    
    // Para simulados fixos (isAdaptive = false) - DEPRECATED mas mantido para compatibilidade
    private List<ExamQuestion> questions;

    @Data
    public static class Metadata {
        @Field("total_questions")
        private Integer totalQuestions;
        
        @Field("module_1_questions")
        private Integer module1Questions;
        
        @Field("module_2_questions")
        private Integer module2Questions;
        
        private Integer threshold;
        
        @Field("duration_minutes")
        private Integer durationMinutes;
        
        @Field("topics_distribution")
        private Map<String, Integer> topicsDistribution;
    }

    @Data
    public static class ExamQuestion {
        @Field("question_number")
        private Integer questionNumber;
        
        private String topic;
        private String subskill;
        private String difficulty;
        private String question;
        private Map<String, String> options;
        
        @Field("correct_option")
        private String correctOption;
        
        private String hintEnglish;
        private String hintPortugues;
        private List<String> solutionEnglish;
        private List<String> solutionPortugues;
        private String representation;
        private String format;
        private String structure;
        
        private Map<String, Object> figure;
    }
}

