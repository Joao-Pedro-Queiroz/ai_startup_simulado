package ai.startup.simulado.simulado;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Document("simulados")
@org.springframework.data.mongodb.core.index.CompoundIndexes({
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "usuario_data_idx", def = "{'idUsuario': 1, 'data': -1}"),
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "usuario_status_idx", def = "{'idUsuario': 1, 'status': 1}")
})
public class Simulado {
    @Id
    private String id;

    private String idUsuario;        // id_aluno
    private String tipo;             // ADAPTATIVO | ORIGINAL
    private LocalDateTime data;      // data e hora
    private String status;           // ABERTO | FINALIZADO
    private Integer faturaWins;      // custo em "wins"
}