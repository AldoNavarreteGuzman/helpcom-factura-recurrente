package cl.helpcom.facturacion.informes.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import java.math.BigDecimal;

/**
 * Una fila del resultado de {@code GROUP BY estado} (proyección de
 * {@link RepositorioResumenFacturacion#agregarPorEstado}). {@code cantidad} nunca es 0: solo
 * aparecen los estados con al menos una propuesta que cumple los filtros.
 */
public record FilaResumenPorEstado(
    EstadoPropuesta estado,
    Long cantidad,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp
) {
}
