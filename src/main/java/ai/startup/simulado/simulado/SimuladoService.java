package ai.startup.simulado.simulado;

import ai.startup.simulado.client.ModeloClient;
import ai.startup.simulado.perfil.PerfilClient;
import ai.startup.simulado.perfil.PerfilCreateDTO;
import ai.startup.simulado.perfil.PerfilTemplateProvider;
import ai.startup.simulado.perfil.TopicDTO;
import ai.startup.simulado.perfil.SubskillDTO;
import ai.startup.simulado.perfil.StructureDTO;
import ai.startup.simulado.questaosimulado.QuestaoClient;
import ai.startup.simulado.questaosimulado.QuestaoUpdateDTO;
import ai.startup.simulado.questaosimulado.QuestoesCreateItemDTO;
import ai.startup.simulado.usuario.UsuarioClient;
import ai.startup.simulado.usuario.UsuarioUpdateDTO;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.ArrayList;

@Slf4j
@Service
public class SimuladoService {

    private final SimuladoRepository repo;
    private final UsuarioClient usuarioClient;
    private final QuestaoClient questaoClient;
    private final ModeloClient modeloClient;
    private final PerfilClient perfilClient;
    private final PerfilTemplateProvider perfilTemplateProvider;
    private final ai.startup.simulado.custompractice.CustomPracticeService customPracticeService;
    private final ai.startup.simulado.originalexam.OriginalExamService originalExamService;

    private transient Map<String, java.time.LocalDateTime> simIdToDateTmp;
    private transient java.util.Set<String> subsUlt1Tmp;
    private transient java.util.Set<String> subsUlt2Tmp;

    public SimuladoService(SimuladoRepository repo,
                           UsuarioClient usuarioClient,
                           QuestaoClient questaoClient,
                           ModeloClient modeloClient,
                           PerfilClient perfilClient,
                           PerfilTemplateProvider perfilTemplateProvider,
                           ai.startup.simulado.custompractice.CustomPracticeService customPracticeService,
                           ai.startup.simulado.originalexam.OriginalExamService originalExamService) {
        this.repo = repo;
        this.usuarioClient = usuarioClient;
        this.questaoClient = questaoClient;
        this.modeloClient = modeloClient;
        this.perfilClient = perfilClient;
        this.perfilTemplateProvider = perfilTemplateProvider;
        this.customPracticeService = customPracticeService;
        this.originalExamService = originalExamService;
    }

    // ================= CRUD =================

