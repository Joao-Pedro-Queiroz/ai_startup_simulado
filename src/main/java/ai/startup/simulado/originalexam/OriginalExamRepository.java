package ai.startup.simulado.originalexam;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OriginalExamRepository extends MongoRepository<OriginalExam, String> {
    
    Optional<OriginalExam> findByExamId(String examId);
    
    List<OriginalExam> findByIsActiveTrue();
    
    long countByIsActiveTrue();
}

