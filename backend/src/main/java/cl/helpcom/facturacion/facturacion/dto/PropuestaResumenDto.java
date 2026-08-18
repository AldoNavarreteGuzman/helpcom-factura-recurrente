package cl.helpcom.facturacion.facturacion.dto;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PropuestaResumenDto(
    Long id,
    int periodoAnio,
    int periodoMes,
    LocalDate fechaFacturacion,
    String descripcion,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp,
    EstadoPropuesta estado
) {
}
