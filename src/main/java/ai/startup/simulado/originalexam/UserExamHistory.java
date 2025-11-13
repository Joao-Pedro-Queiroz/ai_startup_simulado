package ai.startup.simulado.originalexam;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "user_exam_history")
public class UserExamHistory {
    
    @Id
    private String id;
    
    @Field("user_id")
    private String userId;
    
    @Field("completed_original_exams")
    private List<CompletedExam> completedOriginalExams = new ArrayList<>();
    
    @Field("current_original_exam")
    private String currentOriginalExam;
    
    @Field("updated_at")
    private LocalDateTime updatedAt;

    @Data
    public static class CompletedExam {
        @Field("exam_id")
        private String examId;
        
        @Field("completed_at")
        private LocalDateTime completedAt;
        
        @Field("attempt_id")
        private String attemptId;
        
        private Integer score;
        
        @Field("time_taken_minutes")
        private Integer timeTakenMinutes;
        
        @Field("module1_score")
        private Integer module1Score;
        
        @Field("module2_type")
        private String module2Type; // "easy" ou "hard"
    }
}

