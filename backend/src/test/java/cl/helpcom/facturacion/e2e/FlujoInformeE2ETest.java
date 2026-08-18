package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-9 (docs/qa.md, arquitectura-tecnica.md §11): siembra una mezcla de propuestas en los
 * cuatro estados dentro de un mismo período, más una propuesta CSV en otro período, y verifica
 * que el informe suma solo lo facturable (excluye {@code PENDIENTE_UF} y {@code ANULADA}), el
 * desglose por estado y por cliente es exacto, y los filtros (cliente, estado, origen, rango
 * de períodos) acotan correctamente.
 */
@Tag("e2e")
class FlujoInformeE2ETest extends SoporteE2E {

    @Test
    void elInformeDeberiaSumarSoloLoFacturableYFiltrarCorrectamente() throws Exception {
        Long clienteAId = crearCliente(rutDePrueba(), "Cliente Informe A SpA");
        String rutClienteA = obtenerRutDelCliente(clienteAId);
        Long clienteBId = crearCliente(rutDePrueba(), "Cliente Informe B Ltda.");

        // Cliente A: una propuesta PENDIENTE (200.000) y otra que se factura (150.000).
        crearProyecto(clienteAId, "200000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        crearProyecto(clienteAId, "150000", "CLP", "MENSUAL", 6, LocalDate.of(2026, 1, 1), null);
        // Cliente B: una PENDIENTE_UF (sin UF sembrada) y otra que se anula.
        crearProyecto(clienteBId, "80.0000", "UF", "MENSUAL", 7, LocalDate.of(2026, 1, 1), null);
        crearProyecto(clienteBId, "100000", "CLP", "MENSUAL", 8, LocalDate.of(2026, 1, 1), null);

        ejecutarCiclo(2026, 2);

        List<PropuestaFacturacionRespuestaDto> propuestasFeb = listarPropuestas(2026, 2, null, null, null);
        assertThat(propuestasFeb).hasSize(4);

        Long propuestaFacturableAId = idPorMontoYCliente(propuestasFeb, clienteAId, "150000");
        Long propuestaAnuladaId = idPorMontoYCliente(propuestasFeb, clienteBId, "100000");

        crearFactura("F-INFORME-001", LocalDate.of(2026, 3, 1), List.of(propuestaFacturableAId));
        anular(propuestaAnuladaId);

        // Además, una propuesta CSV en OTRO período (marzo), para probar origen y rango.
        String csv = "rut_cliente;descripcion;periodo;fecha_facturacion;moneda;monto_neto\n"
            + rutClienteA + ";Servicio CSV marzo;2026-03;10-03-2026;CLP;500000\n";
        confirmarCsv(csv.getBytes(StandardCharsets.UTF_8));

        // --- Sin filtros (período exacto): la mezcla completa ---
        InformeFacturacionRespuestaDto informeFeb = obtenerInforme("?periodoAnio=2026&periodoMes=2");
        assertThat(informeFeb.resumen().cantidadTotal()).isEqualTo(4);
        Map<EstadoPropuesta, Long> porEstado = informeFeb.resumen().cantidadPorEstado();
        assertThat(porEstado.get(EstadoPropuesta.PENDIENTE)).isEqualTo(1);
        assertThat(porEstado.get(EstadoPropuesta.FACTURADA)).isEqualTo(1);
        assertThat(porEstado.get(EstadoPropuesta.PENDIENTE_UF)).isEqualTo(1);
        assertThat(porEstado.get(EstadoPropuesta.ANULADA)).isEqualTo(1);
        assertThat(informeFeb.resumen().cantidadPendienteUf()).isEqualTo(1);
        // Solo PENDIENTE (200.000) + FACTURADA (150.000): neto 350.000, iva 66.500, total 416.500.
        assertThat(informeFeb.resumen().netoClp()).isEqualByComparingTo("350000");
        assertThat(informeFeb.resumen().ivaClp()).isEqualByComparingTo("66500");
        assertThat(informeFeb.resumen().totalClp()).isEqualByComparingTo("416500");
        // porCliente: solo A tiene propuestas facturables en el período; B no aparece.
        assertThat(informeFeb.resumen().porCliente()).hasSize(1);
        assertThat(informeFeb.resumen().porCliente().get(0).clienteId()).isEqualTo(clienteAId);
        assertThat(informeFeb.resumen().porCliente().get(0).cantidad()).isEqualTo(2);
        assertThat(informeFeb.resumen().porCliente().get(0).totalClp()).isEqualByComparingTo("416500");

        // --- Filtro por estado ---
        InformeFacturacionRespuestaDto informeFacturadas =
            obtenerInforme("?periodoAnio=2026&periodoMes=2&estados=FACTURADA");
        assertThat(informeFacturadas.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(informeFacturadas.resumen().totalClp()).isEqualByComparingTo("178500"); // 150.000 + 19%

        // --- Filtro por cliente: B solo tiene propuestas NO facturables ---
        InformeFacturacionRespuestaDto informeClienteB =
            obtenerInforme("?periodoAnio=2026&periodoMes=2&clienteId=" + clienteBId);
        assertThat(informeClienteB.resumen().cantidadTotal()).isEqualTo(2);
        assertThat(informeClienteB.resumen().totalClp()).isEqualByComparingTo("0");
        assertThat(informeClienteB.resumen().porCliente()).isEmpty();

        // --- Filtro por rango de períodos + origen ---
        InformeFacturacionRespuestaDto informeCsvEnRango =
            obtenerInforme("?anioMesDesde=202602&anioMesHasta=202603&origen=CSV");
        assertThat(informeCsvEnRango.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(informeCsvEnRango.resumen().totalClp()).isEqualByComparingTo("595000"); // 500.000 + 19%
        assertThat(informeCsvEnRango.detalle().contenido().get(0).periodoMes()).isEqualTo(3);

        InformeFacturacionRespuestaDto informeCicloEnRango =
            obtenerInforme("?anioMesDesde=202602&anioMesHasta=202603&origen=CICLO");
        assertThat(informeCicloEnRango.resumen().cantidadTotal()).isEqualTo(4);
    }

    private String obtenerRutDelCliente(Long clienteId) throws Exception {
        String respuesta = mockMvc.perform(get("/api/v1/clientes/{id}", clienteId).with(administrador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("rut").asText();
    }

    private Long idPorMontoYCliente(List<PropuestaFacturacionRespuestaDto> propuestas, Long clienteId, String neto) {
        return propuestas.stream()
            .filter(p -> p.clienteId().equals(clienteId) && p.netoClp().compareTo(new BigDecimal(neto)) == 0)
            .findFirst()
            .orElseThrow(() -> new AssertionError("No se encontró la propuesta cliente=" + clienteId + " neto=" + neto))
            .id();
    }

    private void anular(Long propuestaId) throws Exception {
        mockMvc.perform(patch("/api/v1/propuestas/{id}/anular", propuestaId).with(administrador()))
            .andExpect(status().isNoContent());
    }
}
