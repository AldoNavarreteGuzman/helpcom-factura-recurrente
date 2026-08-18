package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-1 (docs/qa.md): el camino central del sistema de punta a punta — cliente, proyecto sin
 * acuerdo, ciclo, propuesta con snapshot exacto, factura, PDF y su reflejo en el informe.
 * Sirve de "control" sin acuerdo de precio; los ocho tipos/ramas de acuerdo viven en
 * {@link FlujoAcuerdosPrecioE2ETest}.
 */
@Tag("e2e")
class FlujoCaminoFelizE2ETest extends SoporteE2E {

    @Test
    void deberiaRecorrerClienteProyectoCicloFacturaPdfEInformeConMontosExactos() throws Exception {
        // 1) Sembrar UF conocida para el día de facturación.
        LocalDate fechaFacturacion = LocalDate.of(2026, 2, 5);
        sembrarUf(fechaFacturacion, "40000.0000");

        // 2) Cliente.
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Camino Feliz SpA");

        // 3) Proyecto: 12 UF mensual, día 5, sin acuerdo. Inicio en enero → primer cobro en
        // febrero (mensual no factura el mes de inicio).
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "12.0000", "UF", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);

        // 4) Ciclo de febrero 2026.
        ResultadoCiclo resultado = ejecutarCiclo(2026, 2);
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);
        assertThat(resultado.cantidadPendientesUf()).isZero();

        // 5) Snapshot exacto de la propuesta: neto = 12 × 40.000 = 480.000; iva = 91.200;
        // total = 571.200 (sin acuerdo, tabla §5 fila 1).
        List<PropuestaFacturacionRespuestaDto> propuestas =
            listarPropuestas(2026, 2, clienteId, null, OrigenPropuesta.CICLO);
        assertThat(propuestas).hasSize(1);
        PropuestaFacturacionRespuestaDto propuesta = propuestas.get(0);
        assertThat(propuesta.proyectoId()).isEqualTo(proyecto.id());
        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(propuesta.monedaOrigen().name()).isEqualTo("UF");
        assertThat(propuesta.precioBaseNeto()).isEqualByComparingTo("12.0000");
        assertThat(propuesta.valorUf()).isEqualByComparingTo("40000.0000");
        assertThat(propuesta.fechaValorUf()).isEqualTo(fechaFacturacion);
        assertThat(propuesta.tasaIva()).isEqualByComparingTo("0.19");
        assertThat(propuesta.netoClp()).isEqualByComparingTo("480000");
        assertThat(propuesta.ivaClp()).isEqualByComparingTo("91200");
        assertThat(propuesta.totalClp()).isEqualByComparingTo("571200");
        assertThat(propuesta.acuerdoTipo()).isNull();
        assertThat(propuesta.acuerdoValor()).isNull();
        assertThat(propuesta.acuerdoMoneda()).isNull();

        // 6) Asociar factura.
        FacturaRespuestaDto factura = crearFactura(
            "F-E2E-001", LocalDate.of(2026, 3, 1), List.of(propuesta.id()));
        assertThat(factura.clienteId()).isEqualTo(clienteId);
        assertThat(factura.tienePdf()).isFalse();
        assertThat(factura.propuestas()).hasSize(1);
        assertThat(factura.propuestas().get(0).estado()).isEqualTo(EstadoPropuesta.FACTURADA);
        assertThat(factura.propuestas().get(0).totalClp()).isEqualByComparingTo("571200");

        // 7) Subir PDF (almacén local, sin OCI).
        byte[] contenidoPdf = "%PDF-1.4 contenido de prueba E2E".getBytes(StandardCharsets.UTF_8);
        subirPdf(factura.id(), "respaldo-e2e.pdf", contenidoPdf);

        // 8) Descargar y verificar contenido/headers.
        mockMvc.perform(get("/api/v1/facturas/{id}/pdf", factura.id()).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("respaldo-e2e.pdf")))
            .andExpect(content().bytes(contenidoPdf));

        // 9) Informe del período: la propuesta FACTURADA aparece con su número de factura y
        // suma en los totales.
        InformeFacturacionRespuestaDto informe = obtenerInforme("?periodoAnio=2026&periodoMes=2");
        assertThat(informe.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(informe.resumen().cantidadPorEstado().get(EstadoPropuesta.FACTURADA)).isEqualTo(1);
        assertThat(informe.resumen().cantidadPendienteUf()).isZero();
        assertThat(informe.resumen().netoClp()).isEqualByComparingTo("480000");
        assertThat(informe.resumen().ivaClp()).isEqualByComparingTo("91200");
        assertThat(informe.resumen().totalClp()).isEqualByComparingTo("571200");
        assertThat(informe.detalle().contenido()).hasSize(1);
        assertThat(informe.detalle().contenido().get(0).numeroFactura()).isEqualTo("F-E2E-001");
        assertThat(informe.detalle().contenido().get(0).fechaFactura()).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
