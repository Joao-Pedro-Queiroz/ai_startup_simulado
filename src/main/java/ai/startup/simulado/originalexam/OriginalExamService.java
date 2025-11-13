package ai.startup.simulado.originalexam;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OriginalExamService {
    
    private final OriginalExamRepository examRepository;
    private final UserExamHistoryRepository historyRepository;
    private final Random random = new Random();

    /**
     * Buscar IDs dos simulados disponíveis para o usuário
     * @param userId ID do usuário
     * @return Lista de exam_ids disponíveis (não completados)
     */
    public List<String> getAvailableExamIds(String userId) {
        log.info("[OriginalExam] Buscando simulados disponíveis para userId: {}", userId);
        
        // Buscar histórico do usuário
        UserExamHistory history = historyRepository.findByUserId(userId)
            .orElse(new UserExamHistory());
        
        // IDs já completados
        List<String> completedIds = history.getCompletedOriginalExams()
            .stream()
            .map(UserExamHistory.CompletedExam::getExamId)
            .collect(Collectors.toList());
        
        log.info("[OriginalExam] Simulados já completados: {}", completedIds);
        
        // Todos os simulados ativos
        List<String> allActiveIds = examRepository.findByIsActiveTrue()
            .stream()
            .map(OriginalExam::getExamId)
            .collect(Collectors.toList());
        
        log.info("[OriginalExam] Total de simulados ativos no sistema: {}", allActiveIds.size());
        
        // Retornar apenas os que não foram completados
        List<String> available = allActiveIds.stream()
            .filter(id -> !completedIds.contains(id))
            .collect(Collectors.toList());
        
        log.info("[OriginalExam] Simulados disponíveis para este usuário: {}", available);
        
        return available;
    }

    /**
     * Selecionar aleatoriamente um simulado disponível
     * @param userId ID do usuário
     * @return exam_id selecionado ou null se todos já foram completados
     */
    public String selectRandomAvailableExam(String userId) {
        log.info("[OriginalExam] Selecionando simulado aleatório para userId: {}", userId);
        
        List<String> available = getAvailableExamIds(userId);
        
        if (available.isEmpty()) {
            log.warn("[OriginalExam] Usuário {} já completou todos os simulados originais!", userId);
            return null;
        }
        
        // Selecionar aleatoriamente
        int index = random.nextInt(available.size());
        String selected = available.get(index);
        
        log.info("[OriginalExam] Simulado selecionado: {} (índice {} de {} disponíveis)", 
                 selected, index, available.size());
        
        return selected;
    }

    /**
     * Buscar próximo simulado original para o usuário e retornar em formato Map
     * Prioriza SAT_ORIGINAL_001, depois 002, 003, etc.
     * @param userId ID do usuário
     * @return Map com os dados do exam (module_1, module_2_easy, module_2_hard, metadata, exam_id)
     */
    public Map<String, Object> getNextExamForUser(String userId) {
        log.info("[OriginalExam] Buscando próximo simulado original para userId: {}", userId);
        
        // Buscar simulados disponíveis (não completados)
        List<String> available = getAvailableExamIds(userId);
        
        if (available.isEmpty()) {
            log.error("[OriginalExam] ❌ Usuário {} já completou todos os simulados originais!", userId);
            throw new RuntimeException("Todos os simulados originais já foram completados por este usuário.");
        }
        
        // Ordenar por ordem numérica (SAT_ORIGINAL_001, 002, 003...)
        available.sort(String::compareTo);
        
        // Pegar o primeiro disponível
        String nextExamId = available.get(0);
        
        log.info("[OriginalExam] 🎯 Próximo simulado selecionado: {}", nextExamId);
        
        // Buscar o exam completo
        OriginalExam exam = examRepository.findByExamId(nextExamId)
            .orElseThrow(() -> new RuntimeException("Simulado " + nextExamId + " não encontrado no banco de dados!"));
        
        // Marcar como iniciado
        markExamAsStarted(userId, nextExamId);
        
        // Converter para Map (compatível com SimuladoService)
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("exam_id", exam.getExamId());
        result.put("module_1", exam.getModule1());
        result.put("module_2_easy", exam.getModule2Easy());
        result.put("module_2_hard", exam.getModule2Hard());
        result.put("metadata", exam.getMetadata());
        result.put("is_adaptive", exam.getIsAdaptive());
        result.put("is_active", exam.getIsActive());
        
        log.info("[OriginalExam] ✅ Retornando simulado {} com {} questões no Módulo 1", 
                 nextExamId, exam.getModule1() != null ? exam.getModule1().size() : 0);
        
        return result;
    }

    /**
     * Buscar simulado completo por exam_id
     * @param examId ID do simulado
     * @return OriginalExam ou null se não encontrado
     */
    public OriginalExam getExamByExamId(String examId) {
        log.info("[OriginalExam] Buscando simulado: {}", examId);
        return examRepository.findByExamId(examId).orElse(null);
    }

    /**
     * Buscar apenas Módulo 1 de um simulado
     * @param examId ID do simulado
     * @return Lista de questões do Módulo 1
     */
    public List<OriginalExam.ExamQuestion> getModule1Questions(String examId) {
        log.info("[OriginalExam] Buscando Módulo 1 do simulado: {}", examId);
        OriginalExam exam = examRepository.findByExamId(examId).orElse(null);
        
        if (exam == null) {
            log.error("[OriginalExam] ❌ Simulado {} não encontrado!", examId);
            return null;
        }
        
        // Se for adaptativo, retorna module1
        if (Boolean.TRUE.equals(exam.getIsAdaptive()) && exam.getModule1() != null) {
            log.info("[OriginalExam] ✅ Retornando {} questões do Módulo 1", exam.getModule1().size());
            return exam.getModule1();
        }
        
        // Se for fixo (compatibilidade), retorna todas as questões
        log.warn("[OriginalExam] ⚠️ Simulado {} não é adaptativo, retornando todas as questões", examId);
        return exam.getQuestions();
    }

    /**
     * Buscar Módulo 2 baseado na performance do Módulo 1
     * @param examId ID do simulado
     * @param module1Correct Número de questões corretas no Módulo 1
     * @return Lista de questões do Módulo 2 (easy ou hard)
     */
    public Map<String, Object> getModule2Questions(String examId, Integer module1Correct) {
        log.info("[OriginalExam] Buscando Módulo 2 do simulado: {} | Corretas no M1: {}", 
                 examId, module1Correct);
        
        OriginalExam exam = examRepository.findByExamId(examId).orElse(null);
        
        if (exam == null) {
            log.error("[OriginalExam] ❌ Simulado {} não encontrado!", examId);
            return null;
        }
        
        if (!Boolean.TRUE.equals(exam.getIsAdaptive())) {
            log.error("[OriginalExam] ❌ Simulado {} não é adaptativo!", examId);
            return null;
        }
        
        // Threshold padrão: 16 (pode vir do metadata)
        Integer threshold = exam.getMetadata() != null && exam.getMetadata().getThreshold() != null 
                          ? exam.getMetadata().getThreshold() 
                          : 16;
        
        // Determinar qual módulo 2 usar
        boolean useHard = module1Correct > threshold;
        String moduleType = useHard ? "hard" : "easy";
        List<OriginalExam.ExamQuestion> questions = useHard ? exam.getModule2Hard() : exam.getModule2Easy();
        
        log.info("[OriginalExam] 🎯 Corretas: {} | Threshold: {} | Módulo 2: {}", 
                 module1Correct, threshold, moduleType.toUpperCase());
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("module_type", moduleType);
        result.put("questions", questions);
        result.put("threshold_used", threshold);
        result.put("module1_correct", module1Correct);
        
        log.info("[OriginalExam] ✅ Retornando Módulo 2 {} com {} questões", 
                 moduleType.toUpperCase(), questions.size());
        
        return result;
    }

    /**
     * Marcar simulado como iniciado pelo usuário
     * @param userId ID do usuário
     * @param examId ID do simulado
     */
    public void markExamAsStarted(String userId, String examId) {
        log.info("[OriginalExam] Marcando simulado {} como iniciado para userId: {}", examId, userId);
        
        UserExamHistory history = historyRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserExamHistory newHistory = new UserExamHistory();
                newHistory.setUserId(userId);
                return newHistory;
            });
        
        history.setCurrentOriginalExam(examId);
        history.setUpdatedAt(LocalDateTime.now());
        
        historyRepository.save(history);
        
        log.info("[OriginalExam] ✅ Simulado {} marcado como em andamento", examId);
    }

    /**
     * Marcar simulado como completado
     * @param userId ID do usuário
     * @param examId ID do simulado
     * @param attemptId ID da tentativa no sistema
     * @param score Score obtido (0-100)
     * @param timeTaken Tempo gasto em minutos
     * @param module1Score Score do módulo 1 (opcional)
     * @param module2Type Tipo do módulo 2 usado: "easy" ou "hard" (opcional)
     */
    public void markExamAsCompleted(String userId, String examId, 
                                    String attemptId, Integer score, 
                                    Integer timeTaken, Integer module1Score,
                                    String module2Type) {
        log.info("[OriginalExam] Marcando simulado {} como completado para userId: {}", examId, userId);
        log.info("[OriginalExam] Score: {}%, Tempo: {} min, AttemptId: {}", score, timeTaken, attemptId);
        log.info("[OriginalExam] M1 Score: {}, M2 Type: {}", module1Score, module2Type);
        
        UserExamHistory history = historyRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserExamHistory newHistory = new UserExamHistory();
                newHistory.setUserId(userId);
                return newHistory;
            });
        
        // Verificar se já não foi completado (proteção contra duplicação)
        boolean alreadyCompleted = history.getCompletedOriginalExams()
            .stream()
            .anyMatch(e -> e.getExamId().equals(examId));
        
        if (alreadyCompleted) {
            log.warn("[OriginalExam] ⚠️ DUPLICAÇÃO DETECTADA! Simulado {} já foi completado anteriormente pelo usuário {}", 
                     examId, userId);
            return;
        }
        
        // Adicionar ao histórico
        UserExamHistory.CompletedExam completed = new UserExamHistory.CompletedExam();
        completed.setExamId(examId);
        completed.setCompletedAt(LocalDateTime.now());
        completed.setAttemptId(attemptId);
        completed.setScore(score);
        completed.setTimeTakenMinutes(timeTaken);
        completed.setModule1Score(module1Score);
        completed.setModule2Type(module2Type);
        
        history.getCompletedOriginalExams().add(completed);
        history.setCurrentOriginalExam(null); // Limpar "em andamento"
        history.setUpdatedAt(LocalDateTime.now());
        
        historyRepository.save(history);
        
        log.info("[OriginalExam] ✅ Simulado {} completado com sucesso! Total completados: {}", 
                 examId, history.getCompletedOriginalExams().size());
    }

    /**
     * Contar quantos simulados o usuário já completou
     * @param userId ID do usuário
     * @return Número de simulados completados
     */
    public int getCompletedCount(String userId) {
        return historyRepository.findByUserId(userId)
            .map(h -> h.getCompletedOriginalExams().size())
            .orElse(0);
    }

    /**
     * Buscar histórico completo do usuário
     * @param userId ID do usuário
     * @return UserExamHistory ou null
     */
    public UserExamHistory getUserHistory(String userId) {
        return historyRepository.findByUserId(userId).orElse(null);
    }

    /**
     * Verificar se usuário pode fazer determinado simulado
     * @param userId ID do usuário
     * @param examId ID do simulado
     * @return true se pode fazer, false se já completou
     */
    public boolean canUserTakeExam(String userId, String examId) {
        UserExamHistory history = historyRepository.findByUserId(userId)
            .orElse(new UserExamHistory());
        
        boolean alreadyCompleted = history.getCompletedOriginalExams()
            .stream()
            .anyMatch(e -> e.getExamId().equals(examId));
        
        return !alreadyCompleted;
    }

    /**
     * Buscar total de simulados ativos no sistema
     * @return Quantidade de simulados originais disponíveis
     */
    public long getTotalActiveExams() {
        return examRepository.countByIsActiveTrue();
    }

    /**
     * Contar total de usuários com histórico
     * @return Quantidade de usuários que já fizeram pelo menos 1 simulado
     */
    public long getTotalUsersWithHistory() {
        return historyRepository.count();
    }
}

