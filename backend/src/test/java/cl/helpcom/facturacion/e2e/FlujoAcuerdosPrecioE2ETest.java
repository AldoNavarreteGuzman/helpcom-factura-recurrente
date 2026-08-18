package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-2 (docs/qa.md): un caso end-to-end (vía ciclo real, no la calculadora en aislado) por
 * cada rama de acuerdo de precio de modelo-de-datos.md §5 (filas 3 a 10 de la tabla — las
 * filas 1/2, "sin acuerdo", ya las cubre {@link FlujoCaminoFelizE2ETest}). Cada método crea su
 * propio cliente/proyecto/acuerdo para no tener que lidiar con traslapes entre casos.
 */
@Tag("e2e")
class FlujoAcuerdosPrecioE2ETest extends SoporteE2E {

    @Test
    void fila3_descuentoPorcentaje_baseUf() throws Exception {
        // 12 UF, descuento 10%, UF=40.000 → neto = 12 × 0,90 × 40.000 = 432.000 (ejemplo A, §5.1).
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 3 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "12.0000", "UF", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_PORCENTAJE, "10.0000", null,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        sembrarUf(LocalDate.of(2026, 2, 5), "40000.0000");

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "432000", "82080", "514080");
        assertThat(propuesta.acuerdoTipo()).isEqualTo(TipoAcuerdo.DESCUENTO_PORCENTAJE);
        assertThat(propuesta.acuerdoValor()).isEqualByComparingTo("10.0000");
        assertThat(propuesta.acuerdoMoneda()).isNull();
    }

    @Test
    void fila4_descuentoPorcentaje_baseClp() throws Exception {
        // 1.000.000 CLP, descuento 15% → neto = 850.000. Sin UF: la base y el acuerdo son CLP.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 4 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "1000000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_PORCENTAJE, "15.0000", null,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "850000", "161500", "1011500");
        assertThat(propuesta.valorUf()).isNull();
    }

    @Test
    void fila5_descuentoMontoUf_baseUf() throws Exception {
        // 20 UF, descuento 5 UF, UF=35.000 → neto = (20-5) × 35.000 = 525.000.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 5 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "20.0000", "UF", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_MONTO, "5.0000", "UF",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        sembrarUf(LocalDate.of(2026, 2, 5), "35000.0000");

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "525000", "99750", "624750");
        assertThat(propuesta.acuerdoMoneda()).isEqualTo(Moneda.UF);
    }

    @Test
    void fila6_descuentoMontoClp_baseUf() throws Exception {
        // 12 UF, descuento 50.000 CLP, UF=40.000 → neto = 480.000 − 50.000 = 430.000 (ejemplo B, §5.1).
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 6 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "12.0000", "UF", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_MONTO, "50000", "CLP",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        sembrarUf(LocalDate.of(2026, 2, 5), "40000.0000");

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "430000", "81700", "511700");
    }

    @Test
    void fila7_descuentoMontoClp_baseClp() throws Exception {
        // 1.000.000 CLP, descuento 200.000 CLP → neto = 800.000. Sin UF.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 7 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "1000000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_MONTO, "200000", "CLP",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "800000", "152000", "952000");
        assertThat(propuesta.valorUf()).isNull();
    }

    @Test
    void fila8_descuentoMontoUf_baseClp() throws Exception {
        // 1.000.000 CLP, descuento 10 UF, UF=38.000 → neto = 1.000.000 − round(10×38.000) = 620.000.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 8 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "1000000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_MONTO, "10.0000", "UF",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        sembrarUf(LocalDate.of(2026, 2, 5), "38000.0000");

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "620000", "117800", "737800");
    }

    @Test
    void fila9_precioPactadoUf() throws Exception {
        // Proyecto anual, contrato 15-ene-2026, 100 UF, pactado 90 UF los primeros 12 meses,
        // UF del 15-ene = 39.000 → neto = round(90×39.000) = 3.510.000 (ejemplo C, §5.1).
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 9 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "100.0000", "UF", "ANUAL", 15, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.PRECIO_PACTADO, "90.0000", "UF",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        sembrarUf(LocalDate.of(2026, 1, 15), "39000.0000");

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 1, clienteId);

        assertMonto(propuesta, "3510000", "666900", "4176900");
        assertThat(propuesta.acuerdoTipo()).isEqualTo(TipoAcuerdo.PRECIO_PACTADO);
    }

    @Test
    void fila10_precioPactadoClp() throws Exception {
        // Pactado 777.000 CLP → neto = 777.000. Sin UF.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Fila 10 SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "500000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearAcuerdo(proyecto.id(), TipoAcuerdo.PRECIO_PACTADO, "777000", "CLP",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        PropuestaFacturacionRespuestaDto propuesta = ejecutarCicloYObtenerUnicaPropuesta(2026, 2, clienteId);

        assertMonto(propuesta, "777000", "147630", "924630");
        assertThat(propuesta.valorUf()).isNull();
    }

    private PropuestaFacturacionRespuestaDto ejecutarCicloYObtenerUnicaPropuesta(int anio, int mes, Long clienteId)
        throws Exception {
        ResultadoCiclo resultado = ejecutarCiclo(anio, mes);
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);

        List<PropuestaFacturacionRespuestaDto> propuestas = listarPropuestas(anio, mes, clienteId, null, null);
        assertThat(propuestas).hasSize(1);
        return propuestas.get(0);
    }

    private void assertMonto(
        PropuestaFacturacionRespuestaDto propuesta, String netoEsperado, String ivaEsperado, String totalEsperado) {
        assertThat(propuesta.netoClp()).isEqualByComparingTo(netoEsperado);
        assertThat(propuesta.ivaClp()).isEqualByComparingTo(ivaEsperado);
        assertThat(propuesta.totalClp()).isEqualByComparingTo(totalEsperado);
    }
}
