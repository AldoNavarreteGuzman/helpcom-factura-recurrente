package cl.helpcom.facturacion.facturacion.calculo;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Motor de cálculo de la facturación: implementa EXACTAMENTE la tabla de decisión de
 * modelo-de-datos.md §5. Es la ÚNICA fuente de verdad del cálculo — el ciclo de
 * facturación y la importación CSV DEBEN llamar a {@link #calcular(DatosCalculo)} en vez de
 * reimplementar esta matemática, para que ambos caminos calculen siempre igual.
 *
 * <p>Componente puro y sin estado: no toca base de datos, Redis, la API de UF ni
 * configuración. Todo lo que necesita llega por {@link DatosCalculo}. La resolución del
 * acuerdo vigente y la obtención del valor UF son responsabilidad del llamador; esta clase
 * no decide nada de eso, solo calcula con los datos ya resueltos.
 */
@Component
public class CalculadoraFacturacion {

    private static final int ESCALA_CLP = 0;
    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * Indica si el cálculo de estos datos necesita el valor UF, para que el llamador (el
     * ciclo de facturación) decida si debe resolverlo — p. ej. vía {@code ServicioUf} —
     * ANTES de invocar {@link #calcular}, y así evitar una consulta de UF innecesaria (o un
     * {@code ValorUfNoDisponibleException} evitable) cuando el cálculo es puramente en CLP.
     * Debe reflejar EXACTAMENTE las mismas ramas que {@link #calcular}.
     */
    public boolean requiereUf(Moneda monedaPrecio, AcuerdoAplicado acuerdo) {
        if (acuerdo == null) {
            return monedaPrecio == Moneda.UF;
        }
        return switch (acuerdo.tipo()) {
            case DESCUENTO_PORCENTAJE -> monedaPrecio == Moneda.UF;
            case DESCUENTO_MONTO -> monedaPrecio == Moneda.UF || acuerdo.moneda() == Moneda.UF;
            case PRECIO_PACTADO -> acuerdo.moneda() == Moneda.UF;
        };
    }

    public ResultadoCalculo calcular(DatosCalculo datos) {
        validarAcuerdo(datos.acuerdo());

        BigDecimal netoClp = redondear(calcularNeto(datos)).max(BigDecimal.ZERO);
        BigDecimal ivaClp = redondear(netoClp.multiply(datos.tasaIva()));
        BigDecimal totalClp = redondear(netoClp.add(ivaClp));

        return new ResultadoCalculo(netoClp, ivaClp, totalClp);
    }

    private BigDecimal calcularNeto(DatosCalculo datos) {
        AcuerdoAplicado acuerdo = datos.acuerdo();
        if (acuerdo == null) {
            return calcularSinAcuerdo(datos);
        }
        return switch (acuerdo.tipo()) {
            case DESCUENTO_PORCENTAJE -> calcularDescuentoPorcentaje(datos);
            case DESCUENTO_MONTO -> calcularDescuentoMonto(datos);
            case PRECIO_PACTADO -> calcularPrecioPactado(datos);
        };
    }

    private BigDecimal calcularSinAcuerdo(DatosCalculo datos) {
        if (datos.monedaPrecio() == Moneda.UF) {
            return datos.precioBaseNeto().multiply(requerirUf(datos));
        }
        return datos.precioBaseNeto();
    }

    private BigDecimal calcularDescuentoPorcentaje(DatosCalculo datos) {
        // El ajuste en UF (porcentaje) se aplica ANTES de convertir a CLP.
        BigDecimal factor = BigDecimal.ONE.subtract(datos.acuerdo().valor().divide(CIEN));
        BigDecimal baseConDescuento = datos.precioBaseNeto().multiply(factor);

        if (datos.monedaPrecio() == Moneda.UF) {
            return baseConDescuento.multiply(requerirUf(datos));
        }
        return baseConDescuento;
    }

    private BigDecimal calcularDescuentoMonto(DatosCalculo datos) {
        BigDecimal base = datos.precioBaseNeto();
        BigDecimal valor = datos.acuerdo().valor();
        Moneda monedaDescuento = datos.acuerdo().moneda();

        if (datos.monedaPrecio() == Moneda.UF) {
            BigDecimal uf = requerirUf(datos);
            if (monedaDescuento == Moneda.UF) {
                // Ajuste en UF: se resta en UF, LUEGO se convierte a CLP.
                return base.subtract(valor).multiply(uf);
            }
            // Ajuste en CLP: se convierte la base a CLP, LUEGO se resta.
            return redondear(base.multiply(uf)).subtract(valor);
        }

        // Base en CLP.
        if (monedaDescuento == Moneda.CLP) {
            return base.subtract(valor);
        }
        // Ajuste en UF sobre una base en CLP: se convierte el descuento a CLP primero.
        return base.subtract(redondear(valor.multiply(requerirUf(datos))));
    }

    private BigDecimal calcularPrecioPactado(DatosCalculo datos) {
        // El precio pactado REEMPLAZA la base; la moneda relevante es la del acuerdo.
        BigDecimal valor = datos.acuerdo().valor();
        if (datos.acuerdo().moneda() == Moneda.UF) {
            return valor.multiply(requerirUf(datos));
        }
        return valor;
    }

    private BigDecimal requerirUf(DatosCalculo datos) {
        if (datos.valorUf() == null) {
            throw new IllegalArgumentException(
                "Este cálculo requiere el valor de la UF, pero DatosCalculo.valorUf es null. "
                    + "La no disponibilidad real de la UF debe resolverse antes de invocar la "
                    + "calculadora (estado PENDIENTE_UF); esta clase asume que ya está resuelta.");
        }
        return datos.valorUf();
    }

    private void validarAcuerdo(AcuerdoAplicado acuerdo) {
        if (acuerdo == null) {
            return;
        }
        boolean monedaDebeSerNula = switch (acuerdo.tipo()) {
            case DESCUENTO_PORCENTAJE -> true;
            case DESCUENTO_MONTO, PRECIO_PACTADO -> false;
        };
        boolean monedaEsNula = acuerdo.moneda() == null;
        if (monedaDebeSerNula != monedaEsNula) {
            throw new IllegalArgumentException(
                "Moneda incoherente con el tipo de acuerdo: tipo=" + acuerdo.tipo()
                    + ", moneda=" + acuerdo.moneda()
                    + ". DESCUENTO_PORCENTAJE requiere moneda null; los demás tipos la requieren no nula.");
        }
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor.setScale(ESCALA_CLP, RoundingMode.HALF_UP);
    }
}
