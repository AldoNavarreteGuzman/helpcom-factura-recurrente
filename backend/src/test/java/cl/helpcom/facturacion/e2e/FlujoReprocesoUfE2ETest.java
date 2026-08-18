package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * E2E-9 (docs/qa.md, deuda-tecnica.md ítem 8/9): reprocesar UF de una propuesta {@code
 * PENDIENTE_UF} — recupera propuestas reales tras un fallo transitorio de mindicador.cl, algo
 * que hasta este ítem era irrecuperable por dominio. Cubre ambos orígenes (CICLO y CSV), el
 * camino donde la UF sigue sin estar disponible (sin error, sigue {@code PENDIENTE_UF}) y el
 * guard duro que rechaza cualquier estado que no sea {@code PENDIENTE_UF}. La autorización
 * (solo ADMINISTRADOR) se cubre en {@code FlujoAutorizacionE2ETest}, no acá — mismo criterio
 * que el resto de la suite.
 */
@Tag("e2e")
class FlujoReprocesoUfE2ETest extends SoporteE2E {

    @Test
    void reprocesarUnaPropuestaCicloDeberiaCompletarElSnapshotCuandoLaUfYaEstaDisponible() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Reproceso Ciclo SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "12.0000", "UF", "MENSUAL", 15, LocalDate.of(2026, 1, 1), null);
        // UF de 2026-02-15 deliberadamente NO sembrada antes del ciclo.

        ejecutarCiclo(2026, 2);
        PropuestaFacturacionRespuestaDto pendienteUf = listarPropuestas(2026, 2, clienteId, null, null).get(0);
        assertThat(pendienteUf.proyectoId()).isEqualTo(proyecto.id());
        assertThat(pendienteUf.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(pendienteUf.netoClp()).isEqualByComparingTo("0");

        sembrarUf(LocalDate.of(2026, 2, 15), "40000.0000");

        PropuestaFacturacionRespuestaDto reprocesada = reprocesarUf(pendienteUf.id());

        assertThat(reprocesada.id()).isEqualTo(pendienteUf.id());
        assertThat(reprocesada.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(reprocesada.valorUf()).isEqualByComparingTo("40000.0000");
        assertThat(reprocesada.fechaValorUf()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(reprocesada.netoClp()).isEqualByComparingTo("480000"); // 12 × 40.000
        assertThat(reprocesada.ivaClp()).isEqualByComparingTo("91200");
        assertThat(reprocesada.totalClp()).isEqualByComparingTo("571200");

        // No se duplicó nada: sigue habiendo una única propuesta para ese (proyecto, período).
        List<PropuestaFacturacionRespuestaDto> propuestas = listarPropuestas(2026, 2, clienteId, null, null);
        assertThat(propuestas).hasSize(1);
        assertThat(propuestas.get(0).id()).isEqualTo(pendienteUf.id());
    }

    @Test
    void reprocesarUnaPropuestaCsvDeberiaCompletarElSnapshotCuandoLaUfYaEstaDisponible() throws Exception {
        String rut = rutDePrueba();
        crearCliente(rut, "Cliente Reproceso CSV SpA");
        String csv = "rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion\n"
            + rut + ";;Servicio CSV sin UF;2026-04;10-04-2026;UF;10;\n";
        // UF de 2026-04-10 deliberadamente NO sembrada.

        confirmarCsv(csv.getBytes(StandardCharsets.UTF_8));
        PropuestaFacturacionRespuestaDto pendienteUf =
            listarPropuestas(2026, 4, null, null, OrigenPropuesta.CSV).get(0);
        assertThat(pendienteUf.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);

        sembrarUf(LocalDate.of(2026, 4, 10), "40500.0000");

        PropuestaFacturacionRespuestaDto reprocesada = reprocesarUf(pendienteUf.id());

        assertThat(reprocesada.origen()).isEqualTo(OrigenPropuesta.CSV);
        assertThat(reprocesada.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(reprocesada.valorUf()).isEqualByComparingTo("40500.0000");
        assertThat(reprocesada.netoClp()).isEqualByComparingTo("405000"); // 10 × 40.500
    }

    @Test
    void reprocesarSinHaberSembradoLaUfDeberiaSeguirPendienteUfSinError() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Reproceso Sin UF SpA");
        crearProyecto(clienteId, "8.0000", "UF", "MENSUAL", 20, LocalDate.of(2026, 1, 1), null);
        ejecutarCiclo(2026, 2);
        PropuestaFacturacionRespuestaDto pendienteUf = listarPropuestas(2026, 2, clienteId, null, null).get(0);
        assertThat(pendienteUf.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);

        // Sin sembrarUf: sigue sin haber UF real disponible para esa fecha.
        PropuestaFacturacionRespuestaDto resultado = reprocesarUf(pendienteUf.id());

        assertThat(resultado.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(resultado.netoClp()).isEqualByComparingTo("0");
        assertThat(resultado.valorUf()).isNull();

        // Reprocesar de nuevo, todavía sin UF, sigue siendo seguro (idempotente de hecho).
        PropuestaFacturacionRespuestaDto segundoIntento = reprocesarUf(pendienteUf.id());
        assertThat(segundoIntento.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
    }

    @Test
    void reprocesarUnaPropuestaYaCalculableDeberiaRechazarseConConflictoYNoCambiarNada() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Reproceso Guard SpA");
        crearProyecto(clienteId, "200000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        ejecutarCiclo(2026, 2); // Precio en CLP puro: no necesita UF, queda PENDIENTE de entrada.
        PropuestaFacturacionRespuestaDto pendiente = listarPropuestas(2026, 2, clienteId, null, null).get(0);
        assertThat(pendiente.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);

        reprocesarUfSinAsumirExito(pendiente.id())
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("PROPUESTA_NO_REPROCESABLE"));

        PropuestaFacturacionRespuestaDto sinCambios = listarPropuestas(2026, 2, clienteId, null, null).get(0);
        assertThat(sinCambios.netoClp()).isEqualByComparingTo(pendiente.netoClp());
        assertThat(sinCambios.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
    }
}
