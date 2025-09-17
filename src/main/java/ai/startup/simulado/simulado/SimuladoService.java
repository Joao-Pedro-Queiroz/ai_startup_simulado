package ai.startup.simulado.simulado;

import ai.startup.simulado.client.ModeloClient;
import ai.startup.simulado.perfil.PerfilClient;
import ai.startup.simulado.perfil.PerfilCreateDTO;
import ai.startup.simulado.questaosimulado.QuestaoClient;
import ai.startup.simulado.questaosimulado.QuestoesCreateItemDTO;
import ai.startup.simulado.usuario.UsuarioClient;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    // ===== CRUD =====
    public List<SimuladoDTO> listar() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "data"))
                .stream().map(this::toDTO).toList();
    }

    public SimuladoDTO obter(String id) {
        return repo.findById(id).map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Simulado não encontrado."));
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
        var s = repo.findById(id).orElseThrow(() -> new RuntimeException("Simulado não encontrado."));
        if (d.id_usuario()  != null) s.setIdUsuario(d.id_usuario());
        if (d.tipo()        != null) s.setTipo(d.tipo());
        if (d.data()        != null) s.setData(d.data());
        if (d.status()      != null) s.setStatus(d.status());
        if (d.fatura_wins() != null) s.setFaturaWins(d.fatura_wins());
        return toDTO(repo.save(s));
    }

    /** DELETE: remove também as questões do simulado na API de Questões */
    public void deletar(String id, String bearerToken) {
        if (!repo.existsById(id)) throw new RuntimeException("Simulado não encontrado.");
        var qs = questaoClient.listarPorSimulado(bearerToken, id);
        if (qs != null) {
            for (var q : qs) {
                Object qid = q.get("id");
                if (qid != null) questaoClient.deletar(bearerToken, qid.toString());
            }
        }
        repo.deleteById(id);
    }

    // ===== Criação Adaptativo (2 chamadas de 22) =====
    public SimuladoComQuestoesDTO iniciarAdaptativo(HttpServletRequest req, StartAdaptativoDTO payload) {
        String bearer = req.getHeader("Authorization");
        String email = (String) req.getAttribute("authEmail");
        if (email == null) throw new RuntimeException("E-mail não encontrado no JWT.");

        // id_usuario via API de usuário
        String userId = usuarioClient.getUserIdByEmail(email, bearer);

        // Regra: não criar se último está ABERTO
        var ultimo = repo.findMaisRecente(userId);
        if (ultimo != null && "ABERTO".equalsIgnoreCase(ultimo.getStatus()))
            throw new RuntimeException("Há um simulado em aberto. Finalize-o antes de criar outro.");

        var sim = repo.save(Simulado.builder()
                .idUsuario(userId)
                .tipo("ADAPTATIVO")
                .data(LocalDateTime.now())
                .status("ABERTO")
                .faturaWins(payload.fatura_wins())
                .build());

        var m1 = modeloClient.gerarModuloAdaptativo(userId, payload.topic());
        var m2 = modeloClient.gerarModuloAdaptativo(userId, payload.topic());

        var todas = new ArrayList<QuestoesCreateItemDTO>();
        todas.addAll(mapModeloParaQuestoes(sim.getId(), userId, m1, 1));
        todas.addAll(mapModeloParaQuestoes(sim.getId(), userId, m2, 2));

        var qsCriadas = questaoClient.criarQuestoes(bearer, todas);

        return new SimuladoComQuestoesDTO(
                sim.getId(), sim.getIdUsuario(), sim.getTipo(), sim.getData(), sim.getStatus(), sim.getFaturaWins(),
                qsCriadas
        );
    }

    // ===== Criação Original (1 chamada de 44) =====
    public SimuladoComQuestoesDTO iniciarOriginal(HttpServletRequest req, StartOriginalDTO payload) {
        String bearer = req.getHeader("Authorization");
        String email  = (String) req.getAttribute("authEmail");
        if (email == null) throw new RuntimeException("E-mail não encontrado no JWT.");

        String userId = usuarioClient.getUserIdByEmail(email, bearer);

        var ultimo = repo.findMaisRecente(userId);
        if (ultimo != null && "ABERTO".equalsIgnoreCase(ultimo.getStatus()))
            throw new RuntimeException("Há um simulado em aberto. Finalize-o antes de criar outro.");

        var sim = repo.save(Simulado.builder()
                .idUsuario(userId)
                .tipo("ORIGINAL")
                .data(java.time.LocalDateTime.now())
                .status("ABERTO")
                .faturaWins(payload.fatura_wins())
                .build());

        // chama o modelo UMA vez e sem topic
        var modulo = modeloClient.gerarSimuladoOriginal(userId);
        var lista  = mapModeloParaQuestoes(sim.getId(), userId, modulo, 1);
        var qsCriadas = questaoClient.criarQuestoes(bearer, lista);

        return new SimuladoComQuestoesDTO(
                sim.getId(), sim.getIdUsuario(), sim.getTipo(), sim.getData(), sim.getStatus(), sim.getFaturaWins(),
                qsCriadas
        );
    }

    // ===== Finalizar simulado =====
    public SimuladoDTO finalizar(String idSimulado, HttpServletRequest req) {
        String bearer = req.getHeader("Authorization");

        var sim = repo.findById(idSimulado).orElseThrow(() -> new RuntimeException("Simulado não encontrado."));
        if (!"ABERTO".equalsIgnoreCase(sim.getStatus()))
            throw new RuntimeException("Simulado já finalizado.");

        var qs = questaoClient.listarPorSimulado(bearer, idSimulado);
        if (qs == null) qs = List.of();

        // agrupa por (topic, subskill) e computa métricas
        Map<String, Map<String, List<Map<String,Object>>>> porTopicSubskill = qs.stream()
                .collect(Collectors.groupingBy(
                        q -> String.valueOf(q.getOrDefault("topic","")),
                        Collectors.groupingBy(q -> String.valueOf(q.getOrDefault("subskill","")))
                ));

        List<PerfilCreateDTO> perfis = new ArrayList<>();
        for (var eTopic : porTopicSubskill.entrySet()) {
            String topic = eTopic.getKey();
            for (var eSub : eTopic.getValue().entrySet()) {
                String sub = eSub.getKey();
                var list = eSub.getValue();

                long total = list.size();
                long acertos = list.stream().filter(q -> {
                    Object marcada = q.get("alternativa_marcada");
                    Object correta  = q.get("correct_option");
                    return marcada != null && marcada.toString().equalsIgnoreCase(String.valueOf(correta));
                }).count();
                long erros = total - acertos;            // não respondidas contam como erradas
                double acuracia = (total == 0) ? 0.0 : (acertos * 1.0 / total);

                perfis.add(new PerfilCreateDTO(sim.getIdUsuario(), topic, sub, acertos, erros, acuracia));
            }
        }

        if (!perfis.isEmpty()) perfilClient.criarPerfis(bearer, perfis);

        sim.setStatus("FINALIZADO");
        repo.save(sim);
        return toDTO(sim);
    }

    // ===== Listagens por usuário =====
    public List<SimuladoDTO> listarPorUsuario(String idUsuario) {
        return repo.findByIdUsuario(idUsuario, Sort.by(Sort.Direction.DESC, "data"))
                .stream().map(this::toDTO).toList();
    }

    public SimuladoDTO ultimoPorUsuario(String idUsuario) {
        var s = repo.findMaisRecente(idUsuario);
        if (s == null) throw new RuntimeException("Nenhum simulado encontrado para o usuário.");
        return toDTO(s);
    }

    // ===== Helpers =====
    private List<QuestoesCreateItemDTO> mapModeloParaQuestoes(String idSimulado, String userId,
                                                              Map<String,Object> moduloResp, int modulo) {
        var arr = (List<Map<String,Object>>) moduloResp.getOrDefault("questions", List.of());
        List<QuestoesCreateItemDTO> out = new ArrayList<>();
        for (var q : arr) {
            out.add(new QuestoesCreateItemDTO(
                    idSimulado,
                    userId,
                    str(q.get("topic")),
                    str(q.get("subskill")),
                    str(q.get("difficulty")),
                    str(q.get("question")),
                    (Map<String,String>) q.get("options"),
                    str(q.get("correct_option")),
                    (List<String>) q.get("solution"),
                    str(q.get("structure")),
                    str(q.get("format")),
                    str(q.get("representation")),
                    str(q.get("hint")),
                    (List<String>) q.get("target_mistakes"),
                    str(q.getOrDefault("source","ai_generated")),
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