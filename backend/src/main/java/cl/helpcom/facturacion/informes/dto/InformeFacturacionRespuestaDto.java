package cl.helpcom.facturacion.informes.dto;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;

public record InformeFacturacionRespuestaDto(
    InformeFacturacionResumenDto resumen,
    PaginaRespuestaDto<InformeFacturacionDetalleDto> detalle
) {
}
