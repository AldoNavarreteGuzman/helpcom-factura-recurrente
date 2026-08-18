package cl.helpcom.facturacion.proyectos.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AcuerdoPrecioRespuestaDto(
    Long id,
    Long proyectoId,
    TipoAcuerdo tipo,
    BigDecimal valor,
    Moneda moneda,
    LocalDate fechaInicio,
    LocalDate fechaTermino,
    Integer mesesPactados,
    String observacion
) {
}
