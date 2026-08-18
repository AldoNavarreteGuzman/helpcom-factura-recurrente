package cl.helpcom.facturacion.informes.dto;

import java.math.BigDecimal;

/** Subtotal de "lo que se va a facturar" (ver {@link InformeFacturacionResumenDto}) por cliente. */
public record InformeFacturacionClienteResumenDto(
    Long clienteId,
    String clienteRazonSocial,
    long cantidad,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp
) {
}
