package cl.helpcom.facturacion.clientes.repositorio;

import cl.helpcom.facturacion.clientes.dominio.TipoServicio;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoServicioRepositorio extends JpaRepository<TipoServicio, Long> {

    Optional<TipoServicio> findByIdAndEmpresaId(Long id, Long empresaId);

    Page<TipoServicio> findByEmpresaId(Long empresaId, Pageable pageable);

    Page<TipoServicio> findByEmpresaIdAndActivo(Long empresaId, boolean activo, Pageable pageable);

    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    boolean existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(Long empresaId, String nombre, Long id);
}
