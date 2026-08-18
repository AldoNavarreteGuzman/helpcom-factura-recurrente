package cl.helpcom.facturacion.informes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.informes.dto.FiltroInformeFacturacion;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionClienteResumenDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionResumenDto;
import cl.helpcom.facturacion.informes.repositorio.RepositorioResumenFacturacion;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

/**
 * Ejercita el informe contra una base H2 real (sin Docker): las agregaciones SQL
 * (SUM/COUNT/GROUP BY) de {@link RepositorioResumenFacturacion} no se pueden verificar de
 * forma confiable con mocks — necesitan una base de datos real ejecutando la consulta.
 * {@code ServicioInformeFacturacion} se instancia a mano (no vía Spring) porque
 * {@code @DataJpaTest} no escanea {@code @Service}; solo {@code ContextoEmpresa} se mockea,
 * para no depender del id fijo que hoy retorna (podría no coincidir con el id real que H2
 * le asigna a la empresa sembrada en cada método de prueba).
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServicioInformeFacturacionTest {

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

    private ServicioInformeFacturacion servicio;
    private Cliente clienteA;
    private Cliente clienteB;

    @BeforeEach
    void configurar() {
        Empresa empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        ContextoEmpresa contextoEmpresa = mock(ContextoEmpresa.class);
        when(contextoEmpresa.obtenerEmpresaId()).thenReturn(empresa.getId());
        servicio = new ServicioInformeFacturacion(
            propuestaFacturacionRepositorio, new RepositorioResumenFacturacion(entityManager), contextoEmpresa);

        clienteA = new Cliente();
        clienteA.setEmpresa(empresa);
        clienteA.setRut("11111111-1");
        clienteA.setRazonSocial("Cliente A SpA");
        clienteRepositorio.save(clienteA);

        clienteB = new Cliente();
        clienteB.setEmpresa(empresa);
        clienteB.setRut("22222222-2");
        clienteB.setRazonSocial("Cliente B Ltda.");
        clienteRepositorio.save(clienteB);

        Factura factura = new Factura();
        factura.setEmpresa(empresa);
        factura.setNumeroFactura("F-001");
        factura.setFechaFactura(LocalDate.of(2026, 3, 1));
        facturaRepositorio.save(factura);

        // Período 2026-02: PENDIENTE (cliente A), FACTURADA (cliente A, ligada a F-001),
        // PENDIENTE_UF (cliente B) y ANULADA (cliente B) — la mezcla que pide la tarea.
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, clienteA, null, 2026, 2, EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO,
            "100000", "19000", "119000"));
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, clienteA, factura, 2026, 2, EstadoPropuesta.FACTURADA, OrigenPropuesta.CICLO,
            "200000", "38000", "238000"));
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, clienteB, null, 2026, 2, EstadoPropuesta.PENDIENTE_UF, OrigenPropuesta.CSV,
            "0", "0", "0"));
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, clienteB, null, 2026, 2, EstadoPropuesta.ANULADA, OrigenPropuesta.CSV,
            "50000", "9500", "59500"));
        // Período 2026-03: para probar filtros de período exacto y de rango.
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, clienteA, null, 2026, 3, EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO,
            "300000", "57000", "357000"));

        propuestaFacturacionRepositorio.flush();
    }

    private PropuestaFacturacion crearPropuesta(
        Empresa empresa, Cliente cliente, Factura factura, int anio, int mes,
        EstadoPropuesta estado, OrigenPropuesta origen, String neto, String iva, String total) {
        PropuestaFacturacion propuesta = new PropuestaFacturacion();
        propuesta.setEmpresa(empresa);
        propuesta.setCliente(cliente);
        propuesta.setFactura(factura);
        propuesta.setOrigen(origen);
        propuesta.setPeriodoAnio((short) anio);
        propuesta.setPeriodoMes((short) mes);
        propuesta.setFechaFacturacion(LocalDate.of(anio, mes, 15));
        propuesta.setDescripcion("Servicio de prueba");
        propuesta.setMonedaOrigen(Moneda.CLP);
        propuesta.setPrecioBaseNeto(new BigDecimal(neto));
        propuesta.setTasaIva(new BigDecimal("0.19"));
        propuesta.setNetoClp(new BigDecimal(neto));
        propuesta.setIvaClp(new BigDecimal(iva));
        propuesta.setTotalClp(new BigDecimal(total));
        propuesta.setEstado(estado);
        return propuesta;
    }

    private FiltroInformeFacturacion filtro(
        Integer periodoAnio, Integer periodoMes, Integer anioMesDesde, Integer anioMesHasta,
        Long clienteId, List<EstadoPropuesta> estados, OrigenPropuesta origen, Boolean facturada) {
        return new FiltroInformeFacturacion(
            periodoAnio, periodoMes, anioMesDesde, anioMesHasta, clienteId, estados, origen, facturada);
    }

    @Test
    void losTotalesDeberianExcluirPendienteUfYAnuladaYLaCantidadPendienteUfQuedaAparte() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, null, null, null), PageRequest.of(0, 20));
        InformeFacturacionResumenDto resumen = respuesta.resumen();

        assertThat(resumen.cantidadTotal()).isEqualTo(4);
        assertThat(resumen.cantidadPendienteUf()).isEqualTo(1);
        assertThat(resumen.cantidadPorEstado().get(EstadoPropuesta.PENDIENTE)).isEqualTo(1);
        assertThat(resumen.cantidadPorEstado().get(EstadoPropuesta.FACTURADA)).isEqualTo(1);
        assertThat(resumen.cantidadPorEstado().get(EstadoPropuesta.PENDIENTE_UF)).isEqualTo(1);
        assertThat(resumen.cantidadPorEstado().get(EstadoPropuesta.ANULADA)).isEqualTo(1);

        // Solo PENDIENTE + FACTURADA: 100000+200000 / 19000+38000 / 119000+238000.
        assertThat(resumen.netoClp()).isEqualByComparingTo("300000");
        assertThat(resumen.ivaClp()).isEqualByComparingTo("57000");
        assertThat(resumen.totalClp()).isEqualByComparingTo("357000");
    }

    @Test
    void elDesglosePorClienteDeberiaExcluirClientesSinPropuestasFacturables() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, null, null, null), PageRequest.of(0, 20));

        List<InformeFacturacionClienteResumenDto> porCliente = respuesta.resumen().porCliente();

        assertThat(porCliente).hasSize(1);
        assertThat(porCliente.get(0).clienteRazonSocial()).isEqualTo("Cliente A SpA");
        assertThat(porCliente.get(0).cantidad()).isEqualTo(2);
        assertThat(porCliente.get(0).totalClp()).isEqualByComparingTo("357000");
    }

    @Test
    void losTotalesDeberianQuedarEnCeroCuandoElFiltroSoloDejaFilasNoFacturables() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, List.of(EstadoPropuesta.ANULADA), null, null), PageRequest.of(0, 20));

        assertThat(respuesta.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(respuesta.resumen().cantidadPorEstado().get(EstadoPropuesta.ANULADA)).isEqualTo(1);
        assertThat(respuesta.resumen().netoClp()).isEqualByComparingTo("0");
        assertThat(respuesta.resumen().totalClp()).isEqualByComparingTo("0");
        assertThat(respuesta.detalle().contenido()).hasSize(1);
    }

    @Test
    void elFiltroPorClienteDeberiaAcotarElDetalleYElResumen() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, clienteA.getId(), null, null, null), PageRequest.of(0, 20));

        assertThat(respuesta.resumen().cantidadTotal()).isEqualTo(2);
        assertThat(respuesta.detalle().contenido()).allMatch(d -> d.clienteId().equals(clienteA.getId()));
    }

    @Test
    void elFiltroPorOrigenDeberiaAcotarElDetalleYElResumen() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, null, OrigenPropuesta.CSV, null), PageRequest.of(0, 20));

        assertThat(respuesta.resumen().cantidadTotal()).isEqualTo(2);
        assertThat(respuesta.resumen().netoClp()).isEqualByComparingTo("0");
        assertThat(respuesta.detalle().contenido()).allMatch(d -> d.origen() == OrigenPropuesta.CSV);
    }

    @Test
    void elFiltroPorFacturadaDeberiaDejarSoloLaPropuestaFacturada() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, null, null, true), PageRequest.of(0, 20));

        assertThat(respuesta.detalle().contenido()).hasSize(1);
        assertThat(respuesta.detalle().contenido().get(0).numeroFactura()).isEqualTo("F-001");
        assertThat(respuesta.detalle().contenido().get(0).fechaFactura()).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void unaFilaNoFacturadaDeberiaTenerNumeroYFechaDeFacturaNulos() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 2, null, null, null, List.of(EstadoPropuesta.PENDIENTE), null, null),
            PageRequest.of(0, 20));

        assertThat(respuesta.detalle().contenido()).hasSize(1);
        assertThat(respuesta.detalle().contenido().get(0).numeroFactura()).isNull();
        assertThat(respuesta.detalle().contenido().get(0).fechaFactura()).isNull();
    }

    /**
     * Verifica con estadísticas reales de Hibernate (no un mock) que el detalle no dispara
     * una consulta por fila para resolver {@code factura} (LAZY): el fetch join de
     * {@code conFacturaFetch()} debe dejar el conteo de consultas fijo, sin importar cuántas
     * de las filas del período tengan una factura asociada.
     */
    @Test
    void elListadoDeDetalleNoDeberiaDispararUnaConsultaPorFilaParaResolverLaFactura() {
        org.hibernate.stat.Statistics estadisticas = entityManager.getEntityManagerFactory()
            .unwrap(org.hibernate.SessionFactory.class)
            .getStatistics();
        estadisticas.setStatisticsEnabled(true);
        estadisticas.clear();

        servicio.generar(filtro(2026, 2, null, null, null, null, null, null), PageRequest.of(0, 20));

        // Consultas esperadas, fijas: datos (con fetch de factura) + resumen por estado +
        // resumen por cliente. No hay COUNT: Spring Data JPA (PageableExecutionUtils) lo omite
        // cuando el contenido devuelto es menor que el tamaño de página en la primera página,
        // porque puede inferir el total sin consultarlo. Si hubiera N+1, el conteo escalaría
        // con las 4 filas del período (o con la que tiene factura), no se quedaría fijo.
        assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void elRangoDePeriodoDeberiaIncluirAmbosPeriodos() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(null, null, 202602, 202603, null, null, null, null), PageRequest.of(0, 20));

        assertThat(respuesta.resumen().cantidadTotal()).isEqualTo(5);
        assertThat(respuesta.resumen().netoClp()).isEqualByComparingTo("600000");
        assertThat(respuesta.resumen().ivaClp()).isEqualByComparingTo("114000");
        assertThat(respuesta.resumen().totalClp()).isEqualByComparingTo("714000");
    }

    /**
     * D3 (deuda-tecnica.md ítem 3, estandares-de-codigo.md §3.8): sin {@code sort} explícito,
     * el detalle ordena por período descendente. La propuesta de 2026-03 se sembró última (id
     * más alto que las de 2026-02, insertadas antes) — si no hubiera orden por defecto, el
     * resultado natural de la base sería por id ascendente y quedaría al final, no al inicio.
     */
    @Test
    void elDetalleDeberiaOrdenarPorPeriodoDescendenteSinSortExplicito() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(null, null, 202602, 202603, null, null, null, null), PageRequest.of(0, 20));

        assertThat(respuesta.detalle().contenido()).hasSize(5);
        assertThat(respuesta.detalle().contenido().get(0).periodoAnio()).isEqualTo(2026);
        assertThat(respuesta.detalle().contenido().get(0).periodoMes()).isEqualTo(3);
    }

    @Test
    void elPeriodoExactoDeberiaExcluirElOtroPeriodo() {
        InformeFacturacionRespuestaDto respuesta = servicio.generar(
            filtro(2026, 3, null, null, null, null, null, null), PageRequest.of(0, 20));

        assertThat(respuesta.resumen().cantidadTotal()).isEqualTo(1);
        assertThat(respuesta.resumen().totalClp()).isEqualByComparingTo("357000");
    }

    @Test
    void laPaginacionDelDetalleDeberiaRespetarElTamanoDePagina() {
        InformeFacturacionRespuestaDto primeraPagina = servicio.generar(
            filtro(2026, 2, null, null, null, null, null, null), PageRequest.of(0, 2, Sort.by("id")));

        assertThat(primeraPagina.detalle().contenido()).hasSize(2);
        assertThat(primeraPagina.detalle().total()).isEqualTo(4);
        assertThat(primeraPagina.detalle().tamano()).isEqualTo(2);

        InformeFacturacionRespuestaDto segundaPagina = servicio.generar(
            filtro(2026, 2, null, null, null, null, null, null), PageRequest.of(1, 2, Sort.by("id")));

        assertThat(segundaPagina.detalle().contenido()).hasSize(2);
    }

    @Test
    void deberiaRechazarUnRangoDePeriodoInvalido() {
        assertThatThrownBy(() -> servicio.generar(
                filtro(null, null, 202603, 202601, null, null, null, null), PageRequest.of(0, 20)))
            .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void deberiaRechazarUnAnioMesConMesFueraDeRango() {
        assertThatThrownBy(() -> servicio.generar(
                filtro(null, null, 202613, null, null, null, null, null), PageRequest.of(0, 20)))
            .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void exportarDetalleCsvDeberiaIncluirEncabezadoYFilasSinReformatearMontos() {
        byte[] csv = servicio.exportarDetalleCsv(filtro(2026, 2, null, null, null, null, null, null));
        String contenido = new String(csv, StandardCharsets.UTF_8);
        List<String> lineas = contenido.lines().toList();

        assertThat(lineas.get(0)).isEqualTo(
            "id;cliente;proyecto;descripcion;periodo;fecha_facturacion;moneda_origen;valor_uf;neto_clp;iva_clp;"
                + "total_clp;estado;origen;numero_factura");
        assertThat(lineas).hasSize(5);
        assertThat(contenido).contains("238000").contains("F-001").contains("Cliente A SpA");
    }
}
