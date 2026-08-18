package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * E2E-3 (docs/qa.md): un proyecto en UF cuyo día de facturación NO tiene UF sembrada. Cubre
 * el camino "resuelto" de deuda-tecnica.md/arquitectura-tecnica.md §9: nunca se inventan
 * cifras, la propuesta queda {@code PENDIENTE_UF} con montos en 0, el ciclo avanza igual
 * (estado {@code CON_ADVERTENCIAS}, no aborta), el informe la excluye de los totales pero la
 * cuenta aparte, y no puede facturarse.
 */
@Tag("e2e")
class FlujoPendienteUfE2ETest extends SoporteE2E {

    @Test
    void unProyectoSinUfSembradaQuedaPendienteUfYElCicloContinuaConAdvertencias() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Sin UF SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "10.0000", "UF", "MENSUAL", 10, LocalDate.of(2026, 1, 1), null);
        // Deliberadamente NO se siembra valor_uf para 2026-02-10: ni caché, ni persistencia,
        // ni fuente externa (fuenteUf mockeada a Optional.empty() por SoporteE2E).

        ResultadoCiclo resultado = ejecutarCiclo(2026, 2);

        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.CON_ADVERTENCIAS);
        // "Generadas" cuenta toda propuesta efectivamente persistida en esta corrida — incluye
        // las PENDIENTE_UF (ContadoresCiclo.conPendienteUf incrementa ambos contadores); no es
        // un contador exclusivo de "con monto calculado".
        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);
        assertThat(resultado.cantidadPendientesUf()).isEqualTo(1);

        // La ejecución de ciclo (trazabilidad) refleja lo mismo.
        List<EjecucionCicloRespuestaDto> ejecuciones = listarEjecucionesCiclo();
        assertThat(ejecuciones).hasSize(1);
        assertThat(ejecuciones.get(0).estado()).isEqualTo(EstadoEjecucionCiclo.CON_ADVERTENCIAS);
        assertThat(ejecuciones.get(0).cantidadGeneradas()).isEqualTo(1);
        assertThat(ejecuciones.get(0).cantidadPendientesUf()).isEqualTo(1);

        // La propuesta existe (el ciclo NO se abstiene de crearla), pero en 0 y PENDIENTE_UF.
        List<PropuestaFacturacionRespuestaDto> propuestas = listarPropuestas(2026, 2, clienteId, null, null);
        assertThat(propuestas).hasSize(1);
        PropuestaFacturacionRespuestaDto propuesta = propuestas.get(0);
        assertThat(propuesta.proyectoId()).isEqualTo(proyecto.id());
        assertThat(propuesta.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(propuesta.valorUf()).isNull();
        assertThat(propuesta.fechaValorUf()).isNull();
        assertThat(propuesta.netoClp()).isEqualByComparingTo("0");
        assertThat(propuesta.ivaClp()).isEqualByComparingTo("0");
        assertThat(propuesta.totalClp()).isEqualByComparingTo("0");

        // El informe la cuenta aparte y NO la suma al total.
        InformeFacturacionRespuestaDto informe = obtenerInforme("?periodoAnio=2026&periodoMes=2");
        assertThat(informe.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(informe.resumen().cantidadPorEstado().get(EstadoPropuesta.PENDIENTE_UF)).isEqualTo(1);
        assertThat(informe.resumen().cantidadPendienteUf()).isEqualTo(1);
        assertThat(informe.resumen().netoClp()).isEqualByComparingTo("0");
        assertThat(informe.resumen().ivaClp()).isEqualByComparingTo("0");
        assertThat(informe.resumen().totalClp()).isEqualByComparingTo("0");

        // No se puede asociar factura a una propuesta PENDIENTE_UF.
        mockMvc.perform(post("/api/v1/facturas")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"numeroFactura":"F-PENDIENTE-UF","fechaFactura":"2026-03-01","propuestaIds":[%d]}
                    """.formatted(propuesta.id())))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("PROPUESTA_NO_FACTURABLE"));
    }
}
