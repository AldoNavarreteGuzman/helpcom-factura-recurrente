package cl.helpcom.facturacion.proyectos.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Ejercita la consulta real de traslape ({@code buscarTraslapados}) contra una base H2 en
 * memoria (sin Docker). No cubre la exclusion constraint de PostgreSQL en sí misma — esa
 * red de seguridad la cubre {@code EsquemaBaseDatosTest} (Testcontainers). {@code @DataJpaTest}
 * no escanea {@code @Configuration} arbitrarias, por eso se importa {@link AuditoriaConfig}
 * explícitamente (si no, creado_por/creado_en quedan nulos y violan el NOT NULL).
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AcuerdoPrecioRepositorioTest {

    @Autowired
    private EmpresaRepositorio empresaRepositorio;

    @Autowired
    private ClienteRepositorio clienteRepositorio;

    @Autowired
    private ProyectoRepositorio proyectoRepositorio;

    @Autowired
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    private Long proyectoId;

    @BeforeEach
    void configurar() {
        Empresa empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setRut("77222333-4");
        cliente.setRazonSocial("Cliente de Prueba Ltda.");
        clienteRepositorio.save(cliente);

        Proyecto proyecto = new Proyecto();
        proyecto.setEmpresa(empresa);
        proyecto.setCliente(cliente);
        proyecto.setNombre("Proyecto de prueba");
        proyecto.setPrecioBaseNeto(new BigDecimal("100000.0000"));
        proyecto.setMonedaPrecio(Moneda.CLP);
        proyecto.setPeriodicidad(Periodicidad.MENSUAL);
        proyecto.setDiaFacturacion((short) 5);
        proyecto.setFechaInicio(LocalDate.of(2026, 1, 1));
        proyectoRepositorio.save(proyecto);
        proyectoId = proyecto.getId();

        AcuerdoPrecio existente = new AcuerdoPrecio();
        existente.setEmpresa(empresa);
        existente.setProyecto(proyecto);
        existente.setTipo(TipoAcuerdo.DESCUENTO_PORCENTAJE);
        existente.setValor(new BigDecimal("10"));
        existente.setFechaInicio(LocalDate.of(2026, 3, 1));
        existente.setFechaTermino(LocalDate.of(2026, 6, 30));
        acuerdoPrecioRepositorio.save(existente);
    }

    @Test
    void noDeberiaEncontrarTraslapeCuandoLasVigenciasNoSeTocan() {
        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaEncontrarTraslapeCuandoLaNuevaVigenciaContieneCompletamenteALaExistente() {
        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deberiaEncontrarTraslapeParcialCuandoLasVigenciasSeCruzanEnUnTramo() {
        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void deberiaEncontrarTraslapeCuandoLasVigenciasComparteExactamenteUnDiaDeBorde() {
        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 6, 30), LocalDate.of(2026, 8, 31), null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void noDeberiaEncontrarTraslapeElDiaInmediatamenteSiguienteAlBorde() {
        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30), null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaExcluirElPropioAcuerdoAlPasarSuIdComoExcluido() {
        AcuerdoPrecio existente = acuerdoPrecioRepositorio.findByProyectoIdAndEmpresaId(
            proyectoId, empresaRepositorio.findAll().get(0).getId()).get(0);

        List<AcuerdoPrecio> resultado = acuerdoPrecioRepositorio.buscarTraslapados(
            proyectoId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30), existente.getId());

        assertThat(resultado).isEmpty();
    }
}
