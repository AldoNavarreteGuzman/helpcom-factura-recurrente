package cl.helpcom.facturacion.clientes.dto;

public record ClienteRespuestaDto(
    Long id,
    String rut,
    String razonSocial,
    String nombreFantasia,
    String giro,
    String email,
    String telefono,
    String direccion,
    boolean activo
) {
}
