package cl.helpcom.facturacion.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClienteSolicitudDto(
    @NotBlank String rut,
    @NotBlank @Size(max = 200) String razonSocial,
    @Size(max = 200) String nombreFantasia,
    @Size(max = 150) String giro,
    @Email @Size(max = 150) String email,
    @Size(max = 30) String telefono,
    @Size(max = 250) String direccion,
    @NotNull Boolean activo
) {
}
