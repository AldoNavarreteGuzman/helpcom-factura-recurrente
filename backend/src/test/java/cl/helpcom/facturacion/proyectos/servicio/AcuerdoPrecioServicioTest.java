package cl.helpcom.facturacion.proyectos.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioSolicitudDto;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AcuerdoPrecioServicioTest {

    @Mock
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    private AcuerdoPrecioServicio servicio;
    private Proyecto proyecto;

    @BeforeEach
    void configurar() {
        servicio = new AcuerdoPrecioServicio(acuerdoPrecioRepositorio, proyectoRepositorio, new ContextoEmpresa());

        proyecto = new Proyecto();
        proyecto.setId(100L);
        proyecto.setEmpresa(new Empresa());
        proyecto.getEmpresa().setId(1L);
        when(proyectoRepositorio.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(proyecto));
    }

    private AcuerdoPrecioSolicitudDto conFechaTermino(LocalDate inicio, LocalDate termino) {
        return new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null, inicio, termino, null, null);
    }

    @Test
    void deberiaCalcularLaFechaTerminoDesdeMesesPactados() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null,
            LocalDate.of(2026, 1, 15), null, 6, null);
        when(acuerdoPrecioRepositorio.buscarTraslapados(eq(100L), any(), any(), isNull())).thenReturn(List.of());
        when(acuerdoPrecioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.crear(100L, solicitud);

        assertThat(respuesta.fechaTermino()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(respuesta.mesesPactados()).isEqualTo(6);
    }

    @Test
    void deberiaRechazarSiVienenAmbasFormasDeVigencia() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), 6, null);

        assertThatThrownBy(() -> servicio.crear(100L, solicitud))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ACUERDO_VIGENCIA_INVALIDA"));
    }

    @Test
    void deberiaRechazarSiNoVieneNingunaFormaDeVigencia() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null,
            LocalDate.of(2026, 1, 1), null, null, null);

        assertThatThrownBy(() -> servicio.crear(100L, solicitud))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ACUERDO_VIGENCIA_INVALIDA"));
    }

    @Test
    void deberiaRechazarMonedaPresenteParaDescuentoPorcentaje() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), Moneda.CLP,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null, null);

        assertThatThrownBy(() -> servicio.crear(100L, solicitud))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ACUERDO_MONEDA_INVALIDA"));
    }

    @Test
    void deberiaRechazarMonedaAusenteParaPrecioPactado() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.PRECIO_PACTADO, new BigDecimal("90"), null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null, null);

        assertThatThrownBy(() -> servicio.crear(100L, solicitud))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ACUERDO_MONEDA_INVALIDA"));
    }

    @Test
    void deberiaRechazarPorcentajeFueraDeRango() {
        AcuerdoPrecioSolicitudDto solicitud = new AcuerdoPrecioSolicitudDto(
            TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("150"), null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null, null);

        assertThatThrownBy(() -> servicio.crear(100L, solicitud))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ACUERDO_PORCENTAJE_INVALIDO"));
    }

    @Test
    void deberiaCrearElAcuerdoCuandoNoHayTraslape() {
        when(acuerdoPrecioRepositorio.buscarTraslapados(eq(100L), any(), any(), isNull())).thenReturn(List.of());
        when(acuerdoPrecioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.crear(100L, conFechaTermino(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));

        assertThat(respuesta.fechaInicio()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void deberiaRechazarConReglaNegocioExceptionCuandoLaConsultaDeTraslapeEncuentraConflicto() {
        AcuerdoPrecio conflicto = new AcuerdoPrecio();
        conflicto.setId(50L);
        conflicto.setFechaInicio(LocalDate.of(2026, 3, 1));
        conflicto.setFechaTermino(LocalDate.of(2026, 8, 31));
        when(acuerdoPrecioRepositorio.buscarTraslapados(eq(100L), any(), any(), isNull()))
            .thenReturn(List.of(conflicto));

        assertThatThrownBy(() -> servicio.crear(100L, conFechaTermino(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("ACUERDO_TRASLAPADO"));
    }

    @Test
    void deberiaTraducirLaExclusionConstraintDeBaseA409ComoRedDeSeguridad() {
        when(acuerdoPrecioRepositorio.buscarTraslapados(eq(100L), any(), any(), isNull())).thenReturn(List.of());
        when(acuerdoPrecioRepositorio.save(any())).thenThrow(new DataIntegrityViolationException("ex_acuerdo_no_traslape"));

        assertThatThrownBy(() -> servicio.crear(100L, conFechaTermino(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("ACUERDO_TRASLAPADO"));
    }

    @Test
    void deberiaExcluirseASiMismoAlValidarTraslapeEnActualizacion() {
        AcuerdoPrecio existente = new AcuerdoPrecio();
        existente.setId(9L);
        existente.setProyecto(proyecto);
        existente.setEmpresa(proyecto.getEmpresa());
        existente.setTipo(TipoAcuerdo.DESCUENTO_PORCENTAJE);
        existente.setValor(new BigDecimal("10"));
        existente.setFechaInicio(LocalDate.of(2026, 1, 1));
        existente.setFechaTermino(LocalDate.of(2026, 6, 30));

        when(acuerdoPrecioRepositorio.findByIdAndProyectoIdAndEmpresaId(9L, 100L, 1L))
            .thenReturn(Optional.of(existente));
        when(acuerdoPrecioRepositorio.buscarTraslapados(eq(100L), any(), any(), eq(9L))).thenReturn(List.of());
        when(acuerdoPrecioRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var respuesta = servicio.actualizar(
            100L, 9L, conFechaTermino(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(respuesta.fechaTermino()).isEqualTo(LocalDate.of(2026, 12, 31));
        org.mockito.Mockito.verify(acuerdoPrecioRepositorio).buscarTraslapados(eq(100L), any(), any(), eq(9L));
    }
}
