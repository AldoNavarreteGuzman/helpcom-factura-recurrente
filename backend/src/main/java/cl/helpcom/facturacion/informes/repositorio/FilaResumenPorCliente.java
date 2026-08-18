package cl.helpcom.facturacion.informes.repositorio;

import java.math.BigDecimal;

/** Una fila del resultado de {@code GROUP BY cliente} (proyección del desglose por cliente). */
public record FilaResumenPorCliente(
    Long clienteId,
    String clienteRazonSocial,
    Long cantidad,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp
) {
}
