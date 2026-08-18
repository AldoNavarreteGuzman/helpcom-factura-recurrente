package cl.helpcom.facturacion.clientes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.dto.ClienteSolicitudDto;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
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
class ClienteServicioTest {

    @Mock
    private ClienteRepositorio clienteRepositorio;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    private ClienteServicio servicio;

    @BeforeEach
    void configurar() {
        servicio = new ClienteServicio(clienteRepositorio, proyectoRepositorio, empresaRepositorio, new ContextoEmpresa());
    }

    private ClienteSolicitudDto solicitudValida(String rut) {
        return new ClienteSolicitudDto(rut, "Cliente de Prueba SpA", null, null, null, null, null, true);
    }

    @Test
    void deberiaLanzarSolicitudInvalidaExceptionSiElRutNoEsValido() {
        assertThatThrownBy(() -> servicio.crear(solicitudValida("12345678-9")))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("RUT_INVALIDO"));

        verify(clienteRepositorio, never()).save(any());
    }

    @Test
    void deberiaNormalizarElRutAntesDeGuardar() {
        when(clienteRepositorio.existsByEmpresaIdAndRut(1L, "12345678-5")).thenReturn(false);
        when(empresaRepositorio.getReferenceById(1L)).thenReturn(new Empresa());
        when(clienteRepositorio.save(any())).thenAnswer(inv -> {
            Cliente guardado = inv.getArgument(0);
            guardado.setId(20L);
            return guardado;
        });

        var respuesta = servicio.crear(solicitudValida(" 12.345.678-5 "));

        assertThat(respuesta.rut()).isEqualTo("12345678-5");
    }

    @Test
    void deberiaLanzarReglaNegocioExceptionSiElRutYaExiste() {
        when(clienteRepositorio.existsByEmpresaIdAndRut(1L, "12345678-5")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(solicitudValida("12345678-5")))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("CLIENTE_DUPLICADO"));

        verify(clienteRepositorio, never()).save(any());
    }

    @Test
    void deberiaHacerBajaLogicaAlEliminarUnClienteConProyectosAsociados() {
        Cliente cliente = new Cliente();
        cliente.setId(7L);
        cliente.setEmpresa(new Empresa());
        cliente.getEmpresa().setId(1L);
        cliente.setActivo(true);

        when(clienteRepositorio.findByIdAndEmpresaId(7L, 1L)).thenReturn(java.util.Optional.of(cliente));
        when(proyectoRepositorio.existsByClienteIdAndEmpresaId(7L, 1L)).thenReturn(true);

        servicio.eliminar(7L);

        assertThat(cliente.isActivo()).isFalse();
        verify(clienteRepositorio, never()).delete(any(Cliente.class));
    }

    @Test
    void deberiaEliminarFisicamenteUnClienteSinProyectosAsociados() {
        Cliente cliente = new Cliente();
        cliente.setId(8L);
        cliente.setEmpresa(new Empresa());
        cliente.getEmpresa().setId(1L);

        when(clienteRepositorio.findByIdAndEmpresaId(8L, 1L)).thenReturn(java.util.Optional.of(cliente));
        when(proyectoRepositorio.existsByClienteIdAndEmpresaId(8L, 1L)).thenReturn(false);

        servicio.eliminar(8L);

        verify(clienteRepositorio).delete(cliente);
    }
}
