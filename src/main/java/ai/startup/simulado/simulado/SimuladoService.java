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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.time.Duration;

@Service
public class SimuladoService {

    private final SimuladoRepository repo;
    private final UsuarioClient usuarioClient;
    private final QuestaoClient questaoClient;
    private final ModeloClient modeloClient;
    private final PerfilClient perfilClient;
    private final PerfilTemplateProvider perfilTemplateProvider;

    private transient Map<String, java.time.LocalDateTime> simIdToDateTmp;
    private transient java.util.Set<String> subsUlt1Tmp;
    private transient java.util.Set<String> subsUlt2Tmp;

    public SimuladoService(SimuladoRepository repo,
                           UsuarioClient usuarioClient,
                           QuestaoClient questaoClient,
                           ModeloClient modeloClient,
                           PerfilClient perfilClient,
                           PerfilTemplateProvider perfilTemplateProvider) {
        this.repo = repo;
        this.usuarioClient = usuarioClient;
        this.questaoClient = questaoClient;
        this.modeloClient = modeloClient;
        this.perfilClient = perfilClient;
        this.perfilTemplateProvider = perfilTemplateProvider;
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

        var ultimo = repo.findMaisRecente(userId);
        if (ultimo != null && "ABERTO".equalsIgnoreCase(ultimo.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há um simulado em aberto. Finalize-o antes de criar outro.");

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
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao gerar módulos adaptativos.", e);
        }

        var todas  = mapModeloParaQuestoes(sim.getId(), userId, modulo, 1);

        List<Map<String,Object>> qsCriadas;
        try {
            qsCriadas = questaoClient.criarQuestoes(bearer, todas);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao criar questões.", e);
        }

        return new SimuladoComQuestoesDTO(
                sim.getId(), sim.getIdUsuario(), sim.getTipo(), sim.getData(), sim.getStatus(), sim.getFaturaWins(),
                qsCriadas
        );
    }

    /** Inicia simulado ORIGINAL (1 chamada que já retorna ~44) */
    public SimuladoComQuestoesDTO iniciarOriginal(HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");
        var user = usuarioClient.me(bearer);       // uma chamada só
        String userId = user.id();

        if (user.wins() == null || user.wins() < 5) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Saldo insuficiente de wins (mínimo 5).");
        }

        var ultimo = repo.findMaisRecente(userId);
        if (ultimo != null && "ABERTO".equalsIgnoreCase(ultimo.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Há um simulado em aberto. Finalize-o antes de criar outro.");

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

        Map<String,Object> modulo;
        try {
            modulo = modeloClient.gerarSimuladoOriginal(userId); // sem topic
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao gerar simulado original.", e);
        }

        var lista  = mapModeloParaQuestoes(sim.getId(), userId, modulo, 1);

        List<Map<String,Object>> qsCriadas;
        try {
            qsCriadas = questaoClient.criarQuestoes(bearer, lista);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao criar questões.", e);
        }

        return new SimuladoComQuestoesDTO(
                sim.getId(), sim.getIdUsuario(), sim.getTipo(), sim.getData(), sim.getStatus(), sim.getFaturaWins(),
                qsCriadas
        );
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

            var upd = new QuestaoUpdateDTO(
                    body.id_simulado(),                 // id_formulario
                    body.id_usuario(),                  // id_usuario
                    q.topic(),
                    q.subskill(),
                    q.difficulty(),
                    q.question(),
                    q.options(),
                    q.correct_option(),
                    q.solution(),                       // legado
                    q.structure(),
                    q.format(),
                    q.representation(),
                    q.hint(),                           // legado
                    q.target_mistakes(),
                    q.source(),
                    q.solution_english(),
                    q.solution_portugues(),
                    q.hint_english(),
                    q.hint_portugues(),
                    q.figure(),
                    q.alternativa_marcada(),
                    q.dica(),
                    q.solucao(),
                    q.modulo()
            );

            questaoClient.atualizar(bearer, q.id(), upd);
        }

        // 2) ATUALIZAR O SIMULADO (status FINALIZADO + demais campos do body que você autoriza atualizar)
        if (body.tipo() != null)        sim.setTipo(body.tipo());
        if (body.data() != null)        sim.setData(body.data());
        if (body.fatura_wins() != null) sim.setFaturaWins(body.fatura_wins());
        // status no body é ignorado; a regra do endpoint é finalizar:
        sim.setStatus("FINALIZADO");
        repo.save(sim);

        // 3) RECALCULAR PERFIL a partir de TODO o histórico do usuário
        var todasQuestoesUsuario = questaoClient.listarPorUsuario(bearer, sim.getIdUsuario());

        // 3.1) Carregar todos os simulados do usuário (datas e últimos finalizados)
        var simuladosUsuario = repo.findByIdUsuario(sim.getIdUsuario(),
                Sort.by(Sort.Direction.DESC, "data"));

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
            return repo.findByIdUsuario(idUsuario, Sort.by(Sort.Direction.DESC, "data"))
                    .stream().map(this::toDTO).toList();
        }

        public SimuladoDTO ultimoPorUsuario(String idUsuario) {
            var s = repo.findMaisRecente(idUsuario);
            if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nenhum simulado encontrado para o usuário.");
            return toDTO(s);
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
            if (acc >= 0.8) p += 5;
            if (hardExp >= 2 && acc >= 0.6) p += 3;
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
