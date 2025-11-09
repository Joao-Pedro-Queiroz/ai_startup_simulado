package ai.startup.simulado.simulado;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SimuladoRepository extends MongoRepository<Simulado, String> {
    List<Simulado> findByIdUsuario(String idUsuario, Sort sort);
    List<Simulado> findByIdUsuarioAndStatus(String idUsuario, String status);
    
    default Simulado findMaisRecente(String idUsuario) {
        var l = findByIdUsuario(idUsuario, Sort.by(Sort.Direction.DESC, "data"));
        return l.isEmpty() ? null : l.get(0);
    }
}