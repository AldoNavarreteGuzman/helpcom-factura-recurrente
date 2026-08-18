package cl.helpcom.facturacion.facturacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.repositorio.ArchivoRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.unit.DataSize;

/**
 * Ejercita {@code ServicioFactura.listar()} contra una base H2 real (sin Docker): el filtro
 * de rango de fechas (D1, deuda-tecnica.md ítem 3) y el orden por defecto (D3) necesitan que
 * la {@code Specification} se ejecute contra una consulta real — un mock del repositorio no
 * verifica el predicado ni el {@code ORDER BY} generados. Sigue el mismo patrón que
 * {@code ServicioPropuestaFacturacionTest}.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ServicioFacturaListarTest {

    @Autowired
    private EmpresaRepositorio empresaRepositorio;

    @Autowired
    private ClienteRepositorio clienteRepositorio;

    @Autowired
    private FacturaRepositorio facturaRepositorio;

    @Autowired
    private ArchivoRepositorio archivoRepositorio;

    @Autowired
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    private ServicioFactura servicio;

    @BeforeEach
    void configurar() {
        Empresa empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        ContextoEmpresa contextoEmpresa = mock(ContextoEmpresa.class);
        when(contextoEmpresa.obtenerEmpresaId()).thenReturn(empresa.getId());

        PropiedadesAlmacenamiento propiedades = new PropiedadesAlmacenamiento(null, DataSize.ofMegabytes(1), null, null);
        servicio = new ServicioFactura(
            facturaRepositorio, archivoRepositorio, propuestaFacturacionRepositorio,
            empresaRepositorio, mock(AlmacenArchivos.class), propiedades, contextoEmpresa);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setRut("11111111-1");
        cliente.setRazonSocial("Cliente A SpA");
        clienteRepositorio.save(cliente);

        guardarFactura(empresa, "F-ENE", LocalDate.of(2026, 1, 10));
        guardarFactura(empresa, "F-FEB", LocalDate.of(2026, 2, 10));
        guardarFactura(empresa, "F-MAR", LocalDate.of(2026, 3, 10));
    }

    private void guardarFactura(Empresa empresa, String numero, LocalDate fecha) {
        Factura factura = new Factura();
        factura.setEmpresa(empresa);
        factura.setNumeroFactura(numero);
        factura.setFechaFactura(fecha);
        facturaRepositorio.save(factura);
    }

    @Test
    void sinFiltroDeFechaDeberiaTraerTodasOrdenadasPorFechaDescendente() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(null, null, null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-MAR", "F-FEB", "F-ENE");
    }

    @Test
    void conSoloFechaDesdeDeberiaExcluirLasAnteriores() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            null, LocalDate.of(2026, 2, 1), null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-MAR", "F-FEB");
    }

    @Test
    void conSoloFechaHastaDeberiaExcluirLasPosteriores() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            null, null, LocalDate.of(2026, 2, 28), null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-FEB", "F-ENE");
    }

    @Test
    void conAmbosLimitesDeberiaAcotarAlRangoInclusive() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            null, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 2, 10), null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-FEB");
    }

    @Test
    void elRangoDeFechaDeberiaCombinarseConElFiltroDeNumero() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            "FEB", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-FEB");
    }

    @Test
    void unSortExplicitoNoDeberiaSerPisadoPorElOrdenPorDefecto() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            null, null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "fechaFactura")));

        assertThat(pagina.getContent()).extracting(FacturaRespuestaDto::numeroFactura)
            .containsExactly("F-ENE", "F-FEB", "F-MAR");
    }

    @Test
    void sinResultadosDeberiaRetornarPaginaVacia() {
        Page<FacturaRespuestaDto> pagina = servicio.listar(
            null, LocalDate.of(2027, 1, 1), null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).isEmpty();
    }
}
