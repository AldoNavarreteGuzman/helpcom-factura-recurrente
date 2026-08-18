package cl.helpcom.facturacion.facturacion.calculo;

import java.math.BigDecimal;

/**
 * Resultado del cálculo, siempre en pesos chilenos enteros (escala 0).
 */
public record ResultadoCalculo(BigDecimal netoClp, BigDecimal ivaClp, BigDecimal totalClp) {
}
