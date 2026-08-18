package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.Factura;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FacturaRepositorio extends JpaRepository<Factura, Long>, JpaSpecificationExecutor<Factura> {

    Optional<Factura> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByEmpresaIdAndNumeroFactura(Long empresaId, String numeroFactura);
}
