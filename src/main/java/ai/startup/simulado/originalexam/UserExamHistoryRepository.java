package ai.startup.simulado.originalexam;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserExamHistoryRepository extends MongoRepository<UserExamHistory, String> {
    
    Optional<UserExamHistory> findByUserId(String userId);
    
    boolean existsByUserId(String userId);
}

