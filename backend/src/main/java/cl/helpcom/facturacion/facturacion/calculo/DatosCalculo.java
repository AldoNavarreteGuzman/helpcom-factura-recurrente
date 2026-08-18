package cl.helpcom.facturacion.facturacion.calculo;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import java.math.BigDecimal;

/**
 * Todo lo que {@link CalculadoraFacturacion} necesita para calcular un ítem de facturación.
 * {@code acuerdo} es {@code null} cuando no hay acuerdo de precio vigente. {@code valorUf}
 * es {@code null} solo cuando ninguna rama del cálculo requiere UF; si el cálculo sí la
 * requiere y viene null, la calculadora lanza {@link IllegalArgumentException}.
 */
public record DatosCalculo(
    BigDecimal precioBaseNeto,
    Moneda monedaPrecio,
    AcuerdoAplicado acuerdo,
    BigDecimal valorUf,
    BigDecimal tasaIva
) {
}
