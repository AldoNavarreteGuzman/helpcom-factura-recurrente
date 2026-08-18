package cl.helpcom.facturacion.facturacion.ciclo;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ResolutorPeriodoTest {

    private final ResolutorPeriodo resolutor = new ResolutorPeriodo();

    @Test
    void mensual_noDeberiaCorresponderElMesDeInicio() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2026, 1, 10), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 1))).isEmpty();
    }

    @Test
    void mensual_deberiaCorresponderDesdeElMesSiguienteAlDeInicio() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2026, 1, 10), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 2))).contains(LocalDate.of(2026, 2, 15));
    }

    @Test
    void mensual_deberiaCorresponderEnMesesPosteriores() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2026, 1, 10), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 5))).contains(LocalDate.of(2026, 5, 15));
    }

    @Test
    void mensual_diaInexistenteEnElMes_deberiaUsarElUltimoDiaDelMes() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 31, LocalDate.of(2026, 1, 5), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 2))).contains(LocalDate.of(2026, 2, 28));
    }

    @Test
    void anual_deberiaCorresponderElMismoMesYAnioDelContrato() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.ANUAL, 15, LocalDate.of(2026, 1, 15), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 1))).contains(LocalDate.of(2026, 1, 15));
    }

    @Test
    void anual_deberiaCorresponderElMismoMesDeAniosSiguientes() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.ANUAL, 15, LocalDate.of(2026, 1, 15), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2027, 1))).contains(LocalDate.of(2027, 1, 15));
    }

    @Test
    void anual_noDeberiaCorresponderEnOtrosMeses() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.ANUAL, 15, LocalDate.of(2026, 1, 15), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 2))).isEmpty();
        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 12))).isEmpty();
    }

    @Test
    void noDeberiaCorresponderSiElProyectoEstaInactivo() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2025, 1, 10), null, false);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 3))).isEmpty();
    }

    @Test
    void noDeberiaCorresponderSiElPeriodoEsAnteriorAlInicioEfectivo() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2026, 3, 10), null, true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 2))).isEmpty();
    }

    @Test
    void noDeberiaCorresponderSiLaFechaDeFacturacionSuperaLaFechaDeTermino() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 20, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 15), true);

        // Primer cobro mensual es febrero; en abril (2026-04-20) ya superó el término (2026-03-15).
        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 4))).isEmpty();
    }

    @Test
    void deberiaCorresponderCuandoLaFechaDeFacturacionCoincideExactamenteConLaFechaDeTermino() {
        DatosProyectoPeriodo datos = new DatosProyectoPeriodo(
            Periodicidad.MENSUAL, 15, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 15), true);

        assertThat(resolutor.resolver(datos, YearMonth.of(2026, 3))).contains(LocalDate.of(2026, 3, 15));
    }
}
