package ai.startup.simulado.usuario;

import java.time.LocalDate;

public record UsuarioDTO(
        String id,
        String nome,
        String sobrenome,
        String cpf,
        String telefone,
        LocalDate nascimento,
        String email,
        Long wins,
        Long streaks,
        Long xp,
        String permissao
) {}
