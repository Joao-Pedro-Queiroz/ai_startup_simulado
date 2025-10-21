package ai.startup.simulado.usuario;

import java.time.LocalDate;

public record UsuarioUpdateDTO(
        String nome,
        String sobrenome,
        String telefone,
        LocalDate nascimento,
        String email,
        String cpf,
        String senha,
        Long wins,
        Long streaks,
        Long xp,
        String permissao
) {}
