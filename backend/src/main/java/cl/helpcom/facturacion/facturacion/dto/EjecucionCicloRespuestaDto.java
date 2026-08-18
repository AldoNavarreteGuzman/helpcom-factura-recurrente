package cl.helpcom.facturacion.facturacion.dto;

import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import java.time.OffsetDateTime;

public record EjecucionCicloRespuestaDto(
    Long id,
    int periodoAnio,
    int periodoMes,
    OffsetDateTime ejecutadoEn,
    DisparoCiclo disparo,
    int cantidadGeneradas,
    int cantidadPendientesUf,
    EstadoEjecucionCiclo estado,
    String observacion,
    String ejecutadoPor
) {
}
