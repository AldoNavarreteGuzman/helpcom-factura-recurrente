package cl.helpcom.facturacion.importacion.validacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.importacion.csv.FilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidadorFilaCsvTest {

    private static final Long EMPRESA_ID = 1L;

    @Mock
    private ClienteRepositorio clienteRepositorio;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    @Mock
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    private ValidadorFilaCsv validador;
    private Cliente clienteActivo;

    @BeforeEach
    void configurar() {
        validador = new ValidadorFilaCsv(clienteRepositorio, proyectoRepositorio, propuestaFacturacionRepositorio);

        clienteActivo = new Cliente();
        clienteActivo.setId(10L);
        clienteActivo.setRut("11111111-1");
        clienteActivo.setActivo(true);
        lenient().when(clienteRepositorio.findByEmpresaIdAndRut(EMPRESA_ID, "11111111-1"))
            .thenReturn(Optional.of(clienteActivo));
    }

    private FilaCsv filaValida() {
        return new FilaCsv(2, "11111111-1", null, "Servicio de prueba", "2026-01", "15-01-2026", "UF", "12.5", null);
    }

    @Test
    void unaFilaCompletamenteValidaDeberiaQuedarOk() {
        FilaValidada resultado = validador.validar(filaValida(), EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(resultado.mensajes()).isEmpty();
        assertThat(resultado.cliente()).isEqualTo(clienteActivo);
        assertThat(resultado.periodoAnio()).isEqualTo((short) 2026);
        assertThat(resultado.periodoMes()).isEqualTo((short) 1);
        assertThat(resultado.moneda()).isEqualTo(Moneda.UF);
        assertThat(resultado.montoNeto()).isEqualByComparingTo("12.5");
    }

    @Test
    void deberiaQuedarConAdvertenciaCuandoLaFechaNoCoincideConElPeriodo() {
        FilaCsv fila = new FilaCsv(
            2, "11111111-1", null, "Servicio", "2026-01", "15-02-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ADVERTENCIA);
        assertThat(resultado.esValida()).isTrue();
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("no coincide con el período"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElRutNoTieneFormatoValido() {
        FilaCsv fila = new FilaCsv(2, "no-es-un-rut", null, "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.esValida()).isFalse();
        assertThat(resultado.cliente()).isNull();
    }

    @Test
    void deberiaQuedarConErrorCuandoElClienteNoExiste() {
        when(clienteRepositorio.findByEmpresaIdAndRut(EMPRESA_ID, "1-9")).thenReturn(Optional.empty());
        FilaCsv fila = new FilaCsv(2, "1-9", null, "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("No existe un cliente"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElClienteEstaInactivo() {
        clienteActivo.setActivo(false);

        FilaValidada resultado = validador.validar(filaValida(), EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("está inactivo"));
    }

    @Test
    void deberiaQuedarConErrorCuandoLaMonedaNoEsUfNiClp() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, "Servicio", "2026-01", "15-01-2026", "USD", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("debe ser UF o CLP"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElMontoNetoNoEsNumerico() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, "Servicio", "2026-01", "15-01-2026", "UF", "abc", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("no es un número válido"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElMontoNetoEsCeroONegativo() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, "Servicio", "2026-01", "15-01-2026", "UF", "0", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("mayor que cero"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElPeriodoNoTieneElFormatoCorrecto() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, "Servicio", "2026/01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("formato AAAA-MM"));
    }

    @Test
    void deberiaQuedarConErrorCuandoLaFechaNoTieneElFormatoCorrecto() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, "Servicio", "2026-01", "2026-01-15", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("formato DD-MM-AAAA"));
    }

    @Test
    void deberiaQuedarConErrorCuandoLaDescripcionEstaVacia() {
        FilaCsv fila = new FilaCsv(2, "11111111-1", null, " ", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("descripción es obligatoria"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElProyectoNoExiste() {
        when(proyectoRepositorio.findByEmpresaIdAndCodigo(EMPRESA_ID, "PRJ-999")).thenReturn(Optional.empty());
        FilaCsv fila = new FilaCsv(
            2, "11111111-1", "PRJ-999", "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("No existe un proyecto"));
    }

    @Test
    void deberiaQuedarConErrorCuandoElProyectoPerteneceAOtroCliente() {
        Cliente otroCliente = new Cliente();
        otroCliente.setId(99L);
        Proyecto proyectoDeOtroCliente = new Proyecto();
        proyectoDeOtroCliente.setId(500L);
        proyectoDeOtroCliente.setCliente(otroCliente);
        when(proyectoRepositorio.findByEmpresaIdAndCodigo(EMPRESA_ID, "PRJ-014"))
            .thenReturn(Optional.of(proyectoDeOtroCliente));
        FilaCsv fila = new FilaCsv(
            2, "11111111-1", "PRJ-014", "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("pertenece a otro cliente"));
    }

    @Test
    void deberiaQuedarConErrorCuandoYaExisteUnaPropuestaDeCicloParaElMismoProyectoYPeriodo() {
        Proyecto proyecto = new Proyecto();
        proyecto.setId(500L);
        proyecto.setCliente(clienteActivo);
        when(proyectoRepositorio.findByEmpresaIdAndCodigo(EMPRESA_ID, "PRJ-014")).thenReturn(Optional.of(proyecto));
        when(propuestaFacturacionRepositorio.existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
            500L, (short) 2026, (short) 1, OrigenPropuesta.CICLO)).thenReturn(true);
        FilaCsv fila = new FilaCsv(
            2, "11111111-1", "PRJ-014", "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(resultado.mensajes()).anyMatch(m -> m.contains("no se importa para evitar duplicarla"));
    }

    @Test
    void deberiaResolverElProyectoCuandoExisteYPerteneceAlMismoCliente() {
        Proyecto proyecto = new Proyecto();
        proyecto.setId(500L);
        proyecto.setCliente(clienteActivo);
        when(proyectoRepositorio.findByEmpresaIdAndCodigo(EMPRESA_ID, "PRJ-014")).thenReturn(Optional.of(proyecto));
        lenient().when(propuestaFacturacionRepositorio.existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
            eq(500L), any(), any(), eq(OrigenPropuesta.CICLO))).thenReturn(false);
        FilaCsv fila = new FilaCsv(
            2, "11111111-1", "PRJ-014", "Servicio", "2026-01", "15-01-2026", "UF", "12.5", null);

        FilaValidada resultado = validador.validar(fila, EMPRESA_ID);

        assertThat(resultado.estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(resultado.proyecto()).isEqualTo(proyecto);
    }
}
