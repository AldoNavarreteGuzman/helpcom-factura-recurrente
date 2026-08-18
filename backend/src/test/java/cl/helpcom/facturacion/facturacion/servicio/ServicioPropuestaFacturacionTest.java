package cl.helpcom.facturacion.facturacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private Cliente cliente;
    private Empresa empresa;

    @BeforeEach
    void configurar() {
        empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        ContextoEmpresa contextoEmpresa = mock(ContextoEmpresa.class);
        when(contextoEmpresa.obtenerEmpresaId()).thenReturn(empresa.getId());
        servicio = new ServicioPropuestaFacturacion(propuestaFacturacionRepositorio, contextoEmpresa);

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

        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, factura, EstadoPropuesta.FACTURADA, OrigenPropuesta.CICLO));
        propuestaFacturacionRepositorio.save(crearPropuesta(
            empresa, null, EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO));
        propuestaFacturacionRepositorio.save(crearPropuesta(
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
}
