package cl.helpcom.facturacion.importacion.dto;

import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import java.math.BigDecimal;
import java.util.List;

/**
 * Una fila de la previsualización: los datos tal como vinieron en el CSV, su estado y
 * mensajes de validación, y — para filas {@code OK}/{@code ADVERTENCIA} — el cálculo ya
 * resuelto (mismo camino que usará {@code confirmar}) para que el usuario vea qué se va a
 * facturar antes de confirmar. Los tres montos quedan {@code null} en filas {@code ERROR}.
 */
public record ImportacionPreviewFilaDto(
    int numeroFila,
    EstadoFilaCsv estado,
    List<String> mensajes,
    String rutCliente,
    String codigoProyecto,
    String descripcion,
    String periodo,
    String fechaFacturacion,
    String moneda,
    String montoNeto,
    String observacion,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp
) {
}
