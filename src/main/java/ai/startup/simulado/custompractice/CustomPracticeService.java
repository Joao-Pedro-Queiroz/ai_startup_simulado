package ai.startup.simulado.custompractice;

import ai.startup.simulado.simulado.*;
import ai.startup.simulado.usuario.*;
import ai.startup.simulado.questaosimulado.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsável pela lógica de criação de Custom Practice.
 * Orquestra a comunicação entre os diferentes microserviços para:
 * 1. Validar saldo de wins do usuário
 * 2. Debitar wins
 * 3. Transformar seleções do MindMap em plan items
 * 4. Chamar approva-descartes para gerar questões
 * 5. Salvar simulado e questões no banco
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomPracticeService {

    private final CustomPracticeClient customPracticeClient;
    private final SimuladoRepository simuladoRepository;
    private final UsuarioClient usuarioClient;
    private final QuestaoClient questaoClient;

    private static final int WINS_POR_QUESTAO = 2;
    private static final int MIN_QUESTOES = 1;  // Mínimo de 1 questão
    private static final int MAX_QUESTOES_POR_STRUCTURE = 5;  // Máximo de 5 por structure

    /**
     * Cria um Custom Practice completo baseado nas seleções do usuário.
     * 
     * @param request DTO com usuarioId, selections e totalQuestions
     * @param authorizationHeader JWT token para autenticação nos serviços
     * @return SimuladoComQuestoesDTO com o simulado criado e as questões
     * @throws IllegalArgumentException se dados inválidos
     * @throws IllegalStateException se saldo insuficiente ou simulado em aberto
     */
    @Transactional
    public SimuladoComQuestoesDTO criarCustomPractice(
            CustomPracticeRequestDTO request,
            String authorizationHeader
    ) {
        log.info("[CUSTOM] ========== INÍCIO CUSTOM PRACTICE ==========");
        log.info("[CUSTOM] Usuário ID: {}", request.getUsuarioId());
        log.info("[CUSTOM] Seleções: {}", request.getSelections().size());
        log.info("[CUSTOM] Total de questões: {}", request.getTotalQuestions());

        // 1. Validações básicas
        validarRequest(request);

        // 2. Buscar dados do usuário
        UsuarioDTO usuario = usuarioClient.buscarPorId(
            request.getUsuarioId(), 
            authorizationHeader
        );
        
        log.info("[CUSTOM] Usuário encontrado: {} (wins atuais: {})", 
            usuario.email(), usuario.wins());

        // 3. Calcular custo e validar saldo
        int custoTotal = request.getTotalQuestions() * WINS_POR_QUESTAO;
        
        if (usuario.wins() < custoTotal) {
            log.error("[CUSTOM] ❌ Saldo insuficiente. Necessário: {}, Disponível: {}", 
                custoTotal, usuario.wins());
            throw new IllegalStateException(
                String.format("Saldo insuficiente. Necessário: %d wins, Disponível: %d wins",
                    custoTotal, usuario.wins())
            );
        }

        // 4. Verificar se não há simulado em aberto
        verificarSimuladoEmAberto(request.getUsuarioId());

        // 5. Debitar wins do usuário
        long novoSaldo = usuario.wins() - custoTotal;
        UsuarioUpdateDTO updateWins = new UsuarioUpdateDTO(
            null, // nome
            null, // sobrenome
            null, // telefone
            null, // nascimento
            null, // email
            null, // cpf
            null, // senha
            novoSaldo, // wins
            null, // streaks
            null, // xp
            null  // permissao
        );
        usuarioClient.atualizar(
            authorizationHeader,
            request.getUsuarioId(), 
            updateWins
        );
        
        log.info("[CUSTOM] ✅ Wins debitados: {} → {} (-{})", 
            usuario.wins(), novoSaldo, custoTotal);

        try {
            // 6. Transformar selections em plan items
            List<CustomPracticeItemDTO> planItems = 
                transformarSelections(request.getSelections(), request.getTotalQuestions());
            
            log.info("[CUSTOM] Plan items gerados: {}", planItems.size());

            // 7. Chamar approva-descartes
            Map<String, Object> response = customPracticeClient.gerarCustomExam(planItems);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questoesGeradas = 
                (List<Map<String, Object>>) response.get("questions");

            if (questoesGeradas == null || questoesGeradas.isEmpty()) {
                log.error("[CUSTOM] ❌ Nenhuma questão gerada pelo approva-descartes");
                throw new RuntimeException("Nenhuma questão foi gerada");
            }

            log.info("[CUSTOM] Total de questões geradas: {}", questoesGeradas.size());

            // 8. Criar simulado no MongoDB
            Simulado simulado = Simulado.builder()
                .idUsuario(request.getUsuarioId())
                .tipo("CUSTOM_PRACTICE")
                .status("ABERTO")
                .data(LocalDateTime.now())
                .faturaWins(custoTotal)
                .build();
            
            Simulado simuladoSalvo = simuladoRepository.save(simulado);
            log.info("[CUSTOM] ✅ Simulado salvo no MongoDB: {}", simuladoSalvo.getId());

            // 9. Salvar questões no serviço de questões
            List<QuestoesCreateItemDTO> questoesParaSalvar = 
                montarQuestoesDTO(questoesGeradas, simuladoSalvo.getId(), request.getUsuarioId());
            
            questaoClient.criarQuestoes(authorizationHeader, questoesParaSalvar);
            log.info("[CUSTOM] ✅ {} questões salvas no banco de questões", 
                questoesParaSalvar.size());

            // 10. Retornar simulado com questões
            SimuladoDTO simuladoDTO = toDTO(simuladoSalvo);
            SimuladoComQuestoesDTO resultado = new SimuladoComQuestoesDTO(simuladoDTO, questoesGeradas);

            log.info("[CUSTOM] ========== FIM CUSTOM PRACTICE ==========");
            return resultado;
            
        } catch (Exception e) {
            log.error("[CUSTOM] ❌ Erro durante criação do custom practice: {}", e.getMessage());
            log.error("[CUSTOM] Revertendo débito de wins...");
            
            // Reverter débito de wins em caso de erro
            reverterDebito(request.getUsuarioId(), custoTotal, authorizationHeader);
            
            throw new RuntimeException("Falha ao criar custom practice: " + e.getMessage(), e);
        }
    }

    /**
     * Valida os dados da requisição.
     * Máximo de questões = número de structures selecionadas × 5
     */
    private void validarRequest(CustomPracticeRequestDTO request) {
        if (request.getUsuarioId() == null || request.getUsuarioId().isBlank()) {
            throw new IllegalArgumentException("usuarioId é obrigatório");
        }
        
        if (request.getSelections() == null || request.getSelections().isEmpty()) {
            throw new IllegalArgumentException("É necessário selecionar pelo menos um tópico");
        }
        
        if (request.getTotalQuestions() == null) {
            throw new IllegalArgumentException("totalQuestions é obrigatório");
        }
        
        if (request.getTotalQuestions() < MIN_QUESTOES) {
            throw new IllegalArgumentException("Mínimo de " + MIN_QUESTOES + " questão");
        }
        
        // Máximo = 5 questões por structure selecionado
        int numSelections = request.getSelections().size();
        int maxPermitido = numSelections * MAX_QUESTOES_POR_STRUCTURE;
        
        if (request.getTotalQuestions() > maxPermitido) {
            throw new IllegalArgumentException(
                String.format("Máximo de %d questões para %d structure(s) selecionada(s) (5 por structure)",
                    maxPermitido, numSelections)
            );
        }
        
        log.info("[CUSTOM] Validação OK: {} questões para {} structure(s) (máx permitido: {})",
            request.getTotalQuestions(), numSelections, maxPermitido);
    }

    /**
     * Verifica se o usuário tem algum simulado em aberto.
     */
    private void verificarSimuladoEmAberto(String usuarioId) {
        List<Simulado> abertos = simuladoRepository
            .findByIdUsuarioAndStatus(usuarioId, "ABERTO");
        
        if (!abertos.isEmpty()) {
            log.error("[CUSTOM] ❌ Usuário {} tem simulado em aberto: {}", 
                usuarioId, abertos.get(0).getId());
            throw new IllegalStateException(
                "Há um simulado em aberto. Finalize-o antes de iniciar um novo."
            );
        }
    }

    /**
     * Reverte o débito de wins em caso de erro.
     */
    private void reverterDebito(String usuarioId, int valor, String authHeader) {
        try {
            UsuarioDTO user = usuarioClient.buscarPorId(usuarioId, authHeader);
            long novoSaldo = user.wins() + valor;
            UsuarioUpdateDTO update = new UsuarioUpdateDTO(
                null, null, null, null, null, null, null,
                novoSaldo, null, null, null
            );
            usuarioClient.atualizar(authHeader, usuarioId, update);
            log.info("[CUSTOM] ✅ Wins devolvidos ao usuário: +{}", valor);
        } catch (Exception e) {
            log.error("[CUSTOM] ❌ ERRO CRÍTICO: Não foi possível reverter débito de {} wins para usuário {}", 
                valor, usuarioId);
            log.error("[CUSTOM] Erro: {}", e.getMessage());
        }
    }

    /**
     * Transforma as seleções do usuário em plan items para o approva-descartes.
     * Distribui as questões proporcionalmente entre as seleções.
     * Para cada seleção, divide em 3 níveis de dificuldade:
     * - Easy: 30%
     * - Medium: 40%
     * - Hard: 30%
     */
    private List<CustomPracticeItemDTO> transformarSelections(
            List<CustomPracticeSelectionDTO> selections,
            int totalQuestions
    ) {
        List<CustomPracticeItemDTO> planItems = new ArrayList<>();
        
        int numSelections = selections.size();
        int questoesPorSelection = totalQuestions / numSelections;
        int resto = totalQuestions % numSelections;

        log.info("[CUSTOM] Distribuindo {} questões entre {} seleções", 
            totalQuestions, numSelections);

        for (int i = 0; i < selections.size(); i++) {
            CustomPracticeSelectionDTO sel = selections.get(i);
            
            // Quantidade de questões para esta seleção
            // As primeiras seleções recebem +1 questão se houver resto
            int count = questoesPorSelection + (i < resto ? 1 : 0);
            
            log.info("[CUSTOM] Seleção {}: {} questões para {}/{}/{}", 
                i + 1, count, sel.getSkillName(), sel.getSubskillName(), sel.getStructureName());
            
            // Distribuir por dificuldade: 30% easy, 40% medium, 30% hard
            int easy = (int) Math.round(count * 0.3);
            int medium = (int) Math.round(count * 0.4);
            int hard = count - easy - medium; // garante que a soma é exata

            // Normalizar nomes (frontend pode usar espaços, backend precisa underscores)
            String topic = normalizar(sel.getSkillName());
            String subskill = normalizar(sel.getSubskillName());
            String structure = normalizar(sel.getStructureName());

            log.info("[CUSTOM]   - Easy: {}, Medium: {}, Hard: {}", easy, medium, hard);

            // Criar items (um por dificuldade)
            // Nota: MongoDB usa difficulty em lowercase ("easy", "medium", "hard")
            if (easy > 0) {
                planItems.add(criarItem(topic, subskill, structure, "easy", easy));
            }
            if (medium > 0) {
                planItems.add(criarItem(topic, subskill, structure, "medium", medium));
            }
            if (hard > 0) {
                planItems.add(criarItem(topic, subskill, structure, "hard", hard));
            }
        }

        return planItems;
    }

    /**
     * Cria um item do plan para o approva-descartes.
     */
    private CustomPracticeItemDTO criarItem(
            String topic, String subskill, String structure, 
            String difficulty, int count
    ) {
        CustomPracticeItemDTO item = new CustomPracticeItemDTO();
        item.setTopic(topic);
        item.setSubskill(subskill);
        item.setStructure(structure);
        item.setDifficulty(difficulty);
        item.setCount(count);
        return item;
    }

    /**
     * Normaliza strings para o formato esperado pelo MongoDB/approva-descartes.
     * Converte para lowercase e substitui espaços por underscores.
     */
    private String normalizar(String texto) {
        if (texto == null) return null;
        return texto.toLowerCase()
                    .trim()
                    .replace(" ", "_")
                    .replace("-", "_")
                    .replace("&", "and")
                    .replaceAll("[^a-z0-9_]", "");
    }

    /**
     * Monta os DTOs de questão para salvar no serviço de questões.
     * QuestoesCreateItemDTO é um record com muitos campos.
     */
    private List<QuestoesCreateItemDTO> montarQuestoesDTO(
            List<Map<String, Object>> questoesGeradas,
            String simuladoId,
            String usuarioId
    ) {
        return questoesGeradas.stream().map(q -> {
            // Extrair valores do Map
            String topic = (String) q.get("topic");
            String subskill = (String) q.get("subskill");
            String difficulty = (String) q.get("difficulty");
            String question = (String) q.get("question");
            @SuppressWarnings("unchecked")
            Map<String, String> options = (Map<String, String>) q.get("options");
            Object correctOption = q.get("correct_option");
            String structure = (String) q.get("structure");
            String format = (String) q.get("format");
            String representation = (String) q.get("representation");
            String source = (String) q.get("source");
            @SuppressWarnings("unchecked")
            List<String> solutionEnglish = (List<String>) q.get("solution_english");
            @SuppressWarnings("unchecked")
            List<String> solutionPortugues = (List<String>) q.get("solution_portugues");
            String hintEnglish = (String) q.get("hint_english");
            String hintPortugues = (String) q.get("hint_portugues");
            @SuppressWarnings("unchecked")
            List<String> targetMistakes = (List<String>) q.get("target_mistakes");
            @SuppressWarnings("unchecked")
            Map<String, Object> figure = (Map<String, Object>) q.get("figure");
            
            // Criar record usando o construtor
            return new QuestoesCreateItemDTO(
                simuladoId,        // id_formulario
                usuarioId,         // id_usuario
                topic,             // topic
                subskill,          // subskill
                difficulty,        // difficulty
                question,          // question
                options,           // options
                correctOption,     // correct_option
                solutionPortugues, // solution (legado)
                structure,         // structure
                format,            // format
                representation,    // representation
                hintPortugues,     // hint (legado)
                targetMistakes,    // target_mistakes
                source,            // source
                solutionEnglish,   // solution_english
                solutionPortugues, // solution_portugues
                hintEnglish,       // hint_english
                hintPortugues,     // hint_portugues
                figure,            // figure
                null,              // alternativa_marcada
                false,             // dica
                false,             // solucao
                1                  // modulo (1 para custom practice)
            );
        }).collect(Collectors.toList());
    }

    /**
     * Converte entidade Simulado para SimuladoDTO (record).
     */
    private SimuladoDTO toDTO(Simulado simulado) {
        return new SimuladoDTO(
            simulado.getId(),
            simulado.getIdUsuario(),
            simulado.getTipo(),
            simulado.getData(),
            simulado.getStatus(),
            simulado.getFaturaWins()
        );
    }
}