    public List<SimuladoDTO> listar() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "data"))
                .stream().map(this::toDTO).toList();
    }

    public SimuladoDTO obter(String id) {
        return repo.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado."));
    }

    public SimuladoDTO criar(SimuladoCreateDTO d) {
        var s = Simulado.builder()
                .idUsuario(d.id_usuario())
                .tipo(d.tipo())
                .data(d.data() == null ? LocalDateTime.now() : d.data())
                .status(d.status() == null ? "ABERTO" : d.status())
                .faturaWins(d.fatura_wins())
                .build();
        return toDTO(repo.save(s));
    }

    public SimuladoDTO atualizar(String id, SimuladoUpdateDTO d) {
        var s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado."));
        if (d.id_usuario()  != null) s.setIdUsuario(d.id_usuario());
        if (d.tipo()        != null) s.setTipo(d.tipo());
        if (d.data()        != null) s.setData(d.data());
        if (d.status()      != null) s.setStatus(d.status());
        if (d.fatura_wins() != null) s.setFaturaWins(d.fatura_wins());
        return toDTO(repo.save(s));
    }

    /** DELETE: também remove as questões do simulado na API de Questões */
    public void deletar(String id, String bearerToken) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado.");
         try {
            var qs = questaoClient.listarPorSimulado(bearerToken, id);
            if (qs != null) {
                for (var q : qs) {
                    Object qid = q.get("id");
                    if (qid != null) questaoClient.deletar(bearerToken, qid.toString());
                }
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao excluir questões do simulado.", e);
        }
        repo.deleteById(id);
    }

    // ================= Início: ADAPTATIVO & ORIGINAL =================

    /** Inicia simulado ADAPTATIVO (1 chamadas que já retorna ~44) */
    public SimuladoComQuestoesDTO iniciarAdaptativo(HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");
        var user = usuarioClient.me(bearer);       // uma chamada só
        String userId = user.id();

        if (user.wins() == null || user.wins() < 5) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Saldo insuficiente de wins (mínimo 5).");
        }

        // Verificar se há algum simulado em aberto (adaptativo, original ou custom practice)
        List<Simulado> abertos = repo.findByIdUsuarioAndStatus(userId, "ABERTO");
        if (!abertos.isEmpty()) {
            Simulado aberto = abertos.get(0);
            String tipoSimulado = aberto.getTipo();
            String tipoFormatado = tipoSimulado.equals("ADAPTATIVO") ? "adaptativo" 
                                  : tipoSimulado.equals("ORIGINAL") ? "original" 
                                  : tipoSimulado.equals("CUSTOM_PRACTICE") ? "custom practice" 
                                  : "simulado";
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, 
                String.format("Você já tem um %s em aberto. Finalize-o antes de começar outro practice.", tipoFormatado)
            );
        }

        long novoSaldo = Math.max(0L, user.wins() - 5L);
        UsuarioUpdateDTO debitoWins = new UsuarioUpdateDTO(
                null, null, null, null, null, null, // nome, sobrenome, telefone, nascimento, email, cpf
                null,                                // senha
                novoSaldo,                           // wins (apenas este campo será aplicado)
                null,                                // streaks
                null,                                // xp
                null                                 // permissao
        );
        usuarioClient.atualizar(bearer, userId, debitoWins);
        
        var sim = repo.save(Simulado.builder()
                .idUsuario(userId)
                .tipo("ADAPTATIVO")
                .data(LocalDateTime.now())
                .status("ABERTO")
                .faturaWins(5)
                .build());

        Map<String,Object> modulo;
        try {
            modulo = modeloClient.gerarModuloAdaptativo(userId);
        } catch (RuntimeException e) {
            // Re-lança com a mensagem detalhada do ModeloClient
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Erro inesperado ao gerar módulo adaptativo para userId {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, 
                "Falha ao gerar módulos adaptativos: " + e.getMessage(), e);
        }

        var todas  = mapModeloParaQuestoes(sim.getId(), userId, modulo, 1);

        List<Map<String,Object>> qsCriadas;
        try {
            qsCriadas = questaoClient.criarQuestoes(bearer, todas);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao criar questões.", e);
        }

        SimuladoDTO simuladoDTO = toDTO(sim);
        return new SimuladoComQuestoesDTO(simuladoDTO, qsCriadas);
    }

    /** Inicia simulado ORIGINAL (busca do banco de simulados fixos) */
    public SimuladoComQuestoesDTO iniciarOriginal(HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");
        var user = usuarioClient.me(bearer);       // uma chamada só
        String userId = user.id();

        if (user.wins() == null || user.wins() < 5) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Saldo insuficiente de wins (mínimo 5).");
        }

        // Verificar se há algum simulado em aberto (adaptativo, original ou custom practice)
        List<Simulado> abertos = repo.findByIdUsuarioAndStatus(userId, "ABERTO");
        if (!abertos.isEmpty()) {
            Simulado aberto = abertos.get(0);
            String tipoSimulado = aberto.getTipo();
            String tipoFormatado = tipoSimulado.equals("ADAPTATIVO") ? "adaptativo" 
                                  : tipoSimulado.equals("ORIGINAL") ? "original" 
                                  : tipoSimulado.equals("CUSTOM_PRACTICE") ? "custom practice" 
                                  : "simulado";
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, 
                String.format("Você já tem um %s em aberto. Finalize-o antes de começar outro practice.", tipoFormatado)
            );
        }

        long novoSaldo = Math.max(0L, user.wins() - 5L);
        UsuarioUpdateDTO debitoWins = new UsuarioUpdateDTO(
                null, null, null, null, null, null, // nome, sobrenome, telefone, nascimento, email, cpf
                null,                                // senha
                novoSaldo,                           // wins (apenas este campo será aplicado)
                null,                                // streaks
                null,                                // xp
                null                                 // permissao
        );
        usuarioClient.atualizar(bearer, userId, debitoWins);

        var sim = repo.save(Simulado.builder()
                .idUsuario(userId)
                .tipo("ORIGINAL")
                .data(LocalDateTime.now())
                .status("ABERTO")
                .faturaWins(5)
                .build());

        // Buscar próximo simulado original não feito pelo usuário
        Map<String, Object> nextExamData;
        try {
            nextExamData = originalExamService.getNextExamForUser(userId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao buscar simulado original.", e);
        }

        // Mapear as questões do Módulo 1 para o formato esperado
        var lista = mapOriginalExamModule1(sim.getId(), userId, nextExamData);
        
        log.info("[OriginalExam] 🔍 DEBUG - Questões mapeadas do M1: {}", lista.size());
        log.info("[OriginalExam] 🔍 DEBUG - nextExamData.module_1 size: {}", 
                 nextExamData.get("module_1") instanceof List ? ((List<?>)nextExamData.get("module_1")).size() : "não é lista");

        List<Map<String,Object>> qsCriadas;
        try {
            qsCriadas = questaoClient.criarQuestoes(bearer, lista);
            log.info("[OriginalExam] 🔍 DEBUG - Questões CRIADAS retornadas: {}", qsCriadas.size());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao criar questões.", e);
        }

        // Criar metadados para o frontend saber que é original adaptativo
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exam_id", nextExamData.get("exam_id"));
        metadata.put("is_adaptive", nextExamData.get("is_adaptive"));
        metadata.put("threshold", nextExamData.get("metadata") instanceof Map 
            ? ((Map<?,?>)nextExamData.get("metadata")).get("threshold") 
            : 16);
        metadata.put("module1_questions", lista.size());
        
        SimuladoDTO simuladoDTO = toDTO(sim);
        return new SimuladoComQuestoesDTO(simuladoDTO, qsCriadas, metadata);
    }

    /**
     * Inicia um Custom Practice delegando para o CustomPracticeService.
     * 
     * @param request DTO com usuarioId, selections e totalQuestions
     * @param authorizationHeader JWT token
     * @return SimuladoComQuestoesDTO com simulado + questões personalizadas
     */
    public SimuladoComQuestoesDTO iniciarCustomPractice(
            ai.startup.simulado.custompractice.CustomPracticeRequestDTO request,
            String authorizationHeader
    ) {
        // Delega para o serviço especializado que já retorna o tipo correto
        return customPracticeService.criarCustomPractice(request, authorizationHeader);
    }

    /**
     * Carrega Módulo 2 de um simulado original adaptativo baseado na performance do M1
     * 
     * @param simuladoId ID do simulado no banco
     * @param examId ID do exam original (SAT_ORIGINAL_001, etc)
     * @param module1Correct Número de acertos no Módulo 1
     * @param bearer JWT token
     * @return Map com questões do M2 criadas e metadata
     */
    public Map<String, Object> carregarModule2Original(String simuladoId, String examId, 
                                                       Integer module1Correct, String bearer) {
        // Buscar dados do Módulo 2 (easy ou hard)
        Map<String, Object> module2Data = originalExamService.getModule2Questions(examId, module1Correct);
        
        if (module2Data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Módulo 2 não encontrado para este exam.");
        }
        
        // Buscar simulado para pegar userId
        var sim = repo.findById(simuladoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado."));
        
        // Converter questões do M2 para criar no banco
        var lista = mapOriginalExamModule2(simuladoId, sim.getIdUsuario(), module2Data);
        
        List<Map<String,Object>> qsCriadas;
        try {
            qsCriadas = questaoClient.criarQuestoes(bearer, lista);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao criar questões do Módulo 2.", e);
        }
        
        // Retornar questões criadas + metadata
        Map<String, Object> response = new HashMap<>();
        response.put("questions", qsCriadas);
        response.put("module_type", module2Data.get("module_type"));
        response.put("threshold_used", module2Data.get("threshold_used"));
        response.put("module1_correct", module1Correct);
        
        return response;
    }

    // ================= Finalização: calcula Perfil (novo formato) e encerra =================
    public SimuladoDTO finalizarAtualizandoTudo(FinalizarSimuladoRequestFlat body, HttpServletRequest req) {
        final String bearer = req.getHeader("Authorization");

        // 0) validação básica
        if (body == null || body.id_simulado() == null || body.id_usuario() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payload inválido: id_simulado e id_usuario são obrigatórios.");
        }
        var sim = repo.findById(body.id_simulado())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado."));

        if (!sim.getIdUsuario().equals(body.id_usuario())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "id_usuario do payload não corresponde ao dono do simulado.");
        }
        if (!"ABERTO".equalsIgnoreCase(sim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Simulado já finalizado.");
        }
        if (body.questoes() == null || body.questoes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de questões está vazia.");
        }

        // 1) ATUALIZAR TODAS AS QUESTÕES (update-only)
        // OTIMIZAÇÃO: Valida todas primeiro, depois atualiza em lote
        List<Map<String,Object>> questoesParaBulk = new ArrayList<>();
        
        for (var q : body.questoes()) {
            if (q.id() == null || q.id().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Questão sem id: a rota apenas atualiza questões existentes.");
            }
            if (q.id_formulario() != null && !body.id_simulado().equals(q.id_formulario())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "id_formulario inconsistente em questão do payload.");
            }
            if (q.id_usuario() != null && !body.id_usuario().equals(q.id_usuario())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "id_usuario inconsistente em questão do payload.");
            }

            // Prepara para atualização em lote (apenas campos que mudam)
            Map<String,Object> questaoUpdate = new HashMap<>();
            questaoUpdate.put("id", q.id());
            if (q.alternativa_marcada() != null) {
                questaoUpdate.put("alternativa_marcada", q.alternativa_marcada());
            }
            if (q.dica() != null) {
                questaoUpdate.put("dica", q.dica());
            }
            if (q.solucao() != null) {
                questaoUpdate.put("solucao", q.solucao());
            }
            questoesParaBulk.add(questaoUpdate);
        }
        
        // Atualiza todas de uma vez usando endpoint de lote (muito mais rápido)
        try {
            questaoClient.atualizarEmLote(bearer, questoesParaBulk);
            log.info("[SimuladoService] Atualizadas {} questões em lote", questoesParaBulk.size());
        } catch (Exception e) {
            // Fallback: se endpoint de lote não existir, usa método antigo
            log.warn("[SimuladoService] Endpoint de lote não disponível, usando atualização individual");
            for (var q : body.questoes()) {
                var upd = new QuestaoUpdateDTO(
                        body.id_simulado(), body.id_usuario(),
                        q.topic(), q.subskill(), q.difficulty(),
                        q.question(), q.options(), q.correct_option(),
                        q.solution(), q.structure(), q.format(), q.representation(),
                        q.hint(), q.target_mistakes(), q.source(),
                        q.solution_english(), q.solution_portugues(),
                        q.hint_english(), q.hint_portugues(),
                        q.figure(), q.alternativa_marcada(), q.dica(), q.solucao(), q.modulo()
                );
                questaoClient.atualizar(bearer, q.id(), upd);
            }
        }

        // 2) ATUALIZAR O SIMULADO (status FINALIZADO + demais campos do body que você autoriza atualizar)
        if (body.tipo() != null)        sim.setTipo(body.tipo());
        if (body.data() != null)        sim.setData(body.data());
        if (body.fatura_wins() != null) sim.setFaturaWins(body.fatura_wins());
        // status no body é ignorado; a regra do endpoint é finalizar:
        sim.setStatus("FINALIZADO");
        repo.save(sim);

        // 3) RECALCULAR PERFIL a partir de TODO o histórico do usuário
        // OTIMIZAÇÃO: Limita a últimos 500 questões para evitar processar milhares
        // Se necessário, pode ser processado de forma assíncrona
        var todasQuestoesUsuario = questaoClient.listarPorUsuario(bearer, sim.getIdUsuario());
        // Garante que não seja null e limita processamento para performance (últimas 500 questões)
        if (todasQuestoesUsuario == null) {
            todasQuestoesUsuario = new ArrayList<>();
        }
        int totalQuestoes = todasQuestoesUsuario.size();
        if (totalQuestoes > 500) {
            todasQuestoesUsuario = new ArrayList<>(todasQuestoesUsuario.subList(0, 500));
            log.warn("[SimuladoService] Limitei processamento a 500 questões para performance. Total disponível: {}", totalQuestoes);
        }

        // 3.1) Carregar todos os simulados do usuário (datas e últimos finalizados)
        // OTIMIZAÇÃO: Limita a últimos 50 simulados para performance
        var simuladosUsuario = repo.findByIdUsuario(sim.getIdUsuario(),
                Sort.by(Sort.Direction.DESC, "data"))
                .stream()
                .limit(50) // Limite para performance
                .toList();

        Map<String, LocalDateTime> simIdToDate = new HashMap<>();
        for (var sx : simuladosUsuario) {
            if (sx.getData() != null) simIdToDate.put(sx.getId(), sx.getData());
        }
        List<Simulado> ult2Finalizados = simuladosUsuario.stream()
                .filter(sx -> "FINALIZADO".equalsIgnoreCase(sx.getStatus()))
                .limit(2)
                .toList();

        Set<String> subsUlt1 = new HashSet<>();
        Set<String> subsUlt2 = new HashSet<>();
        if (ult2Finalizados.size() >= 1) {
            String ult1Id = ult2Finalizados.get(0).getId();
            for (var q : todasQuestoesUsuario) {
                if (ult1Id.equals(String.valueOf(q.get("id_formulario")))) {
                    String sub = str(q.get("subskill"));
                    if (sub != null) subsUlt1.add(sub);
                }
            }
        }
        if (ult2Finalizados.size() >= 2) {
            String ult2Id = ult2Finalizados.get(1).getId();
            for (var q : todasQuestoesUsuario) {
                if (ult2Id.equals(String.valueOf(q.get("id_formulario")))) {
                    String sub = str(q.get("subskill"));
                    if (sub != null) subsUlt2.add(sub);
                }
            }
        }

        // 3.2) Carregar template COMPLETO
        Map<String, TopicDTO> template = perfilTemplateProvider.getTopicsTemplate(sim.getIdUsuario());

        // 3.3) Clonar e zerar (mantendo catálogo)
        Map<String, TopicDTO> topicsAgregado = deepCloneAndZero(template);

        // 3.4) Agregar com timestamps e níveis
        // (armazeno em campos temporários para usar dentro dos helpers)
        this.simIdToDateTmp = simIdToDate;
        this.subsUlt1Tmp = subsUlt1;
        this.subsUlt2Tmp = subsUlt2;

        agregarQuestoesNoPerfil(topicsAgregado, todasQuestoesUsuario);

        // 3.5) Derivar last_seen_at_s e missed_two_sessions
        fecharSubskills(topicsAgregado);

        // limpando auxiliares
        this.simIdToDateTmp = null;
        this.subsUlt1Tmp = null;
        this.subsUlt2Tmp = null;

        // 3.6) Atualiza Perfil na API
        var perfilPayload = new PerfilCreateDTO(sim.getIdUsuario(), topicsAgregado);
        perfilClient.atualizarPerfilPorUsuario(bearer, sim.getIdUsuario(), perfilPayload);

        return toDTO(sim);
    }

        // ================= Listagens por usuário =================

        public List<SimuladoDTO> listarPorUsuario(String idUsuario) {
            // OTIMIZAÇÃO: Limita a últimos 100 simulados para evitar retornar milhares
            // Frontend pode paginar se necessário
            return repo.findByIdUsuario(idUsuario, Sort.by(Sort.Direction.DESC, "data"))
                    .stream()
                    .limit(100) // Limite para performance
                    .map(this::toDTO)
                    .toList();
        }

        public SimuladoDTO ultimoPorUsuario(String idUsuario) {
            // OTIMIZAÇÃO: Busca direto por status ABERTO ao invés de buscar todos
            var aberto = repo.findByIdUsuarioAndStatus(idUsuario, "ABERTO")
                    .stream()
                    .max(Comparator.comparing(Simulado::getData, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            
            if (aberto == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum simulado em aberto encontrado para o usuário.");
            }
            return toDTO(aberto);
        }

        /**
         * OTIMIZAÇÃO CRÍTICA: Retorna estatísticas do usuário sem buscar todas as questões
         * Calcula bestScore apenas dos últimos 5 simulados para performance máxima
         */
        public UserStatsDTO getUserStats(String idUsuario, String bearerToken) {
            // OTIMIZAÇÃO: Busca apenas simulados finalizados com limite (evita carregar todos)
            var todosSimulados = repo.findByIdUsuario(idUsuario, Sort.by(Sort.Direction.DESC, "data"))
                    .stream()
                    .limit(100) // Limite já aplicado no listarPorUsuario, mas garante aqui também
                    .toList();
            
            var finalizados = todosSimulados.stream()
                    .filter(s -> "FINALIZADO".equalsIgnoreCase(s.getStatus()))
                    .toList();
            
            int total = finalizados.size();
            
            // OTIMIZAÇÃO AGRESSIVA: Limita a apenas últimos 5 simulados para calcular bestScore
            // Isso reduz de 10 chamadas para 5, melhorando performance significativamente
            var finalizadosLimitados = finalizados.stream()
                    .limit(5)
                    .toList();
            
            if (finalizadosLimitados.isEmpty()) {
                return new UserStatsDTO(null, total);
            }
            
            // Busca questões em paralelo (apenas dos últimos 5)
            int bestScore = 0;
            try {
                // Usa stream paralelo para buscar questões simultaneamente
                var scores = finalizadosLimitados.parallelStream()
                        .map(sim -> {
                            try {
                                var questoes = questaoClient.listarPorSimulado(bearerToken, sim.getId());
                                if (questoes != null && !questoes.isEmpty()) {
                                    int correct = 0;
                                    for (var q : questoes) {
                                        var marcada = q.get("alternativa_marcada");
                                        var correta = q.get("correct_option");
                                        if (marcada != null && correta != null &&
                                            String.valueOf(marcada).toUpperCase().trim()
                                                .equals(String.valueOf(correta).toUpperCase().trim())) {
                                            correct++;
                                        }
                                    }
                                    return questoes.size() > 0 ? Math.round((correct * 100) / questoes.size()) : 0;
                                }
                                return 0;
                            } catch (Exception e) {
                                log.warn("[SimuladoService] Erro ao calcular score do simulado {}: {}", sim.getId(), e.getMessage());
                                return 0;
                            }
                        })
                        .filter(score -> score > 0)
                        .toList();
                
                if (!scores.isEmpty()) {
                    bestScore = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
                }
            } catch (Exception e) {
                log.error("[SimuladoService] Erro ao calcular estatísticas do usuário: {}", e.getMessage());
            }
            
            return new UserStatsDTO(bestScore > 0 ? bestScore : null, total);
        }

    // ================= Helpers =================

    @SuppressWarnings("unchecked")
    private List<QuestoesCreateItemDTO> mapModeloParaQuestoes(String idSimulado, String userId,
                                                              Map<String,Object> moduloResp, int modulo) {
        Object raw = moduloResp == null ? null : moduloResp.get("questions");
        List<Map<String,Object>> arr = (raw instanceof List<?> list)
                ? (List<Map<String,Object>>) list
                : List.of();

        List<QuestoesCreateItemDTO> out = new ArrayList<>();
        for (var q : arr) {
            Map<String,String> options = (Map<String,String>) q.get("options"); // pode ser {}
            Object correct             = q.get("correct_option");               // "A"/"B"/... ou -1
            List<String> solOld        = (List<String>) q.get("solution");      // legado

            List<String> solEn         = (List<String>) q.get("solution_english");
            List<String> solPt         = (List<String>) q.get("solution_portugues");

            String hintOld             = str(q.get("hint"));          // legado
            String hintEn              = str(q.get("hint_english"));
            String hintPt              = str(q.get("hint_portugues"));

            Map<String,Object> figure  = (Map<String,Object>) q.get("figure");

            out.add(new QuestoesCreateItemDTO(
                    idSimulado,
                    userId,
                    str(q.get("topic")),
                    str(q.get("subskill")),
                    str(q.get("difficulty")),
                    str(q.get("question")),
                    options,
                    correct,
                    solOld,
                    str(q.get("structure")),
                    str(q.get("format")),
                    str(q.get("representation")),
                    hintOld,
                    (List<String>) q.get("target_mistakes"),
                    str(q.getOrDefault("source","ai_generated")),

                    // novos campos
                    solEn,
                    solPt,
                    hintEn,
                    hintPt,
                    figure,

                    // app
                    null,      // alternativa_marcada
                    false,     // dica
                    false,     // solucao
                    modulo
            ));
        }
        return out;
    }

    /**
     * Mapeia as questões do Módulo 1 de um Original Exam para QuestoesCreateItemDTO
     */
    @SuppressWarnings("unchecked")
    private List<QuestoesCreateItemDTO> mapOriginalExamModule1(String idSimulado, String userId,
                                                                Map<String, Object> examData) {
        Object raw = examData == null ? null : examData.get("module_1");
        
        // O Spring retorna List<OriginalExam.ExamQuestion>, não List<Map>
        if (!(raw instanceof List<?>)) {
            return List.of();
        }
        
        List<?> rawList = (List<?>) raw;
        List<QuestoesCreateItemDTO> out = new ArrayList<>();
        
        for (Object item : rawList) {
            // Se for ExamQuestion (objeto tipado do Spring)
            if (item instanceof ai.startup.simulado.originalexam.OriginalExam.ExamQuestion examQ) {
                out.add(new QuestoesCreateItemDTO(
                        idSimulado,
                        userId,
                        examQ.getTopic(),
                        examQ.getSubskill(),
                        examQ.getDifficulty(),
                        examQ.getQuestion(),
                        examQ.getOptions(),               // Map<String, String>
                        examQ.getCorrectOption(),         // String
                        examQ.getSolution(),              // List<String> - legado
                        examQ.getStructure(),
                        examQ.getFormat(),
                        examQ.getRepresentation(),
                        examQ.getHint(),                  // legado
                        null,                             // target_mistakes
                        "sat_original",                   // source
                        
                        // novos campos
                        examQ.getSolution(),              // solution_english (reusa solution)
                        null,                             // solution_portugues
                        examQ.getHint(),                  // hint_english (reusa hint)
                        null,                             // hint_portugues
                        null,                             // figure
                        
                        // app
                        null,                             // alternativa_marcada
                        false,                            // dica
                        false,                            // solucao
                        1                                 // modulo 1
                ));
            }
            // Se for Map (compatibilidade com estrutura antiga)
            else if (item instanceof Map<?,?> q) {
                Map<String,Object> qMap = (Map<String,Object>) q;
                Map<String,String> options = (Map<String,String>) qMap.get("options");
                Object correct = qMap.get("correct_option");
                List<String> solution = (List<String>) qMap.get("solution");
                String hint = str(qMap.get("hint"));
                
                out.add(new QuestoesCreateItemDTO(
                        idSimulado,
                        userId,
                        str(qMap.get("topic")),
                        str(qMap.get("subskill")),
                        str(qMap.get("difficulty")),
                        str(qMap.get("question")),
                        options,
                        correct,
                        solution,
                        str(qMap.get("structure")),
                        str(qMap.get("format")),
                        str(qMap.get("representation")),
                        hint,
                        null,
                        "sat_original",
                        solution,
                        null,
                        hint,
                        null,
                        null,
                        null,
                        false,
                        false,
                        1
                ));
            }
        }
        return out;
    }

    /**
     * Mapeia as questões do Módulo 2 de um Original Exam para QuestoesCreateItemDTO
     */
    @SuppressWarnings("unchecked")
    private List<QuestoesCreateItemDTO> mapOriginalExamModule2(String idSimulado, String userId,
                                                                Map<String, Object> module2Data) {
        Object raw = module2Data == null ? null : module2Data.get("questions");
        
        // O Spring retorna List<OriginalExam.ExamQuestion>, não List<Map>
        if (!(raw instanceof List<?>)) {
            return List.of();
        }
        
        List<?> rawList = (List<?>) raw;
        List<QuestoesCreateItemDTO> out = new ArrayList<>();
        
        for (Object item : rawList) {
            // Se for ExamQuestion (objeto tipado do Spring)
            if (item instanceof ai.startup.simulado.originalexam.OriginalExam.ExamQuestion examQ) {
                out.add(new QuestoesCreateItemDTO(
                        idSimulado,
                        userId,
                        examQ.getTopic(),
                        examQ.getSubskill(),
                        examQ.getDifficulty(),
                        examQ.getQuestion(),
                        examQ.getOptions(),
                        examQ.getCorrectOption(),
                        examQ.getSolution(),
                        examQ.getStructure(),
                        examQ.getFormat(),
                        examQ.getRepresentation(),
                        examQ.getHint(),
                        null,
                        "sat_original",
                        examQ.getSolution(),
                        null,
                        examQ.getHint(),
                        null,
                        null,
                        null,
                        false,
                        false,
                        2  // modulo 2
                ));
            }
        }
        return out;
    }

    /**
     * Clona o template e zera contadores/rates/flags.
     * Mantém total_estruturas_s a partir do template (NUNCA remover nada).
     */
    private Map<String, TopicDTO> deepCloneAndZero(Map<String, TopicDTO> template) {
        Map<String, TopicDTO> out = new HashMap<>();
        if (template == null) return out;

        template.forEach((topicName, topicDTO) -> {
            Map<String, SubskillDTO> newSubs = new HashMap<>();
            if (topicDTO.subskills() != null) {
                topicDTO.subskills().forEach((subName, subDTO) -> {
                    Map<String, StructureDTO> newStructs = new HashMap<>();
                    int totalStructs = 0;
                    if (subDTO.structures() != null) {
                        for (var e : subDTO.structures().entrySet()) {
                            totalStructs++;
                            newStructs.put(e.getKey(), new StructureDTO(
                                    50, 0L, 0L, 0.0, 0.0,
                                    false, false, false,
                                    0L, 0L,
                                    "easy", 0, null // last_seen_at_sc = null
                            ));
                        }
                    }
                    newSubs.put(subName, new SubskillDTO(
                            0L, 0L, 0.0, 0.0, null,   // last_seen_at_s = null
                            null,                     // missed_two_sessions (derivaremos depois)
                            false, false, false,
                            0L, (long) totalStructs,
                            newStructs
                    ));
                });
            }
            out.put(topicName, new TopicDTO(newSubs));
        });
        return out;
    }

    /**
     * Soma/agg das métricas nas structures e subskills.
     * Entrada: lista “todasQuestoesUsuario” como Maps (da API de Questões).
     */
    private void agregarQuestoesNoPerfil(Map<String, TopicDTO> profile, List<Map<String,Object>> qs) {
        if (qs == null || qs.isEmpty()) return;

        for (var q : qs) {
            String topic = str(q.get("topic"));
            String sub   = str(q.get("subskill"));
            String st    = str(q.get("structure"));
            if (topic == null || sub == null || st == null) continue;

            var t = profile.get(topic);
            if (t == null) continue;
            var s = t.subskills().get(sub);
            if (s == null) continue;
            var mapStructs = s.structures();
            if (mapStructs == null) continue;
            var stDTO = mapStructs.get(st);
            if (stDTO == null) continue;

            // ===== contadores básicos =====
            long attempts_prev = stDTO.attempts_sc() == null ? 0L : stDTO.attempts_sc();
            long attempts_sc   = attempts_prev + 1L;

            Object marcada = q.get("alternativa_marcada");
            Object correta = q.get("correct_option");
            boolean acertou = (marcada != null && correta != null &&
                    marcada.toString().equalsIgnoreCase(correta.toString()));
            long correct_sc = (stDTO.correct_sc() == null ? 0L : stDTO.correct_sc()) + (acertou ? 1L : 0L);

            boolean usouDica = Boolean.TRUE.equals(q.get("dica"));
            boolean abriuSol = Boolean.TRUE.equals(q.get("solucao"));

            long hintsCountPrev     = Math.round((stDTO.hints_rate_sc()     == null ? 0.0 : stDTO.hints_rate_sc())     * attempts_prev);
            long solutionsCountPrev = Math.round((stDTO.solutions_rate_sc() == null ? 0.0 : stDTO.solutions_rate_sc()) * attempts_prev);
            long hintsCount     = hintsCountPrev     + (usouDica ? 1 : 0);
            long solutionsCount = solutionsCountPrev + (abriuSol ? 1 : 0);

            String diff = str(q.get("difficulty"));
            boolean easy   = "easy".equalsIgnoreCase(diff);
            boolean medium = "medium".equalsIgnoreCase(diff);
            boolean hard   = "hard".equalsIgnoreCase(diff);

            long mediumExp = (stDTO.medium_exposures_sc() == null ? 0L : stDTO.medium_exposures_sc()) + (medium ? 1L : 0L);
            long hardExp   = (stDTO.hard_exposures_sc()   == null ? 0L : stDTO.hard_exposures_sc())   + (hard   ? 1L : 0L);

            double hintsRate     = attempts_sc == 0 ? 0.0 : (hintsCount * 1.0 / attempts_sc);
            double solutionsRate = attempts_sc == 0 ? 0.0 : (solutionsCount * 1.0 / attempts_sc);

            // ===== last_seen_at_sc a partir da data do simulado =====
            String simId = str(q.get("id_formulario"));
            LocalDateTime seenAt = (this.simIdToDateTmp == null) ? null : this.simIdToDateTmp.get(simId);
            String prevSeen = stDTO.last_seen_at_sc();
            String newSeen  = prevSeen;
            if (seenAt != null) {
                if (prevSeen == null) newSeen = seenAt.toString();
                else {
                    LocalDateTime prev = LocalDateTime.parse(prevSeen);
                    if (seenAt.isAfter(prev)) newSeen = seenAt.toString();
                }
            }

            // ===== nível aplicado (maior dificuldade vista)
            String prevLevel = stDTO.last_level_applied_sc() == null ? "easy" : stDTO.last_level_applied_sc();
            String newLevel  = promoteLevel(prevLevel, diff);

            // ===== P_sc heurístico
            int p = stDTO.P_sc() == null ? 50 : stDTO.P_sc();
            double acc = attempts_sc == 0 ? 0.0 : (correct_sc * 1.0 / attempts_sc);
            if (acc >= 0.85) p += 3;
            if (hardExp >= 2 && acc >= 0.7) p += 2;
            if (hintsRate >= 0.5) p -= 4;
            if (solutionsRate >= 0.3) p -= 6;
            p = Math.max(0, Math.min(100, p));

            // ===== cooldown por “tempo desde última exposição”
            int cooldown = 0;
            if (newSeen != null) {
                LocalDateTime last = LocalDateTime.parse(newSeen);
                long days = Duration.between(last, LocalDateTime.now()).toDays();
                cooldown = (int) Math.max(0, 2 - days);
            }

            // aplica
            mapStructs.put(st, new StructureDTO(
                    p,
                    attempts_sc,
                    correct_sc,
                    hintsRate,
                    solutionsRate,
                    (stDTO.easy_seen_sc()   != null && stDTO.easy_seen_sc())   || easy,
                    (stDTO.medium_seen_sc() != null && stDTO.medium_seen_sc()) || medium,
                    (stDTO.hard_seen_sc()   != null && stDTO.hard_seen_sc())   || hard,
                    mediumExp,
                    hardExp,
                    newLevel,
                    cooldown,
                    newSeen
            ));
        }
    }

    private void fecharSubskills(Map<String, TopicDTO> profile) {
        for (var tEntry : profile.entrySet()) {
            var subs = tEntry.getValue().subskills();
            if (subs == null) continue;

            for (var sEntry : subs.entrySet()) {
                var sName = sEntry.getKey();
                var sDTO  = sEntry.getValue();
                var structs = sDTO.structures();
                if (structs == null || structs.isEmpty()) continue;

                long attempts_s = 0, correct_s = 0, hintsUsed = 0, solsUsed = 0;
                boolean easy_s=false, med_s=false, hard_s=false;
                long vistas = 0;
                long total  = sDTO.total_estruturas_s() == null ? structs.size() : sDTO.total_estruturas_s();

                String lastSeenS = null;

                for (var stDTO : structs.values()) {
                    long a = stDTO.attempts_sc() == null ? 0L : stDTO.attempts_sc();
                    attempts_s += a;
                    correct_s  += (stDTO.correct_sc() == null ? 0L : stDTO.correct_sc());

                    if (a > 0) {
                        vistas += 1;
                        hintsUsed += Math.round((stDTO.hints_rate_sc() == null ? 0.0 : stDTO.hints_rate_sc()) * a);
                        solsUsed  += Math.round((stDTO.solutions_rate_sc() == null ? 0.0 : stDTO.solutions_rate_sc()) * a);
                    }

                    easy_s |= (stDTO.easy_seen_sc()   != null && stDTO.easy_seen_sc());
                    med_s  |= (stDTO.medium_seen_sc() != null && stDTO.medium_seen_sc());
                    hard_s |= (stDTO.hard_seen_sc()   != null && stDTO.hard_seen_sc());

                    // last_seen_at_s = max(last_seen_at_sc)
                    String stSeen = stDTO.last_seen_at_sc();
                    if (stSeen != null) {
                        if (lastSeenS == null) lastSeenS = stSeen;
                        else {
                            LocalDateTime aSeen = LocalDateTime.parse(lastSeenS);
                            LocalDateTime bSeen = LocalDateTime.parse(stSeen);
                            if (bSeen.isAfter(aSeen)) lastSeenS = stSeen;
                        }
                    }
                }

                double hr_s = attempts_s == 0 ? 0.0 : (hintsUsed * 1.0 / attempts_s);
                double sr_s = attempts_s == 0 ? 0.0 : (solsUsed  * 1.0 / attempts_s);

                // missed_two_sessions com base nos dois últimos finalizados
                Boolean missedTwo = null;
                if (this.subsUlt1Tmp != null) {
                    boolean inUlt1 = this.subsUlt1Tmp.contains(sName);
                    if (this.subsUlt2Tmp != null && !this.subsUlt2Tmp.isEmpty()) {
                        boolean inUlt2 = this.subsUlt2Tmp.contains(sName);
                        missedTwo = (!inUlt1 && !inUlt2);
                    } else {
                        // só 1 simulado finalizado disponível
                        missedTwo = (!inUlt1);
                    }
                }

                subs.put(sName, new SubskillDTO(
                        attempts_s, correct_s, hr_s, sr_s, lastSeenS,
                        missedTwo,
                        easy_s, med_s, hard_s,
                        vistas, total,
                        structs
                ));
            }
        }
    }

    private String promoteLevel(String prev, String seenNow) {
        int rp = rank(prev);
        int rn = rank(seenNow);
        return rn > rp ? norm(seenNow) : norm(prev);
    }
    private int rank(String lvl) {
        String v = norm(lvl);
        return switch (v) {
            case "hard" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }
    private String norm(String lvl) {
        if (lvl == null) return "easy";
        String v = lvl.trim().toLowerCase();
        if ("hard".equals(v) || "medium".equals(v)) return v;
        return "easy";
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    private SimuladoDTO toDTO(Simulado s) {
        return new SimuladoDTO(
                s.getId(), s.getIdUsuario(), s.getTipo(), s.getData(), s.getStatus(), s.getFaturaWins()
        );
    }
}
