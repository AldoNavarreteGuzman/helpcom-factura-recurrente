package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.EjecucionCiclo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EjecucionCicloRepositorio extends JpaRepository<EjecucionCiclo, Long> {

    Page<EjecucionCiclo> findByEmpresaIdOrderByEjecutadoEnDesc(Long empresaId, Pageable pageable);
}
