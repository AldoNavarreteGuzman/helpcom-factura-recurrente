package cl.helpcom.facturacion.facturacion.ciclo;

import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;

/**
 * Resumen de una corrida del ciclo, tanto el retorno de
 * {@link ServicioCicloFacturacion#ejecutarCiclo} como el cuerpo de respuesta del endpoint
 * manual. {@code ejecucionId} es {@code null} cuando la corrida se omitió por completo
 * porque ya había otra ejecución en curso para el mismo período (lock de Redis tomado).
 */
public record ResultadoCiclo(
    Long ejecucionId,
    int anio,
    int mes,
    DisparoCiclo disparo,
    EstadoEjecucionCiclo estado,
    int cantidadGeneradas,
    int cantidadPendientesUf,
    String observacion
) {
}
