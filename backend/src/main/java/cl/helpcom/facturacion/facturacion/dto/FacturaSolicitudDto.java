package cl.helpcom.facturacion.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record FacturaSolicitudDto(
    @NotBlank @Size(max = 40) String numeroFactura,
    @NotNull LocalDate fechaFactura,
    @Size(max = 300) String observacion,
    @NotEmpty List<Long> propuestaIds
) {
}
