package cl.helpcom.facturacion.importacion.dto;

import java.util.List;

public record ImportacionPreviewRespuestaDto(
    ImportacionPreviewResumenDto resumen,
    List<ImportacionPreviewFilaDto> filas
) {
}
