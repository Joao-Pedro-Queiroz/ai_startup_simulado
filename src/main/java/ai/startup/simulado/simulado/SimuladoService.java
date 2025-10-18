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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SimuladoService {

    private final SimuladoRepository repo;
    private final UsuarioClient usuarioClient;
    private final QuestaoClient questaoClient;
    private final ModeloClient modeloClient;
    private final PerfilClient perfilClient;
    private final PerfilTemplateProvider perfilTemplateProvider;

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

    /** Inicia simulado ADAPTATIVO (2 chamadas de ~22 questões cada; total 44) */
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

        // 3) RECALCULAR PERFIL
        //    sempre a partir de TODAS as questões do USUÁRIO
        var todasQuestoesUsuario = questaoClient.listarPorUsuario(bearer, sim.getIdUsuario());

        //    3.1 Carregar template COMPLETO de perfil (todas subskills/structures)
        //        -> você pode injetar um provider que lê o mesmo JSON usado no register
        Map<String, TopicDTO> template = perfilTemplateProvider.getTopicsTemplate(sim.getIdUsuario()); // injete isso

        //    3.2 Construir um mapa mutável a partir do template (zerado)
        Map<String, TopicDTO> topicsAgregado = deepCloneAndZero(template);

        //    3.3 Agregar todas as questões do usuário
        agregarQuestoesNoPerfil(topicsAgregado, todasQuestoesUsuario);

        //    3.4 Montar payload e chamar API de Perfil (update por usuário = merge)
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
                            var stName = e.getKey();
                            var tpl = e.getValue();
                            totalStructs++;
                            newStructs.put(stName, new StructureDTO(
                                    50, 0L, 0L, 0.0, 0.0,
                                    false, false, false,
                                    0L, 0L,
                                    "easy", 0, null
                            ));
                        }
                    }
                    newSubs.put(subName, new SubskillDTO(
                            0L, 0L, 0.0, 0.0, "",     // last_seen_at_s string vazia (mantemos padrão)
                            null,                     // missed_two_sessions: não recalculamos aqui (merge ignora null)
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
    @SuppressWarnings("unchecked")
    private void agregarQuestoesNoPerfil(Map<String, TopicDTO> profile, List<Map<String,Object>> qs) {
        if (qs == null || qs.isEmpty()) return;

        for (var q : qs) {
            String topic = str(q.get("topic"));
            String sub   = str(q.get("subskill"));
            String st    = str(q.get("structure"));
            if (topic == null || sub == null || st == null) continue;

            var t = profile.get(topic);
            if (t == null) continue; // não existe no template → ignoramos
            var s = t.subskills().get(sub);
            if (s == null) continue;
            var mapStructs = s.structures();
            if (mapStructs == null) continue;
            var stDTO = mapStructs.get(st);
            if (stDTO == null) continue;

            // ====== nível structure ======
            long attempts_sc = (stDTO.attempts_sc() == null ? 0L : stDTO.attempts_sc()) + 1L;

            Object marcada = q.get("alternativa_marcada");
            Object correta = q.get("correct_option");
            boolean acertou = (marcada != null && correta != null &&
                    marcada.toString().equalsIgnoreCase(correta.toString()));
            long correct_sc = (stDTO.correct_sc() == null ? 0L : stDTO.correct_sc()) + (acertou ? 1L : 0L);

            boolean usouDica = Boolean.TRUE.equals(q.get("dica"));
            boolean abriuSol = Boolean.TRUE.equals(q.get("solucao"));

            long hintsCount     = Math.round((stDTO.hints_rate_sc() == null ? 0.0 : stDTO.hints_rate_sc()) * (stDTO.attempts_sc() == null ? 0 : stDTO.attempts_sc()));
            long solutionsCount = Math.round((stDTO.solutions_rate_sc() == null ? 0.0 : stDTO.solutions_rate_sc()) * (stDTO.attempts_sc() == null ? 0 : stDTO.attempts_sc()));
            hintsCount     += (usouDica ? 1 : 0);
            solutionsCount += (abriuSol ? 1 : 0);

            String diff = str(q.get("difficulty"));
            boolean easy   = "easy".equalsIgnoreCase(diff);
            boolean medium = "medium".equalsIgnoreCase(diff);
            boolean hard   = "hard".equalsIgnoreCase(diff);

            long mediumExp = (stDTO.medium_exposures_sc() == null ? 0L : stDTO.medium_exposures_sc()) + (medium ? 1L : 0L);
            long hardExp   = (stDTO.hard_exposures_sc()   == null ? 0L : stDTO.hard_exposures_sc())   + (hard   ? 1L : 0L);

            // Recalcula rates
            double hintsRate     = attempts_sc == 0 ? 0.0 : (hintsCount * 1.0 / attempts_sc);
            double solutionsRate = attempts_sc == 0 ? 0.0 : (solutionsCount * 1.0 / attempts_sc);

            // aplica no structure
            mapStructs.put(st, new StructureDTO(
                    stDTO.P_sc() == null ? 50 : stDTO.P_sc(), // mantemos P_sc neutro
                    attempts_sc,
                    correct_sc,
                    hintsRate,
                    solutionsRate,
                    stDTO.easy_seen_sc()   || easy,
                    stDTO.medium_seen_sc() || medium,
                    stDTO.hard_seen_sc()   || hard,
                    mediumExp,
                    hardExp,
                    stDTO.last_level_applied_sc() == null ? "easy" : stDTO.last_level_applied_sc(),
                    stDTO.cooldown_sc() == null ? 0 : stDTO.cooldown_sc(),
                    stDTO.last_seen_at_sc()  // opcional manter vazio
            ));
        }

        // ====== derivar métricas no nível subskill (somatórios dos structures)
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

                for (var stDTO : structs.values()) {
                    long a = stDTO.attempts_sc() == null ? 0L : stDTO.attempts_sc();
                    attempts_s += a;
                    correct_s  += (stDTO.correct_sc() == null ? 0L : stDTO.correct_sc());
                    if (a > 0) {
                        vistas += 1;
                        // reverse rates → contagem aproximada para derivar rate_s
                        hintsUsed += Math.round((stDTO.hints_rate_sc() == null ? 0.0 : stDTO.hints_rate_sc()) * a);
                        solsUsed  += Math.round((stDTO.solutions_rate_sc() == null ? 0.0 : stDTO.solutions_rate_sc()) * a);
                    }
                    easy_s |= (stDTO.easy_seen_sc()   != null && stDTO.easy_seen_sc());
                    med_s  |= (stDTO.medium_seen_sc() != null && stDTO.medium_seen_sc());
                    hard_s |= (stDTO.hard_seen_sc()   != null && stDTO.hard_seen_sc());
                }
                double hr_s = attempts_s == 0 ? 0.0 : (hintsUsed * 1.0 / attempts_s);
                double sr_s = attempts_s == 0 ? 0.0 : (solsUsed  * 1.0 / attempts_s);

                subs.put(sName, new SubskillDTO(
                        attempts_s, correct_s, hr_s, sr_s, "",
                        null,                    // missed_two_sessions: não recalculamos aqui
                        easy_s, med_s, hard_s,
                        vistas, total,
                        structs
                ));
            }
        }
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    private SimuladoDTO toDTO(Simulado s) {
        return new SimuladoDTO(
                s.getId(), s.getIdUsuario(), s.getTipo(), s.getData(), s.getStatus(), s.getFaturaWins()
        );
    }
}
