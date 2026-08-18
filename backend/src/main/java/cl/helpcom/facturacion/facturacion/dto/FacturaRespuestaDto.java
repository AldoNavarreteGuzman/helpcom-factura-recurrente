package cl.helpcom.facturacion.facturacion.dto;

import java.time.LocalDate;
import java.util.List;

public record FacturaRespuestaDto(
    Long id,
    String numeroFactura,
    LocalDate fechaFactura,
    String observacion,
    Long clienteId,
    String clienteRazonSocial,
    boolean tienePdf,
    String nombreArchivoPdf,
    List<PropuestaResumenDto> propuestas
) {
}
