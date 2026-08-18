package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-5 (docs/qa.md): reglas de resolución de período (arquitectura-tecnica.md §9,
 * {@code ResolutorPeriodo}), ejercitadas de punta a punta vía el ciclo real. Todos los
 * proyectos usan moneda CLP para aislar la resolución de fecha/período del cálculo del
 * monto (ya cubierto en {@link FlujoAcuerdosPrecioE2ETest}/{@link FlujoCaminoFelizE2ETest}).
 */
@Tag("e2e")
class FlujoPeriodicidadE2ETest extends SoporteE2E {

    @Test
    void unProyectoMensualNoFacturaElMesDeInicioYSiElSiguiente() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Mensual SpA");
        crearProyecto(clienteId, "500000", "CLP", "MENSUAL", 10, LocalDate.of(2026, 4, 15), null);

        ResultadoCiclo cicloMesDeInicio = ejecutarCiclo(2026, 4);
        assertThat(cicloMesDeInicio.cantidadGeneradas()).isZero();
        assertThat(listarPropuestas(2026, 4, clienteId, null, null)).isEmpty();

        ResultadoCiclo cicloMesSiguiente = ejecutarCiclo(2026, 5);
        assertThat(cicloMesSiguiente.cantidadGeneradas()).isEqualTo(1);
        List<PropuestaFacturacionRespuestaDto> propuestasMayo = listarPropuestas(2026, 5, clienteId, null, null);
        assertThat(propuestasMayo).hasSize(1);
        assertThat(propuestasMayo.get(0).fechaFacturacion()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    @Test
    void unProyectoAnualSoloFacturaEnElMesDeAniversarioYCadaAnio() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Anual SpA");
        crearProyecto(clienteId, "2000000", "CLP", "ANUAL", 20, LocalDate.of(2026, 6, 1), null);

        assertThat(ejecutarCiclo(2026, 5).cantidadGeneradas()).isZero();
        assertThat(ejecutarCiclo(2026, 7).cantidadGeneradas()).isZero();

        ResultadoCiclo primerAniversario = ejecutarCiclo(2026, 6);
        assertThat(primerAniversario.cantidadGeneradas()).isEqualTo(1);
        assertThat(listarPropuestas(2026, 6, clienteId, null, null).get(0).fechaFacturacion())
            .isEqualTo(LocalDate.of(2026, 6, 20));

        // El aniversario se repite cada año desde el inicio, no una sola vez.
        ResultadoCiclo segundoAniversario = ejecutarCiclo(2027, 6);
        assertThat(segundoAniversario.cantidadGeneradas()).isEqualTo(1);
        assertThat(listarPropuestas(2027, 6, clienteId, null, null).get(0).fechaFacturacion())
            .isEqualTo(LocalDate.of(2027, 6, 20));
    }

    @Test
    void unDiaDeFacturacionQueNoExisteEnElMesUsaElUltimoDiaDelMes() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Dia 31 SpA");
        crearProyecto(clienteId, "300000", "CLP", "MENSUAL", 31, LocalDate.of(2026, 1, 1), null);

        // Febrero de 2026 (no bisiesto) tiene 28 días.
        ejecutarCiclo(2026, 2);
        assertThat(listarPropuestas(2026, 2, clienteId, null, null).get(0).fechaFacturacion())
            .isEqualTo(LocalDate.of(2026, 2, 28));

        // Abril tiene 30 días.
        ejecutarCiclo(2026, 4);
        assertThat(listarPropuestas(2026, 4, clienteId, null, null).get(0).fechaFacturacion())
            .isEqualTo(LocalDate.of(2026, 4, 30));

        // Un mes con 31 días sí usa el día 31 tal cual.
        ejecutarCiclo(2026, 3);
        assertThat(listarPropuestas(2026, 3, clienteId, null, null).get(0).fechaFacturacion())
            .isEqualTo(LocalDate.of(2026, 3, 31));
    }
}
