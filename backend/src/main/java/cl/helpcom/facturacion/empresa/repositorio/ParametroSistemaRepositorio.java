package cl.helpcom.facturacion.empresa.repositorio;

import cl.helpcom.facturacion.empresa.dominio.ParametroSistema;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroSistemaRepositorio extends JpaRepository<ParametroSistema, Long> {

    Optional<ParametroSistema> findByEmpresaIdAndClave(Long empresaId, String clave);
}
