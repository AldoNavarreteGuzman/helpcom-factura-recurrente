package cl.helpcom.facturacion.proyectos.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProyectoSolicitudDto(
    @NotNull Long clienteId,
    Long tipoServicioId,
    @Size(max = 40) String codigo,
    @NotBlank @Size(max = 200) String nombre,
    @Size(max = 500) String descripcion,
    @NotNull @PositiveOrZero BigDecimal precioBaseNeto,
    @NotNull Moneda monedaPrecio,
    @NotNull Periodicidad periodicidad,
    @NotNull @Min(1) @Max(31) Integer diaFacturacion,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaTermino,
    @NotNull Boolean activo
) {
}
