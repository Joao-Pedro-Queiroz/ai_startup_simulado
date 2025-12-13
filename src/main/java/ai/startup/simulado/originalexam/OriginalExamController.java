package ai.startup.simulado.originalexam;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/simulados/original")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OriginalExamController {

    private final OriginalExamService service;

    /**
     * GET /api/simulados/original/available?userId=xxx
     * Retorna lista de simulados disponíveis para o usuário
     */
    @GetMapping("/available")
    public Map<String, Object> getAvailable(@RequestParam String userId) {
        log.info("[OriginalExamAPI] GET /available - userId: {}", userId);
        
        List<String> available = service.getAvailableExamIds(userId);
        int completed = service.getCompletedCount(userId);
        long totalExams = service.getTotalActiveExams();
        
        Map<String, Object> response = new HashMap<>();
        response.put("available", available);
        response.put("total_available", available.size());
        response.put("completed_count", completed);
        response.put("total_exams", totalExams);
        response.put("can_take_exam", !available.isEmpty());
        response.put("progress", String.format("%d/%d", completed, totalExams));
        
        log.info("[OriginalExamAPI] Resposta: {} disponíveis, {} completados de {}", 
                 available.size(), completed, totalExams);
        
        return response;
    }

    /**
     * GET /api/simulados/original/select?userId=xxx
     * Seleciona aleatoriamente um simulado disponível
     */
    @GetMapping("/select")
    public Map<String, Object> selectRandom(@RequestParam String userId) {
        log.info("[OriginalExamAPI] GET /select - userId: {}", userId);
        
        String selectedId = service.selectRandomAvailableExam(userId);
        
        Map<String, Object> response = new HashMap<>();
        if (selectedId == null) {
            log.warn("[OriginalExamAPI] Usuário {} já completou todos os simulados!", userId);
            response.put("error", "Você já completou todos os simulados originais!");
            response.put("selected_exam_id", null);
            response.put("completed_all", true);
        } else {
            log.info("[OriginalExamAPI] Simulado selecionado: {}", selectedId);
            response.put("selected_exam_id", selectedId);
            response.put("completed_all", false);
            response.put("message", "Simulado selecionado com sucesso");
        }
        
        return response;
    }

    /**
     * GET /api/simulados/original/{examId}
     * Busca um simulado específico pelo exam_id
     */
    @GetMapping("/{examId}")
    public OriginalExam getExam(@PathVariable String examId) {
        log.info("[OriginalExamAPI] GET /{} - Buscando simulado", examId);
        
        OriginalExam exam = service.getExamByExamId(examId);
        
        if (exam == null) {
            log.error("[OriginalExamAPI] ❌ Simulado {} não encontrado!", examId);
        } else {
            log.info("[OriginalExamAPI] ✅ Simulado {} encontrado com {} questões", 
                     examId, exam.getQuestions().size());
        }
        
        return exam;
    }

    /**
     * GET /api/simulados/original/{examId}/module1
     * Retorna apenas o Módulo 1 de um simulado adaptativo
     */
    @GetMapping("/{examId}/module1")
    public Map<String, Object> getModule1(@PathVariable String examId, 
                                         @RequestParam(required = false) String userId) {
        log.info("[OriginalExamAPI] GET /{}/module1 - userId: {}", examId, userId);
        
        OriginalExam exam = service.getExamByExamId(examId);
        
        Map<String, Object> response = new HashMap<>();
        if (exam == null) {
            log.error("[OriginalExamAPI] ❌ Simulado {} não encontrado!", examId);
            response.put("error", "Simulado não encontrado");
            return response;
        }
        
        // Verificar se usuário pode fazer esse simulado
        if (userId != null && !service.canUserTakeExam(userId, examId)) {
            log.warn("[OriginalExamAPI] ⚠️ Usuário {} já completou simulado {}", userId, examId);
            response.put("error", "Você já completou este simulado");
            response.put("already_completed", true);
            return response;
        }
        
        List<OriginalExam.ExamQuestion> module1Questions = service.getModule1Questions(examId);
        
        if (module1Questions == null) {
            response.put("error", "Módulo 1 não encontrado");
            return response;
        }
        
        response.put("exam_id", exam.getExamId());
        response.put("name", exam.getName());
        response.put("is_adaptive", exam.getIsAdaptive());
        response.put("metadata", exam.getMetadata());
        response.put("questions", module1Questions);
        response.put("module", 1);
        
        log.info("[OriginalExamAPI] ✅ Retornando Módulo 1 com {} questões", module1Questions.size());
        
        return response;
    }

    /**
     * POST /api/simulados/original/{examId}/module2
     * Retorna Módulo 2 (easy ou hard) baseado na performance do Módulo 1
     */
    @PostMapping("/{examId}/module2")
    public Map<String, Object> getModule2(@PathVariable String examId, 
                                         @RequestBody Module2RequestDTO request) {
        log.info("[OriginalExamAPI] POST /{}/module2 - userId: {}, M1 correct: {}", 
                 examId, request.getUserId(), request.getModule1Correct());
        
        // Verificar se usuário pode fazer esse simulado
        if (!service.canUserTakeExam(request.getUserId(), examId)) {
            log.warn("[OriginalExamAPI] ⚠️ Usuário {} já completou simulado {}", 
                     request.getUserId(), examId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Você já completou este simulado");
            error.put("already_completed", true);
            return error;
        }
        
        Map<String, Object> module2Data = service.getModule2Questions(examId, request.getModule1Correct());
        
        if (module2Data == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Módulo 2 não encontrado");
            return error;
        }
        
        // Adicionar informações do exam
        OriginalExam exam = service.getExamByExamId(examId);
        module2Data.put("exam_id", examId);
        module2Data.put("name", exam.getName());
        module2Data.put("module", 2);
        
        log.info("[OriginalExamAPI] ✅ Retornando Módulo 2 tipo: {}", 
                 module2Data.get("module_type"));
        
        return module2Data;
    }

    /**
     * POST /api/simulados/original/start
     * Marca que usuário iniciou um simulado
     */
    @PostMapping("/start")
    public Map<String, Object> startExam(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String examId = body.get("examId");
        
        log.info("[OriginalExamAPI] POST /start - userId: {}, examId: {}", userId, examId);
        
        // Verificar se pode fazer
        if (!service.canUserTakeExam(userId, examId)) {
            log.warn("[OriginalExamAPI] ⚠️ Usuário {} tentou iniciar simulado {} já completado", 
                     userId, examId);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Você já completou este simulado");
            error.put("already_completed", true);
            return error;
        }
        
        service.markExamAsStarted(userId, examId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Simulado iniciado com sucesso");
        response.put("exam_id", examId);
        response.put("started_at", LocalDateTime.now().toString());
        
        log.info("[OriginalExamAPI] ✅ Simulado {} iniciado com sucesso", examId);
        
        return response;
    }

    /**
     * POST /api/simulados/original/complete
     * Marca que usuário completou um simulado
     */
    @PostMapping("/complete")
    public Map<String, Object> completeExam(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        String examId = (String) body.get("examId");
        String attemptId = (String) body.get("attemptId");
        Integer score = body.get("score") != null ? 
                        Integer.parseInt(body.get("score").toString()) : null;
        Integer timeTaken = body.get("timeTakenMinutes") != null ? 
                            Integer.parseInt(body.get("timeTakenMinutes").toString()) : null;
        Integer module1Score = body.get("module1Score") != null ?
                               Integer.parseInt(body.get("module1Score").toString()) : null;
        String module2Type = (String) body.get("module2Type"); // "easy" ou "hard"
        
        log.info("[OriginalExamAPI] POST /complete - userId: {}, examId: {}, score: {}%, tempo: {} min", 
                 userId, examId, score, timeTaken);
        log.info("[OriginalExamAPI] M1 Score: {}, M2 Type: {}", module1Score, module2Type);
        
        service.markExamAsCompleted(userId, examId, attemptId, score, timeTaken, module1Score, module2Type);
        
        int totalCompleted = service.getCompletedCount(userId);
        long totalExams = service.getTotalActiveExams();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Simulado completado com sucesso");
        response.put("completed_count", totalCompleted);
        response.put("total_exams", totalExams);
        response.put("all_completed", totalCompleted >= totalExams);
        
        if (totalCompleted >= totalExams) {
            log.info("[OriginalExamAPI] 🎉 Usuário {} completou TODOS os simulados originais!", userId);
            response.put("achievement", "Parabéns! Você completou todos os simulados originais!");
            response.put("message_completed_all", "🎉 Mais provas em breve! Enquanto isso, que tal fazer um simulado adaptativo para treinar direcionado?");
            response.put("suggestion", "Fazer simulado adaptativo");
        }
        
        log.info("[OriginalExamAPI] ✅ Simulado completado. Progresso: {}/{}", totalCompleted, totalExams);
        
        return response;
    }

    /**
     * GET /api/simulados/original/history?userId=xxx
     * Retorna histórico completo do usuário
     */
    @GetMapping("/history")
    public Map<String, Object> getUserHistory(@RequestParam String userId) {
        log.info("[OriginalExamAPI] GET /history - userId: {}", userId);
        
        UserExamHistory history = service.getUserHistory(userId);
        
        Map<String, Object> response = new HashMap<>();
        if (history == null) {
            response.put("completed_exams", List.of());
            response.put("completed_count", 0);
            response.put("current_exam", null);
        } else {
            response.put("completed_exams", history.getCompletedOriginalExams());
            response.put("completed_count", history.getCompletedOriginalExams().size());
            response.put("current_exam", history.getCurrentOriginalExam());
            response.put("updated_at", history.getUpdatedAt());
        }
        
        return response;
    }

    /**
     * GET /api/simulados/original/stats
     * Estatísticas gerais do sistema
     */
    @GetMapping("/stats")
    public Map<String, Object> getSystemStats() {
        log.info("[OriginalExamAPI] GET /stats - Buscando estatísticas do sistema");
        
        long totalExams = service.getTotalActiveExams();
        long totalUsers = service.getTotalUsersWithHistory();
        
        Map<String, Object> response = new HashMap<>();
        response.put("total_active_exams", totalExams);
        response.put("total_users_with_history", totalUsers);
        
        log.info("[OriginalExamAPI] Stats: {} simulados ativos, {} usuários com histórico", 
                 totalExams, totalUsers);
        
        return response;
    }
}

