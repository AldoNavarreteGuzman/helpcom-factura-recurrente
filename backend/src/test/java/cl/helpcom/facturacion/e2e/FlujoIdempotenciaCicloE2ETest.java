package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-4 (docs/qa.md): el ciclo puede correr más de una vez para el mismo período sin duplicar
 * propuestas (arquitectura-tecnica.md §9) — verificado contra Postgres real, donde participa
 * el índice único parcial {@code uq_prop_ciclo_periodo} como red de seguridad de última
 * instancia (la primera línea de defensa es el {@code existsBy...} de
 * {@code ServicioCicloFacturacion}; ver {@code EsquemaBaseDatosTest} para la prueba directa
 * de la restricción).
 */
@Tag("e2e")
class FlujoIdempotenciaCicloE2ETest extends SoporteE2E {

    @Test
    void ejecutarElMismoPeriodoDosVecesNoDuplicaPropuestas() throws Exception {
        LocalDate fechaFacturacion = LocalDate.of(2026, 3, 7);
        sembrarUf(fechaFacturacion, "41000.0000");

        Long clienteId = crearCliente(rutDePrueba(), "Cliente Idempotencia SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "15.0000", "UF", "MENSUAL", 7, LocalDate.of(2026, 2, 1), null);

        ResultadoCiclo primeraCorrida = ejecutarCiclo(2026, 3);
        assertThat(primeraCorrida.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        assertThat(primeraCorrida.cantidadGeneradas()).isEqualTo(1);

        ResultadoCiclo segundaCorrida = ejecutarCiclo(2026, 3);
        assertThat(segundaCorrida.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        assertThat(segundaCorrida.cantidadGeneradas()).isZero();
        assertThat(segundaCorrida.cantidadPendientesUf()).isZero();

        // Solo una propuesta persistida, con el snapshot de la primera corrida (invariante:
        // un recálculo posterior NO la modifica).
        List<PropuestaFacturacionRespuestaDto> propuestas = listarPropuestas(2026, 3, clienteId, null, null);
        assertThat(propuestas).hasSize(1);
        assertThat(propuestas.get(0).proyectoId()).isEqualTo(proyecto.id());
        assertThat(propuestas.get(0).netoClp()).isEqualByComparingTo("615000"); // 15 × 41.000

        // Ambas corridas quedan trazadas en ejecucion_ciclo (auditoría), pero sin duplicar
        // propuestas.
        List<EjecucionCicloRespuestaDto> ejecuciones = listarEjecucionesCiclo();
        assertThat(ejecuciones).hasSize(2);
        assertThat(ejecuciones).allMatch(e -> e.periodoAnio() == 2026 && e.periodoMes() == 3);
    }
}
