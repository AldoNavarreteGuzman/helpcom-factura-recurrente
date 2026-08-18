package cl.helpcom.facturacion.facturacion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Ambos campos son opcionales: si se omiten, el servicio usa el mes actual (zona
 * America/Santiago).
 */
public record EjecutarCicloSolicitudDto(
    @Min(2000) @Max(3000) Integer anio,
    @Min(1) @Max(12) Integer mes
) {
}
