package cl.helpcom.facturacion.facturacion.calculo;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import java.math.BigDecimal;

/**
 * Acuerdo de precio ya resuelto (vigente en la fecha de facturación), tal como lo entrega
 * el llamador a {@link CalculadoraFacturacion}. {@code moneda} es {@code null} cuando
 * {@code tipo == DESCUENTO_PORCENTAJE}; obligatoria (UF o CLP) en los demás casos.
 */
public record AcuerdoAplicado(TipoAcuerdo tipo, BigDecimal valor, Moneda moneda) {
}
