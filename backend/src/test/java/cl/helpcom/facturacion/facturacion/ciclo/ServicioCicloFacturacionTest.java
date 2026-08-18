package cl.helpcom.facturacion.facturacion.ciclo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.dominio.ParametroSistema;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.repositorio.ParametroSistemaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.calculo.CalculadoraFacturacion;
import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.EjecucionCicloRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.servicio.ServicioUf;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class ServicioCicloFacturacionTest {

    private static final Long EMPRESA_ID = 1L;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    @Mock
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    @Mock
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    @Mock
    private EjecucionCicloRepositorio ejecucionCicloRepositorio;

    @Mock
    private ParametroSistemaRepositorio parametroSistemaRepositorio;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    @Mock
    private ServicioUf servicioUf;

    @Mock
    private LockCiclo lockCiclo;

    @Mock
    private ContextoEmpresa contextoEmpresa;

    @Mock
    private AuditorAware<String> auditorAware;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ServicioCicloFacturacion servicio;
    private Empresa empresa;
    private Cliente cliente;
    private Proyecto proyecto;
    private EjecucionCiclo ejecucionAbierta;

    @BeforeEach
    void configurar() {
        // lenient(): esta es una fábrica de fixture compartida y cada prueba ejercita solo
        // un subconjunto (p. ej. la de "lock no adquirido" nunca llega a abrir la ejecución
        // ni a procesar proyectos), así que no todos los stubs se usan en todas las pruebas.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        servicio = new ServicioCicloFacturacion(
            proyectoRepositorio, propuestaFacturacionRepositorio, ejecucionCicloRepositorio, empresaRepositorio,
            new ArmadorPropuesta(acuerdoPrecioRepositorio, parametroSistemaRepositorio, servicioUf, new CalculadoraFacturacion()),
            new ResolutorPeriodo(), lockCiclo, contextoEmpresa, auditorAware, transactionManager);

        lenient().when(contextoEmpresa.obtenerEmpresaId()).thenReturn(EMPRESA_ID);
        lenient().when(lockCiclo.adquirir(eq(EMPRESA_ID), anyInt(), anyInt())).thenReturn(true);
        lenient().when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of("sistema"));

        empresa = new Empresa();
        empresa.setId(EMPRESA_ID);
        lenient().when(empresaRepositorio.getReferenceById(EMPRESA_ID)).thenReturn(empresa);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmpresa(empresa);
        cliente.setRazonSocial("Cliente de Prueba SpA");

        proyecto = new Proyecto();
        proyecto.setId(100L);
        proyecto.setEmpresa(empresa);
        proyecto.setCliente(cliente);
        proyecto.setNombre("Servicio de prueba");
        proyecto.setPrecioBaseNeto(new BigDecimal("12"));
        proyecto.setMonedaPrecio(Moneda.UF);
        proyecto.setPeriodicidad(Periodicidad.MENSUAL);
        proyecto.setDiaFacturacion((short) 15);
        proyecto.setFechaInicio(LocalDate.of(2026, 1, 10));
        proyecto.setActivo(true);

        lenient().when(proyectoRepositorio.encontrarIdsActivosDeLaEmpresa(EMPRESA_ID)).thenReturn(List.of(100L));
        lenient().when(proyectoRepositorio.findByIdAndEmpresaId(100L, EMPRESA_ID)).thenReturn(Optional.of(proyecto));
        lenient().when(acuerdoPrecioRepositorio.buscarVigente(eq(100L), any())).thenReturn(Optional.empty());

        ParametroSistema tasaIva = new ParametroSistema();
        tasaIva.setClave("tasa_iva");
        tasaIva.setValor("0.19");
        lenient().when(parametroSistemaRepositorio.findByEmpresaIdAndClave(EMPRESA_ID, "tasa_iva"))
            .thenReturn(Optional.of(tasaIva));

        AtomicLong secuenciaEjecucion = new AtomicLong(1);
        lenient().when(ejecucionCicloRepositorio.save(any())).thenAnswer(inv -> {
            ejecucionAbierta = inv.getArgument(0);
            ejecucionAbierta.setId(secuenciaEjecucion.getAndIncrement());
            return ejecucionAbierta;
        });
        lenient().when(ejecucionCicloRepositorio.getReferenceById(any())).thenAnswer(inv -> ejecucionAbierta);

        lenient().when(propuestaFacturacionRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void deberiaGenerarLaPropuestaConElSnapshotCompleto() {
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 2, 15))).thenReturn(new BigDecimal("40000"));

        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 2, DisparoCiclo.MANUAL);

        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);
        assertThat(resultado.cantidadPendientesUf()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);

        ArgumentCaptor<PropuestaFacturacion> captor = ArgumentCaptor.forClass(PropuestaFacturacion.class);
        verify(propuestaFacturacionRepositorio).save(captor.capture());
        PropuestaFacturacion propuesta = captor.getValue();

        assertThat(propuesta.getEmpresa()).isEqualTo(empresa);
        assertThat(propuesta.getCliente()).isEqualTo(cliente);
        assertThat(propuesta.getProyecto()).isEqualTo(proyecto);
        assertThat(propuesta.getOrigen()).isEqualTo(OrigenPropuesta.CICLO);
        assertThat(propuesta.getPeriodoAnio()).isEqualTo((short) 2026);
        assertThat(propuesta.getPeriodoMes()).isEqualTo((short) 2);
        assertThat(propuesta.getFechaFacturacion()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(propuesta.getDescripcion()).isEqualTo("Servicio de prueba");
        assertThat(propuesta.getMonedaOrigen()).isEqualTo(Moneda.UF);
        assertThat(propuesta.getPrecioBaseNeto()).isEqualByComparingTo("12");
        assertThat(propuesta.getAcuerdo()).isNull();
        assertThat(propuesta.getValorUf()).isEqualByComparingTo("40000");
        assertThat(propuesta.getFechaValorUf()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(propuesta.getNetoClp()).isEqualByComparingTo("480000");
        assertThat(propuesta.getTasaIva()).isEqualByComparingTo("0.19");
        assertThat(propuesta.getIvaClp()).isEqualByComparingTo("91200");
        assertThat(propuesta.getTotalClp()).isEqualByComparingTo("571200");
        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);

        assertThat(ejecucionAbierta.getEstado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        assertThat(ejecucionAbierta.getCantidadGeneradas()).isEqualTo(1);
        assertThat(ejecucionAbierta.getCantidadPendientesUf()).isZero();
    }

    @Test
    void deberiaSerIdempotenteYNoDuplicarSiYaExisteUnaPropuestaDeCiclo() {
        when(propuestaFacturacionRepositorio.existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
            100L, (short) 2026, (short) 2, OrigenPropuesta.CICLO)).thenReturn(true);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 2, DisparoCiclo.MANUAL);

        assertThat(resultado.cantidadGeneradas()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        verify(propuestaFacturacionRepositorio, never()).save(any());
        verify(servicioUf, never()).obtenerValorUf(any());
    }

    @Test
    void deberiaMarcarPendienteUfYContinuarCuandoLaUfNoEstaDisponible() {
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 2, 15)))
            .thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 2, 15)));

        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 2, DisparoCiclo.MANUAL);

        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);
        assertThat(resultado.cantidadPendientesUf()).isEqualTo(1);
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.CON_ADVERTENCIAS);

        ArgumentCaptor<PropuestaFacturacion> captor = ArgumentCaptor.forClass(PropuestaFacturacion.class);
        verify(propuestaFacturacionRepositorio).save(captor.capture());
        PropuestaFacturacion propuesta = captor.getValue();

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(propuesta.getValorUf()).isNull();
        assertThat(propuesta.getFechaValorUf()).isNull();
        assertThat(propuesta.getNetoClp()).isEqualByComparingTo("0");
        assertThat(propuesta.getIvaClp()).isEqualByComparingTo("0");
        assertThat(propuesta.getTotalClp()).isEqualByComparingTo("0");

        assertThat(ejecucionAbierta.getEstado()).isEqualTo(EstadoEjecucionCiclo.CON_ADVERTENCIAS);
        assertThat(ejecucionAbierta.getCantidadPendientesUf()).isEqualTo(1);
    }

    @Test
    void deberiaEjecutarElCicloIgualCuandoElLockDeRedisSeDegradaYPermiteContinuar() {
        // LockCiclo.adquirir() ya encapsula la degradación: si Redis falla, retorna true
        // igual (ver LockCicloTest). Desde la perspectiva del servicio, "lock adquirido" y
        // "Redis caído mas se permite continuar" son indistinguibles: en ambos casos debe
        // seguir de largo y generar la propuesta con normalidad.
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));

        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 2, DisparoCiclo.MANUAL);

        assertThat(resultado.cantidadGeneradas()).isEqualTo(1);
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        verify(lockCiclo).liberar(EMPRESA_ID, 2026, 2);
    }

    @Test
    void noDeberiaHacerNadaSiOtraEjecucionYaEstaEnCursoParaElMismoPeriodo() {
        when(lockCiclo.adquirir(EMPRESA_ID, 2026, 2)).thenReturn(false);

        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 2, DisparoCiclo.MANUAL);

        assertThat(resultado.ejecucionId()).isNull();
        assertThat(resultado.cantidadGeneradas()).isZero();
        verify(ejecucionCicloRepositorio, never()).save(any());
        verify(propuestaFacturacionRepositorio, never()).save(any());
    }

    @Test
    void deberiaOmitirElProyectoCuandoElResolutorDePeriodoDiceQueNoCorresponde() {
        // El período de inicio del proyecto (enero) no se factura: MENSUAL parte el mes
        // siguiente. Se ejecuta el ciclo del propio mes de inicio.
        ResultadoCiclo resultado = servicio.ejecutarCiclo(2026, 1, DisparoCiclo.MANUAL);

        assertThat(resultado.cantidadGeneradas()).isZero();
        assertThat(resultado.estado()).isEqualTo(EstadoEjecucionCiclo.EXITOSA);
        verify(propuestaFacturacionRepositorio, never()).save(any());
        verify(servicioUf, never()).obtenerValorUf(any());
    }
}
