package cl.helpcom.facturacion.facturacion.dto;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PropuestaFacturacionRespuestaDto(
    Long id,
    Long clienteId,
    String clienteRazonSocial,
    Long proyectoId,
    String proyectoNombre,
    OrigenPropuesta origen,
    int periodoAnio,
    int periodoMes,
    LocalDate fechaFacturacion,
    String descripcion,
    Moneda monedaOrigen,
    BigDecimal precioBaseNeto,
    TipoAcuerdo acuerdoTipo,
    BigDecimal acuerdoValor,
    Moneda acuerdoMoneda,
    BigDecimal valorUf,
    LocalDate fechaValorUf,
    BigDecimal netoClp,
    BigDecimal tasaIva,
    BigDecimal ivaClp,
    BigDecimal totalClp,
    EstadoPropuesta estado,
    String numeroFactura,
    LocalDate fechaFactura
) {
}
