package ai.startup.simulado.simulado;

import ai.startup.simulado.client.ModeloClient;
import ai.startup.simulado.perfil.PerfilClient;
import ai.startup.simulado.perfil.PerfilCreateDTO;
import ai.startup.simulado.perfil.TopicDTO;
import ai.startup.simulado.perfil.SubskillDTO;
import ai.startup.simulado.perfil.StructureDTO;
import ai.startup.simulado.questaosimulado.QuestaoClient;
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

    public SimuladoService(SimuladoRepository repo,
                           UsuarioClient usuarioClient,
                           QuestaoClient questaoClient,
                           ModeloClient modeloClient,
                           PerfilClient perfilClient) {
        this.repo = repo;
        this.usuarioClient = usuarioClient;
        this.questaoClient = questaoClient;
        this.modeloClient = modeloClient;
        this.perfilClient = perfilClient;
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
    public SimuladoDTO finalizar(String idSimulado, HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");

        var sim = repo.findById(idSimulado)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Simulado não encontrado."));
        if (!"ABERTO".equalsIgnoreCase(sim.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Simulado já finalizado.");

        List<Map<String,Object>> qs;
        try {
            qs = questaoClient.listarPorSimulado(bearer, idSimulado);
            if (qs == null) qs = List.of();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao listar questões do simulado.", e);
        }

        // topic -> subskill -> structure -> lista de questões
        Map<String, Map<String, Map<String, List<Map<String,Object>>>>> tree = new HashMap<>();
        for (var q : qs) {
            String topic = String.valueOf(q.getOrDefault("topic",""));
            String sub   = String.valueOf(q.getOrDefault("subskill",""));
            String struc = String.valueOf(q.getOrDefault("structure",""));
            tree.computeIfAbsent(topic, t -> new HashMap<>())
                .computeIfAbsent(sub, s -> new HashMap<>())
                .computeIfAbsent(struc, st -> new ArrayList<>())
                .add(q);
        }

        Map<String, TopicDTO> topicsDTO = new HashMap<>();

        for (var eTopic : tree.entrySet()) {
            Map<String, SubskillDTO> subskillsDTO = new HashMap<>();

            for (var eSub : eTopic.getValue().entrySet()) {
                Map<String, StructureDTO> structuresDTO = new HashMap<>();

                long attempts_s = 0, correct_s = 0, hints_s = 0, solutions_s = 0;
                boolean easy_s=false, med_s=false, hard_s=false;

                for (var eStr : eSub.getValue().entrySet()) {
                    var list = eStr.getValue();

                    long attempts_sc = list.size();
                    long correct_sc  = list.stream().filter(x -> {
                        var m = x.get("alternativa_marcada");
                        var c = x.get("correct_option");
                        return m != null && c != null && m.toString().equalsIgnoreCase(c.toString());
                    }).count();
                    long hints_sc     = list.stream().filter(x -> Boolean.TRUE.equals(x.get("dica"))).count();
                    long solutions_sc = list.stream().filter(x -> Boolean.TRUE.equals(x.get("solucao"))).count();

                    boolean easy_seen   = list.stream().anyMatch(x -> "easy".equalsIgnoreCase(String.valueOf(x.get("difficulty"))));
                    boolean medium_seen = list.stream().anyMatch(x -> "medium".equalsIgnoreCase(String.valueOf(x.get("difficulty"))));
                    boolean hard_seen   = list.stream().anyMatch(x -> "hard".equalsIgnoreCase(String.valueOf(x.get("difficulty"))));

                    double hr = attempts_sc==0 ? 0.0 : (hints_sc*1.0/attempts_sc);
                    double sr = attempts_sc==0 ? 0.0 : (solutions_sc*1.0/attempts_sc);

                    long mediumExp = list.stream().filter(x -> "medium".equalsIgnoreCase(String.valueOf(x.get("difficulty")))).count();
                    long hardExp   = list.stream().filter(x -> "hard".equalsIgnoreCase(String.valueOf(x.get("difficulty")))).count();

                    structuresDTO.put(eStr.getKey(),
                            new StructureDTO(
                                    50, attempts_sc, correct_sc, hr, sr,
                                    easy_seen, medium_seen, hard_seen,
                                    mediumExp, hardExp, "easy", 0, null
                            )
                    );

                    attempts_s  += attempts_sc;
                    correct_s   += correct_sc;
                    hints_s     += hints_sc;
                    solutions_s += solutions_sc;
                    easy_s |= easy_seen;  med_s |= medium_seen;  hard_s |= hard_seen;
                }

                long vistas = structuresDTO.values().stream()
                        .filter(st -> st.attempts_sc()!=null && st.attempts_sc()>0).count();
                long total  = structuresDTO.size();
                double hr_s = attempts_s==0 ? 0.0 : (hints_s*1.0/attempts_s);
                double sr_s = attempts_s==0 ? 0.0 : (solutions_s*1.0/attempts_s);

                subskillsDTO.put(eSub.getKey(),
                        new SubskillDTO(
                                attempts_s, correct_s, hr_s, sr_s, null, 
                                null, easy_s, med_s, hard_s,
                                vistas, total, structuresDTO
                        )
                );
            }

            topicsDTO.put(eTopic.getKey(), new TopicDTO(subskillsDTO));
        }

        // envia UM perfil (novo contrato do Perfil)
        var perfilPayload = new PerfilCreateDTO(sim.getIdUsuario(), topicsDTO);
        try {
            perfilClient.atualizarPerfilPorUsuario(bearer, sim.getIdUsuario(), perfilPayload);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao atualizar Perfil do usuário.", e);
        }

        // finaliza o simulado
        sim.setStatus("FINALIZADO");
        repo.save(sim);

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

    private String str(Object o) { return o == null ? null : o.toString(); }

    private SimuladoDTO toDTO(Simulado s) {
        return new SimuladoDTO(
                s.getId(), s.getIdUsuario(), s.getTipo(), s.getData(), s.getStatus(), s.getFaturaWins()
        );
    }
}
