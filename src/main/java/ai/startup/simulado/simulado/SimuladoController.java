package ai.startup.simulado.simulado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@SecurityRequirement(name = "bearerAuth")
public class SimuladoController {

    private final SimuladoService service;
    public SimuladoController(SimuladoService service) { this.service = service; }

    // CRUD
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/simulados")
    public ResponseEntity<List<SimuladoDTO>> listar() { return ResponseEntity.ok(service.listar()); }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/simulados/{id}")
    public ResponseEntity<SimuladoDTO> obter(@PathVariable String id) { return ResponseEntity.ok(service.obter(id)); }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/simulados")
    public ResponseEntity<SimuladoDTO> criar(@RequestBody SimuladoCreateDTO dto) { return ResponseEntity.ok(service.criar(dto)); }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/simulados/{id}")
    public ResponseEntity<SimuladoDTO> atualizar(@PathVariable String id, @RequestBody SimuladoUpdateDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/simulados/{id}")
    public ResponseEntity<Void> deletar(@PathVariable String id, HttpServletRequest req) {
        service.deletar(id, req.getHeader("Authorization"));
        return ResponseEntity.noContent().build();
    }

    // Iniciar simulado adaptativo (gera 44 questões em 2 chamadas)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Inicia simulado adaptativo e retorna simulado + questões")
    @PostMapping("/simulados/adaptativo")
    public ResponseEntity<SimuladoComQuestoesDTO> iniciarAdaptativo(@RequestBody StartAdaptativoDTO dto,
                                                                    HttpServletRequest req) {
        return ResponseEntity.ok(service.iniciarAdaptativo(req, dto));
    }

    // Iniciar simulado original (gera 44 questões em 1 chamada)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Inicia simulado original e retorna simulado + questões")
    @PostMapping("/simulados/original")
    public ResponseEntity<SimuladoComQuestoesDTO> iniciarOriginal(@RequestBody StartOriginalDTO dto,
                                                                jakarta.servlet.http.HttpServletRequest req) {
        return ResponseEntity.ok(service.iniciarOriginal(req, dto));
    }


    // Finalizar simulado: calcula perfis e fecha
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/simulados/{id}/finalizar")
    public ResponseEntity<SimuladoDTO> finalizar(@PathVariable String id, HttpServletRequest req) {
        return ResponseEntity.ok(service.finalizar(id, req));
    }

    // Listagens por usuário
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/simulados/by-usuario/{userId}")
    public ResponseEntity<List<SimuladoDTO>> listarPorUsuario(@PathVariable String userId) {
        return ResponseEntity.ok(service.listarPorUsuario(userId));
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/simulados/by-usuario/{userId}/ultimo")
    public ResponseEntity<SimuladoDTO> ultimoPorUsuario(@PathVariable String userId) {
        return ResponseEntity.ok(service.ultimoPorUsuario(userId));
    }
}