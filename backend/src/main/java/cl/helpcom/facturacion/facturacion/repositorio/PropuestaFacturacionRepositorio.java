package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PropuestaFacturacionRepositorio
    extends JpaRepository<PropuestaFacturacion, Long>, JpaSpecificationExecutor<PropuestaFacturacion> {

    /**
     * Base de la idempotencia del ciclo (además del índice único parcial {@code
     * uq_prop_ciclo_periodo} como red de seguridad ante condiciones de carrera).
     */
    boolean existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
        Long proyectoId, Short periodoAnio, Short periodoMes, OrigenPropuesta origen);

    Optional<PropuestaFacturacion> findByIdAndEmpresaId(Long id, Long empresaId);
}
