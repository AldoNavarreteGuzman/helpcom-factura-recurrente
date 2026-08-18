package cl.helpcom.facturacion.informes.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InformeFacturacionDetalleDto(
    Long id,
    Long clienteId,
    String clienteRazonSocial,
    Long proyectoId,
    String proyectoNombre,
    String descripcion,
    int periodoAnio,
    int periodoMes,
    LocalDate fechaFacturacion,
    Moneda monedaOrigen,
    BigDecimal valorUf,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp,
    EstadoPropuesta estado,
    OrigenPropuesta origen,
    Long facturaId,
    String numeroFactura,
    LocalDate fechaFactura
) {
}
