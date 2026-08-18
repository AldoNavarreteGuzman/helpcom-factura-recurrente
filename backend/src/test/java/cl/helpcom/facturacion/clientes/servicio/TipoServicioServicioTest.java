package cl.helpcom.facturacion.clientes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.TipoServicio;
import cl.helpcom.facturacion.clientes.dto.TipoServicioSolicitudDto;
import cl.helpcom.facturacion.clientes.repositorio.TipoServicioRepositorio;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TipoServicioServicioTest {

    @Mock
    private TipoServicioRepositorio tipoServicioRepositorio;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    private TipoServicioServicio servicio;

    @BeforeEach
    void configurar() {
        servicio = new TipoServicioServicio(
            tipoServicioRepositorio, proyectoRepositorio, empresaRepositorio, new ContextoEmpresa());
    }

    @Test
    void deberiaLanzarReglaNegocioExceptionSiElNombreYaExisteAlCrear() {
        TipoServicioSolicitudDto solicitud = new TipoServicioSolicitudDto("Soporte", true);
        when(tipoServicioRepositorio.existsByEmpresaIdAndNombreIgnoreCase(1L, "Soporte")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(solicitud))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("TIPO_SERVICIO_DUPLICADO"));

        verify(tipoServicioRepositorio, never()).save(any());
    }

    @Test
    void deberiaCrearElTipoServicioCuandoElNombreNoEstaDuplicado() {
        TipoServicioSolicitudDto solicitud = new TipoServicioSolicitudDto("Mantención", true);
        when(tipoServicioRepositorio.existsByEmpresaIdAndNombreIgnoreCase(1L, "Mantención")).thenReturn(false);
        when(empresaRepositorio.getReferenceById(1L)).thenReturn(new Empresa());
        when(tipoServicioRepositorio.save(any())).thenAnswer(inv -> {
            TipoServicio guardado = inv.getArgument(0);
            guardado.setId(10L);
            return guardado;
        });

        var respuesta = servicio.crear(solicitud);

        assertThat(respuesta.id()).isEqualTo(10L);
        assertThat(respuesta.nombre()).isEqualTo("Mantención");
        assertThat(respuesta.activo()).isTrue();
    }

    @Test
    void deberiaExcluirseASiMismoAlValidarDuplicadosEnActualizacion() {
        TipoServicio existente = new TipoServicio();
        existente.setId(5L);
        existente.setEmpresa(new Empresa());
        existente.getEmpresa().setId(1L);
        existente.setNombre("Soporte");
        existente.setActivo(true);

        when(tipoServicioRepositorio.findByIdAndEmpresaId(5L, 1L)).thenReturn(java.util.Optional.of(existente));
        when(tipoServicioRepositorio.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(1L, "Soporte técnico", 5L))
            .thenReturn(false);

        var respuesta = servicio.actualizar(5L, new TipoServicioSolicitudDto("Soporte técnico", true));

        assertThat(respuesta.nombre()).isEqualTo("Soporte técnico");
        verify(tipoServicioRepositorio, never()).existsByEmpresaIdAndNombreIgnoreCase(anyLong(), eq("Soporte técnico"));
    }
}
