package cl.helpcom.facturacion.clientes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TipoServicioSolicitudDto(
    @NotBlank @Size(max = 120) String nombre,
    @NotNull Boolean activo
) {
}
