package cl.helpcom.facturacion.facturacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.armado.EntradaArmadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

/**
 * Ejercita {@code listar()} contra una base H2 real (sin Docker): el mapeo de
 * numero/fecha de factura y el fetch join que evita N+1 (ver
 * {@code PropuestaFacturacionEspecificaciones.conFacturaFetch}) necesitan una relación LAZY
 * real resuelta por Hibernate, algo que un mock del repositorio no puede verificar.
 * {@code ServicioPropuestaFacturacion} se instancia a mano porque {@code @DataJpaTest} no
 * escanea {@code @Service}; solo {@code ContextoEmpresa} se mockea.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServicioPropuestaFacturacionTest {

    @Autowired
    private EmpresaRepositorio empresaRepositorio;

    @Autowired
    private ClienteRepositorio clienteRepositorio;

    @Autowired
    private FacturaRepositorio facturaRepositorio;

    @Autowired
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    @Autowired
    private EntityManager entityManager;

    private ServicioPropuestaFacturacion servicio;
    private ArmadorPropuesta armadorPropuesta;
    private Cliente cliente;
    private Empresa empresa;
    private PropuestaFacturacion propuestaFacturada;
    private PropuestaFacturacion propuestaPendiente;
    private PropuestaFacturacion propuestaPendienteUf;

    @BeforeEach
    void configurar() {
        empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        ContextoEmpresa contextoEmpresa = mock(ContextoEmpresa.class);
        when(contextoEmpresa.obtenerEmpresaId()).thenReturn(empresa.getId());
        armadorPropuesta = mock(ArmadorPropuesta.class);
        servicio = new ServicioPropuestaFacturacion(propuestaFacturacionRepositorio, contextoEmpresa, armadorPropuesta);

        cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setRut("11111111-1");
        cliente.setRazonSocial("Cliente A SpA");
        clienteRepositorio.save(cliente);

        Factura factura = new Factura();
        factura.setEmpresa(empresa);
        factura.setNumeroFactura("F-001");
        factura.setFechaFactura(LocalDate.of(2026, 3, 1));
        facturaRepositorio.save(factura);

        propuestaFacturada = propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, factura, EstadoPropuesta.FACTURADA, OrigenPropuesta.CICLO));
        propuestaPendiente = propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, null, EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO));
        propuestaPendienteUf = propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, null, EstadoPropuesta.PENDIENTE_UF, OrigenPropuesta.CSV));

        propuestaFacturacionRepositorio.flush();
    }

    private PropuestaFacturacion crearPropuesta(
        Empresa empresa, Factura factura, EstadoPropuesta estado, OrigenPropuesta origen) {
        PropuestaFacturacion propuesta = new PropuestaFacturacion();
        propuesta.setEmpresa(empresa);
        propuesta.setCliente(cliente);
        propuesta.setFactura(factura);
        propuesta.setOrigen(origen);
        propuesta.setPeriodoAnio((short) 2026);
        propuesta.setPeriodoMes((short) 2);
        propuesta.setFechaFacturacion(LocalDate.of(2026, 2, 15));
        propuesta.setDescripcion("Servicio de prueba");
        propuesta.setMonedaOrigen(Moneda.CLP);
        propuesta.setPrecioBaseNeto(new BigDecimal("100000"));
        propuesta.setTasaIva(new BigDecimal("0.19"));
        propuesta.setNetoClp(new BigDecimal("100000"));
        propuesta.setIvaClp(new BigDecimal("19000"));
        propuesta.setTotalClp(new BigDecimal("119000"));
        propuesta.setEstado(estado);
        return propuesta;
    }

    @Test
    void unaPropuestaFacturadaDeberiaTraerNumeroYFechaDeFactura() {
        Page<PropuestaFacturacionRespuestaDto> pagina = servicio.listar(
            2026, 2, null, EstadoPropuesta.FACTURADA, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1);
        PropuestaFacturacionRespuestaDto dto = pagina.getContent().get(0);
        assertThat(dto.numeroFactura()).isEqualTo("F-001");
        assertThat(dto.fechaFactura()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void unaPropuestaNoFacturadaDeberiaTenerNumeroYFechaDeFacturaNulos() {
        Page<PropuestaFacturacionRespuestaDto> pagina = servicio.listar(
            2026, 2, null, EstadoPropuesta.PENDIENTE, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1);
        PropuestaFacturacionRespuestaDto dto = pagina.getContent().get(0);
        assertThat(dto.numeroFactura()).isNull();
        assertThat(dto.fechaFactura()).isNull();
    }

    @Test
    void elFiltroPorOrigenDeberiaAcotarElListado() {
        Page<PropuestaFacturacionRespuestaDto> soloCiclo = servicio.listar(
            2026, 2, null, null, OrigenPropuesta.CICLO, PageRequest.of(0, 20));
        Page<PropuestaFacturacionRespuestaDto> soloCsv = servicio.listar(
            2026, 2, null, null, OrigenPropuesta.CSV, PageRequest.of(0, 20));

        assertThat(soloCiclo.getContent()).hasSize(2)
            .allMatch(dto -> dto.origen() == OrigenPropuesta.CICLO);
        assertThat(soloCsv.getContent()).hasSize(1)
            .allMatch(dto -> dto.origen() == OrigenPropuesta.CSV);
    }

    @Test
    void elFiltroPorOrigenCombinadoConEstadoDeberiaAcotarAunMas() {
        Page<PropuestaFacturacionRespuestaDto> pagina = servicio.listar(
            2026, 2, null, EstadoPropuesta.FACTURADA, OrigenPropuesta.CICLO, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).numeroFactura()).isEqualTo("F-001");

        Page<PropuestaFacturacionRespuestaDto> vacia = servicio.listar(
            2026, 2, null, EstadoPropuesta.FACTURADA, OrigenPropuesta.CSV, PageRequest.of(0, 20));

        assertThat(vacia.getContent()).isEmpty();
    }

    /**
     * D3 (deuda-tecnica.md ítem 3, estandares-de-codigo.md §3.8): sin {@code sort} explícito,
     * el listado ordena por período descendente (más reciente primero).
     */
    @Test
    void sinSortExplicitoDeberiaOrdenarPorPeriodoDescendente() {
        propuestaFacturacionRepositorio.save(crearPropuestaConPeriodo(2026, 5));
        propuestaFacturacionRepositorio.save(crearPropuestaConPeriodo(2025, 11));
        propuestaFacturacionRepositorio.flush();

        Page<PropuestaFacturacionRespuestaDto> pagina = servicio.listar(
            null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(5);
        assertThat(pagina.getContent().get(0).periodoAnio()).isEqualTo(2026);
        assertThat(pagina.getContent().get(0).periodoMes()).isEqualTo(5);
        assertThat(pagina.getContent().get(pagina.getContent().size() - 1).periodoAnio()).isEqualTo(2025);
        assertThat(pagina.getContent().get(pagina.getContent().size() - 1).periodoMes()).isEqualTo(11);
    }

    @Test
    void unSortExplicitoNoDeberiaSerPisadoPorElOrdenPorDefecto() {
        propuestaFacturacionRepositorio.save(crearPropuestaConPeriodo(2026, 5));
        propuestaFacturacionRepositorio.save(crearPropuestaConPeriodo(2025, 11));
        propuestaFacturacionRepositorio.flush();

        Page<PropuestaFacturacionRespuestaDto> pagina = servicio.listar(
            null, null, null, null, null,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "periodoAnio")));

        assertThat(pagina.getContent().get(0).periodoAnio()).isEqualTo(2025);
    }

    private PropuestaFacturacion crearPropuestaConPeriodo(int periodoAnio, int periodoMes) {
        PropuestaFacturacion propuesta = crearPropuesta(empresa, null, EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO);
        propuesta.setPeriodoAnio((short) periodoAnio);
        propuesta.setPeriodoMes((short) periodoMes);
        return propuesta;
    }

    /**
     * Verifica con estadísticas reales de Hibernate que el listado no dispara una consulta
     * por fila para resolver {@code factura} (LAZY): el fetch join de
     * {@code conFacturaFetch()} debe dejar el conteo de consultas fijo (COUNT + datos),
     * aunque solo una de las tres propuestas del período tenga factura asociada.
     */
    @Test
    void elListadoNoDeberiaDispararUnaConsultaPorFilaParaResolverLaFactura() {
        Statistics estadisticas = entityManager.getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        servicio.listar(2026, 2, null, null, null, PageRequest.of(0, 20));

        // Solo la consulta de datos: Spring Data JPA (PageableExecutionUtils) omite el
        // COUNT cuando el contenido devuelto es menor que el tamaño de página en la
        // primera página, porque puede inferir el total sin consultarlo.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------------------------
    // reprocesarUf — deuda-tecnica.md ítem 8/9: guard duro (solo PENDIENTE_UF), reuso de
    // ArmadorPropuesta, y transición de estado según haya o no valor UF disponible.
    // ---------------------------------------------------------------------------------------

    @Test
    void reprocesarUfDeberiaRechazarUnaPropuestaPendienteYNoLlamarAlArmador() {
        assertThatThrownBy(() -> servicio.reprocesarUf(propuestaPendiente.getId()))
            .isInstanceOf(ReglaNegocioException.class)
            .extracting(ex -> ((ReglaNegocioException) ex).getCodigo())
            .isEqualTo("PROPUESTA_NO_REPROCESABLE");

        verifyNoInteractions(armadorPropuesta);
    }

    @Test
    void reprocesarUfDeberiaRechazarUnaPropuestaFacturadaYNoLlamarAlArmador() {
        assertThatThrownBy(() -> servicio.reprocesarUf(propuestaFacturada.getId()))
            .isInstanceOf(ReglaNegocioException.class)
            .extracting(ex -> ((ReglaNegocioException) ex).getCodigo())
            .isEqualTo("PROPUESTA_NO_REPROCESABLE");

        verifyNoInteractions(armadorPropuesta);
    }

    @Test
    void reprocesarUfDeberiaRechazarUnaPropuestaAnuladaYNoLlamarAlArmador() {
        PropuestaFacturacion anulada = crearPropuestaConPeriodo(2026, 4);
        anulada.setEstado(EstadoPropuesta.ANULADA);
        propuestaFacturacionRepositorio.save(anulada);
        propuestaFacturacionRepositorio.flush();

        assertThatThrownBy(() -> servicio.reprocesarUf(anulada.getId()))
            .isInstanceOf(ReglaNegocioException.class)
            .extracting(ex -> ((ReglaNegocioException) ex).getCodigo())
            .isEqualTo("PROPUESTA_NO_REPROCESABLE");

        verifyNoInteractions(armadorPropuesta);
    }

    @Test
    void reprocesarUfDeberiaFallarConRecursoNoEncontradoSiElIdNoExiste() {
        assertThatThrownBy(() -> servicio.reprocesarUf(999_999L))
            .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(armadorPropuesta);
    }

    @Test
    void reprocesarUfDeberiaRecalcularYPasarAPendienteCuandoLaUfYaEstaDisponible() {
        PropuestaFacturacion recalculada = new PropuestaFacturacion();
        recalculada.setAcuerdoTipo(TipoAcuerdo.DESCUENTO_PORCENTAJE);
        recalculada.setAcuerdoValor(new BigDecimal("10.0000"));
        recalculada.setTasaIva(new BigDecimal("0.19"));
        recalculada.setValorUf(new BigDecimal("40340.8600"));
        recalculada.setFechaValorUf(LocalDate.of(2026, 2, 15));
        recalculada.setNetoClp(new BigDecimal("363068"));
        recalculada.setIvaClp(new BigDecimal("68983"));
        recalculada.setTotalClp(new BigDecimal("432051"));
        recalculada.setEstado(EstadoPropuesta.PENDIENTE);
        when(armadorPropuesta.armar(any())).thenReturn(recalculada);

        PropuestaFacturacionRespuestaDto dto = servicio.reprocesarUf(propuestaPendienteUf.getId());

        assertThat(dto.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(dto.netoClp()).isEqualByComparingTo("363068");
        assertThat(dto.valorUf()).isEqualByComparingTo("40340.8600");

        PropuestaFacturacion persistida = propuestaFacturacionRepositorio.findById(propuestaPendienteUf.getId()).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(persistida.getNetoClp()).isEqualByComparingTo("363068");
        assertThat(persistida.getValorUf()).isEqualByComparingTo("40340.8600");

        // Reuso real de la vía de cálculo: la entrada que llegó a ArmadorPropuesta se
        // reconstruyó desde los propios campos de la fila PENDIENTE_UF existente.
        ArgumentCaptor<EntradaArmadoPropuesta> captor = ArgumentCaptor.forClass(EntradaArmadoPropuesta.class);
        verify(armadorPropuesta).armar(captor.capture());
        EntradaArmadoPropuesta entrada = captor.getValue();
        assertThat(entrada.empresa().getId()).isEqualTo(propuestaPendienteUf.getEmpresa().getId());
        assertThat(entrada.cliente().getId()).isEqualTo(propuestaPendienteUf.getCliente().getId());
        assertThat(entrada.proyecto()).isNull();
        assertThat(entrada.origen()).isEqualTo(OrigenPropuesta.CSV);
        assertThat(entrada.periodoAnio()).isEqualTo(propuestaPendienteUf.getPeriodoAnio());
        assertThat(entrada.periodoMes()).isEqualTo(propuestaPendienteUf.getPeriodoMes());
        assertThat(entrada.fechaFacturacion()).isEqualTo(propuestaPendienteUf.getFechaFacturacion());
        assertThat(entrada.descripcion()).isEqualTo(propuestaPendienteUf.getDescripcion());
        assertThat(entrada.precioBaseNeto()).isEqualByComparingTo(propuestaPendienteUf.getPrecioBaseNeto());
        assertThat(entrada.monedaPrecio()).isEqualTo(propuestaPendienteUf.getMonedaOrigen());
    }

    @Test
    void reprocesarUfDeberiaSeguirPendienteUfSinErrorCuandoLaUfSigueSinDisponible() {
        PropuestaFacturacion recalculada = new PropuestaFacturacion();
        recalculada.setTasaIva(new BigDecimal("0.19"));
        recalculada.setNetoClp(BigDecimal.ZERO);
        recalculada.setIvaClp(BigDecimal.ZERO);
        recalculada.setTotalClp(BigDecimal.ZERO);
        recalculada.setEstado(EstadoPropuesta.PENDIENTE_UF);
        when(armadorPropuesta.armar(any())).thenReturn(recalculada);

        PropuestaFacturacionRespuestaDto dto = servicio.reprocesarUf(propuestaPendienteUf.getId());

        assertThat(dto.estado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
        assertThat(dto.netoClp()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.valorUf()).isNull();

        PropuestaFacturacion persistida = propuestaFacturacionRepositorio.findById(propuestaPendienteUf.getId()).orElseThrow();
        assertThat(persistida.getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE_UF);
    }

    @Test
    void reprocesarUfDeberiaFuncionarIgualParaUnaPropuestaDeOrigenCiclo() {
        PropuestaFacturacion pendienteUfCiclo = crearPropuestaConPeriodo(2026, 4);
        pendienteUfCiclo.setEstado(EstadoPropuesta.PENDIENTE_UF);
        pendienteUfCiclo.setNetoClp(BigDecimal.ZERO);
        pendienteUfCiclo.setIvaClp(BigDecimal.ZERO);
        pendienteUfCiclo.setTotalClp(BigDecimal.ZERO);
        propuestaFacturacionRepositorio.save(pendienteUfCiclo);
        propuestaFacturacionRepositorio.flush();

        PropuestaFacturacion recalculada = new PropuestaFacturacion();
        recalculada.setTasaIva(new BigDecimal("0.19"));
        recalculada.setValorUf(new BigDecimal("41000.0000"));
        recalculada.setFechaValorUf(pendienteUfCiclo.getFechaFacturacion());
        recalculada.setNetoClp(new BigDecimal("100000"));
        recalculada.setIvaClp(new BigDecimal("19000"));
        recalculada.setTotalClp(new BigDecimal("119000"));
        recalculada.setEstado(EstadoPropuesta.PENDIENTE);
        when(armadorPropuesta.armar(any())).thenReturn(recalculada);

        PropuestaFacturacionRespuestaDto dto = servicio.reprocesarUf(pendienteUfCiclo.getId());

        assertThat(dto.estado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(dto.origen()).isEqualTo(OrigenPropuesta.CICLO);
        assertThat(dto.netoClp()).isEqualByComparingTo("100000");

        ArgumentCaptor<EntradaArmadoPropuesta> captor = ArgumentCaptor.forClass(EntradaArmadoPropuesta.class);
        verify(armadorPropuesta).armar(captor.capture());
        assertThat(captor.getValue().origen()).isEqualTo(OrigenPropuesta.CICLO);
    }
}
