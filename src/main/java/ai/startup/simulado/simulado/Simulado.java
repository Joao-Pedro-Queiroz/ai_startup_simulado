package ai.startup.simulado.simulado;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Document("simulados")
public class Simulado {
    @Id
    private String id;

    private String idUsuario;        // id_aluno
    private String tipo;             // ADAPTATIVO | ORIGINAL
    private LocalDateTime data;      // data e hora
    private String status;           // ABERTO | FINALIZADO
    private Integer faturaWins;      // custo em "wins"
}