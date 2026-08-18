package cl.helpcom.facturacion.proyectos.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProyectoRespuestaDto(
    Long id,
    Long clienteId,
    String clienteRazonSocial,
    Long tipoServicioId,
    String tipoServicioNombre,
    String codigo,
    String nombre,
    String descripcion,
    BigDecimal precioBaseNeto,
    Moneda monedaPrecio,
    Periodicidad periodicidad,
    int diaFacturacion,
    LocalDate fechaInicio,
    LocalDate fechaTermino,
    boolean activo
) {
}
