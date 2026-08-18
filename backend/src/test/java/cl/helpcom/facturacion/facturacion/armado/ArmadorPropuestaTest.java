package cl.helpcom.facturacion.facturacion.armado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.dominio.ParametroSistema;
import cl.helpcom.facturacion.empresa.repositorio.ParametroSistemaRepositorio;
import cl.helpcom.facturacion.facturacion.calculo.CalculadoraFacturacion;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.servicio.ServicioUf;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArmadorPropuestaTest {

    private static final Long EMPRESA_ID = 1L;

    @Mock
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    @Mock
    private ParametroSistemaRepositorio parametroSistemaRepositorio;

    @Mock
    private ServicioUf servicioUf;

    private ArmadorPropuesta armador;
    private Empresa empresa;
    private Cliente cliente;

    @BeforeEach
    void configurar() {
        armador = new ArmadorPropuesta(
            acuerdoPrecioRepositorio, parametroSistemaRepositorio, servicioUf, new CalculadoraFacturacion());

        empresa = new Empresa();
        empresa.setId(EMPRESA_ID);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmpresa(empresa);

        ParametroSistema tasaIva = new ParametroSistema();
        tasaIva.setClave("tasa_iva");
        tasaIva.setValor("0.19");
        lenient().when(parametroSistemaRepositorio.findByEmpresaIdAndClave(EMPRESA_ID, "tasa_iva"))
            .thenReturn(Optional.of(tasaIva));
    }

    @Test
    void deberiaCalcularElSnapshotCompletoCuandoLaUfEstaDisponible() {
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 2, 15))).thenReturn(new BigDecimal("40000"));

        PropuestaFacturacion propuesta = armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, null, OrigenPropuesta.CSV, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), "Servicio de prueba", new BigDecimal("12"), Moneda.UF));

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(propuesta.getValorUf()).isEqualByComparingTo("40000");
        assertThat(propuesta.getNetoClp()).isEqualByComparingTo("480000");
        assertThat(propuesta.getIvaClp()).isEqualByComparingTo("91200");
        assertThat(propuesta.getTotalClp()).isEqualByComparingTo("571200");
        assertThat(propuesta.getAcuerdo()).isNull();
    }

    @Test
    void deberiaQuedarPendienteUfConMontosEnCeroCuandoLaUfNoEstaDisponible() {
        when(servicioUf.obtenerValorUf(any())).thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 2, 15)));

        PropuestaFacturacion propuesta = armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, null, OrigenPropuesta.CSV, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), "Servicio de prueba", new BigDecimal("12"), Moneda.UF));

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(propuesta.getValorUf()).isNull();
        assertThat(propuesta.getNetoClp()).isEqualByComparingTo("0");
        assertThat(propuesta.getIvaClp()).isEqualByComparingTo("0");
        assertThat(propuesta.getTotalClp()).isEqualByComparingTo("0");
    }

    @Test
    void noDeberiaConsultarLaUfCuandoElCalculoEsEnClp() {
        PropuestaFacturacion propuesta = armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, null, OrigenPropuesta.CSV, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), "Servicio en CLP", new BigDecimal("850000"), Moneda.CLP));

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(propuesta.getNetoClp()).isEqualByComparingTo("850000");
        assertThat(propuesta.getIvaClp()).isEqualByComparingTo("161500");
        assertThat(propuesta.getTotalClp()).isEqualByComparingTo("1011500");
    }

    @Test
    void deberiaLanzarExcepcionSiLaTasaIvaNoEstaConfigurada() {
        when(parametroSistemaRepositorio.findByEmpresaIdAndClave(EMPRESA_ID, "tasa_iva")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, null, OrigenPropuesta.CSV, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), "Servicio en CLP", new BigDecimal("850000"), Moneda.CLP)))
            .isInstanceOf(IllegalStateException.class);
    }

    /**
     * Prueba ancla de consistencia de cálculo (mandato de la tarea): una fila CSV y un
     * proyecto del ciclo, con los mismos datos subyacentes (precio, moneda, acuerdo, UF,
     * tasa), DEBEN producir el mismo neto/iva/total — porque ambos pasan por el mismo
     * {@link ArmadorPropuesta} y la misma {@link CalculadoraFacturacion}.
     */
    @Test
    void deberiaProducirElMismoResultadoParaUnaFilaCsvQueParaUnProyectoDelCicloConLosMismosDatos() {
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 2, 15))).thenReturn(new BigDecimal("40000"));

        Proyecto proyecto = new Proyecto();
        proyecto.setId(100L);
        proyecto.setEmpresa(empresa);
        proyecto.setCliente(cliente);
        proyecto.setNombre("Servicio de prueba");
        proyecto.setPrecioBaseNeto(new BigDecimal("12"));
        proyecto.setMonedaPrecio(Moneda.UF);
        when(acuerdoPrecioRepositorio.buscarVigente(eq(100L), any())).thenReturn(Optional.empty());

        PropuestaFacturacion propuestaDelCiclo = armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, proyecto, OrigenPropuesta.CICLO, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), proyecto.getNombre(), proyecto.getPrecioBaseNeto(), proyecto.getMonedaPrecio()));

        PropuestaFacturacion propuestaCsv = armador.armar(new EntradaArmadoPropuesta(
            empresa, cliente, null, OrigenPropuesta.CSV, (short) 2026, (short) 2,
            LocalDate.of(2026, 2, 15), "Servicio de prueba (vía CSV)", new BigDecimal("12"), Moneda.UF));

        assertThat(propuestaCsv.getNetoClp()).isEqualByComparingTo(propuestaDelCiclo.getNetoClp());
        assertThat(propuestaCsv.getIvaClp()).isEqualByComparingTo(propuestaDelCiclo.getIvaClp());
        assertThat(propuestaCsv.getTotalClp()).isEqualByComparingTo(propuestaDelCiclo.getTotalClp());
        assertThat(propuestaCsv.getValorUf()).isEqualByComparingTo(propuestaDelCiclo.getValorUf());
        assertThat(propuestaDelCiclo.getNetoClp()).isEqualByComparingTo("480000");
    }
}
