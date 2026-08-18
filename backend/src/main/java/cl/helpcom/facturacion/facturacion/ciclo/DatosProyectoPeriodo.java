package cl.helpcom.facturacion.facturacion.ciclo;

import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import java.time.LocalDate;

/**
 * Los datos del proyecto que {@link ResolutorPeriodo} necesita, desacoplados de la entidad
 * JPA para que la resolución de períodos sea lógica pura, testeable sin base de datos.
 */
public record DatosProyectoPeriodo(
    Periodicidad periodicidad,
    int diaFacturacion,
    LocalDate fechaInicio,
    LocalDate fechaTermino,
    boolean activo
) {
}
