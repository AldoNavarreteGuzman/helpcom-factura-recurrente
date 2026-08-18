package cl.helpcom.facturacion.facturacion.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CalculadoraFacturacionTest {

    private static final BigDecimal TASA_IVA = new BigDecimal("0.19");

    private final CalculadoraFacturacion calculadora = new CalculadoraFacturacion();

    // --- Las 10 filas de la tabla de decisión (modelo-de-datos.md §5) ---

    private static Stream<Arguments> filasTablaDecision() {
        return Stream.of(
            Arguments.of(
                "Sin acuerdo, base UF",
                new BigDecimal("10"), Moneda.UF, null, new BigDecimal("1000"),
                "10000", "1900", "11900"),
            Arguments.of(
                "Sin acuerdo, base CLP",
                new BigDecimal("500000"), Moneda.CLP, null, null,
                "500000", "95000", "595000"),
            Arguments.of(
                "Descuento %, base UF",
                new BigDecimal("10"), Moneda.UF,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("20"), null),
                new BigDecimal("1000"),
                "8000", "1520", "9520"),
            Arguments.of(
                "Descuento %, base CLP",
                new BigDecimal("500000"), Moneda.CLP,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null),
                null,
                "450000", "85500", "535500"),
            Arguments.of(
                "Descuento monto UF, base UF",
                new BigDecimal("10"), Moneda.UF,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("2"), Moneda.UF),
                new BigDecimal("1000"),
                "8000", "1520", "9520"),
            Arguments.of(
                "Descuento monto CLP, base UF",
                new BigDecimal("10"), Moneda.UF,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("5000"), Moneda.CLP),
                new BigDecimal("1000"),
                "5000", "950", "5950"),
            Arguments.of(
                "Descuento monto CLP, base CLP",
                new BigDecimal("500000"), Moneda.CLP,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("100000"), Moneda.CLP),
                null,
                "400000", "76000", "476000"),
            Arguments.of(
                "Descuento monto UF, base CLP",
                new BigDecimal("500000"), Moneda.CLP,
                new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("2"), Moneda.UF),
                new BigDecimal("1000"),
                "498000", "94620", "592620"),
            Arguments.of(
                "Precio pactado UF (la base y su moneda quedan reemplazadas)",
                new BigDecimal("999999"), Moneda.CLP,
                new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("8"), Moneda.UF),
                new BigDecimal("1000"),
                "8000", "1520", "9520"),
            Arguments.of(
                "Precio pactado CLP (no requiere UF aunque la base sea UF)",
                new BigDecimal("999999"), Moneda.UF,
                new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("300000"), Moneda.CLP),
                null,
                "300000", "57000", "357000")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filasTablaDecision")
    void deberiaCalcularCadaFilaDeLaTablaDeDecision(
        String descripcion, BigDecimal precioBase, Moneda monedaPrecio, AcuerdoAplicado acuerdo,
        BigDecimal valorUf, String netoEsperado, String ivaEsperado, String totalEsperado) {

        ResultadoCalculo resultado = calculadora.calcular(
            new DatosCalculo(precioBase, monedaPrecio, acuerdo, valorUf, TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo(netoEsperado);
        assertThat(resultado.ivaClp()).isEqualByComparingTo(ivaEsperado);
        assertThat(resultado.totalClp()).isEqualByComparingTo(totalEsperado);
    }

    // --- Ejemplos exactos de modelo-de-datos.md §5.1 ---

    @Test
    void ejemploA_docenaUfConDescuentoPorcentual() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("12"), Moneda.UF,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null),
            new BigDecimal("40000"), TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("432000");
        assertThat(resultado.ivaClp()).isEqualByComparingTo("82080");
        assertThat(resultado.totalClp()).isEqualByComparingTo("514080");
    }

    @Test
    void ejemploB_docenaUfConDescuentoMontoClp() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("12"), Moneda.UF,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("50000"), Moneda.CLP),
            new BigDecimal("40000"), TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("430000");
        assertThat(resultado.ivaClp()).isEqualByComparingTo("81700");
        assertThat(resultado.totalClp()).isEqualByComparingTo("511700");
    }

    @Test
    void ejemploC_precioPactadoEnUf() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("100"), Moneda.UF,
            new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("90"), Moneda.UF),
            new BigDecimal("39000"), TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("3510000");
        assertThat(resultado.ivaClp()).isEqualByComparingTo("666900");
        assertThat(resultado.totalClp()).isEqualByComparingTo("4176900");
    }

    // --- Piso en cero ---

    @Test
    void deberiaAplicarPisoEnCeroCuandoElDescuentoSuperaLaBase() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("100000"), Moneda.CLP,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("500000"), Moneda.CLP),
            null, TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("0");
        assertThat(resultado.ivaClp()).isEqualByComparingTo("0");
        assertThat(resultado.totalClp()).isEqualByComparingTo("0");
    }

    // --- Redondeo HALF_UP exactamente en el borde .5 ---

    @Test
    void deberiaRedondearHaciaArribaEnElBorde5AlConvertirDeUfAClp() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            BigDecimal.ONE, Moneda.UF, null, new BigDecimal("40000.5"), TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("40001");
    }

    @Test
    void deberiaRedondearHaciaArribaEnElBorde5AlCalcularElIva() {
        // 50 * 0.19 = 9.5 exacto -> HALF_UP debe subir a 10.
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("50"), Moneda.CLP, null, null, TASA_IVA));

        assertThat(resultado.netoClp()).isEqualByComparingTo("50");
        assertThat(resultado.ivaClp()).isEqualByComparingTo("10");
        assertThat(resultado.totalClp()).isEqualByComparingTo("60");
    }

    // --- Escala de salida ---

    @Test
    void losMontosDeSalidaDebenTenerEscalaCero() {
        ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
            new BigDecimal("12345.6789"), Moneda.CLP, null, null, TASA_IVA));

        assertThat(resultado.netoClp().scale()).isZero();
        assertThat(resultado.ivaClp().scale()).isZero();
        assertThat(resultado.totalClp().scale()).isZero();
    }

    // --- Contrato de errores ---

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoFaltaElValorUfSinAcuerdo() {
        DatosCalculo datos = new DatosCalculo(new BigDecimal("10"), Moneda.UF, null, null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoFaltaElValorUfConDescuentoPorcentual() {
        DatosCalculo datos = new DatosCalculo(
            new BigDecimal("10"), Moneda.UF,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null),
            null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoFaltaElValorUfConPrecioPactadoEnUf() {
        DatosCalculo datos = new DatosCalculo(
            new BigDecimal("10"), Moneda.CLP,
            new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("90"), Moneda.UF),
            null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoElDescuentoPorcentualTraeMonedaNoNula() {
        DatosCalculo datos = new DatosCalculo(
            new BigDecimal("10"), Moneda.CLP,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), Moneda.CLP),
            null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoElDescuentoMontoTraeMonedaNula() {
        DatosCalculo datos = new DatosCalculo(
            new BigDecimal("10"), Moneda.CLP,
            new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, new BigDecimal("10"), null),
            null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberiaLanzarIllegalArgumentExceptionCuandoElPrecioPactadoTraeMonedaNula() {
        DatosCalculo datos = new DatosCalculo(
            new BigDecimal("10"), Moneda.CLP,
            new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("10"), null),
            null, TASA_IVA);

        assertThatThrownBy(() -> calculadora.calcular(datos)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- requiereUf: debe reflejar exactamente las mismas ramas que calcular() ---

    @Test
    void requiereUf_sinAcuerdoDependeSoloDeLaMonedaDeLaBase() {
        assertThat(calculadora.requiereUf(Moneda.UF, null)).isTrue();
        assertThat(calculadora.requiereUf(Moneda.CLP, null)).isFalse();
    }

    @Test
    void requiereUf_descuentoMontoLoRequiereSiBaseOAcuerdoEstanEnUf() {
        AcuerdoAplicado descuentoUf = new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, BigDecimal.TEN, Moneda.UF);
        AcuerdoAplicado descuentoClp = new AcuerdoAplicado(TipoAcuerdo.DESCUENTO_MONTO, BigDecimal.TEN, Moneda.CLP);

        assertThat(calculadora.requiereUf(Moneda.CLP, descuentoUf)).isTrue();
        assertThat(calculadora.requiereUf(Moneda.UF, descuentoClp)).isTrue();
        assertThat(calculadora.requiereUf(Moneda.CLP, descuentoClp)).isFalse();
    }

    @Test
    void requiereUf_precioPactadoDependeSoloDeLaMonedaDelAcuerdoNoDeLaBase() {
        AcuerdoAplicado pactadoUf = new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, BigDecimal.TEN, Moneda.UF);
        AcuerdoAplicado pactadoClp = new AcuerdoAplicado(TipoAcuerdo.PRECIO_PACTADO, BigDecimal.TEN, Moneda.CLP);

        assertThat(calculadora.requiereUf(Moneda.CLP, pactadoUf)).isTrue();
        assertThat(calculadora.requiereUf(Moneda.UF, pactadoClp)).isFalse();
    }
}
