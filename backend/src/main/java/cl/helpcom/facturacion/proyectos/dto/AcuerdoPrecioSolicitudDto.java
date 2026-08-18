package cl.helpcom.facturacion.proyectos.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Vigencia: se debe indicar exactamente una de las dos formas — {@code fechaTermino}
 * explícita, o {@code mesesPactados} (a partir del cual el servicio calcula fechaTermino).
 */
public record AcuerdoPrecioSolicitudDto(
    @NotNull TipoAcuerdo tipo,
    @NotNull BigDecimal valor,
    Moneda moneda,
    @NotNull LocalDate fechaInicio,
    LocalDate fechaTermino,
    @Min(1) Integer mesesPactados,
    @Size(max = 300) String observacion
) {
}
