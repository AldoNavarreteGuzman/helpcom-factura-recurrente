package cl.helpcom.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.dominio.ParametroSistema;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.repositorio.ParametroSistemaRepositorio;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración del esquema completo contra PostgreSQL 16 real (Testcontainers):
 * aplica las 11 migraciones Flyway, confirma que Hibernate valida el esquema resultante
 * ({@code ddl-auto=validate}), que la semilla de {@code V011} quedó cargada, y ejercita las
 * dos restricciones a nivel de base de datos que no se pueden verificar solo leyendo el DDL:
 * la exclusion constraint de {@code acuerdo_precio} y el índice único parcial de
 * {@code propuesta_facturacion}.
 *
 * <p>Requiere Docker accesible desde la JVM (no solo desde la shell). En Windows con Docker
 * Desktop reciente, el cliente Java de Testcontainers (docker-java) puede fallar por dos
 * motivos independientes, ambos resueltos con configuración de máquina — nunca en el código
 * del proyecto ni en el {@code pom.xml}, para no romper otros entornos (CI, otras máquinas):
 * <ol>
 *   <li>El pipe por defecto {@code npipe:////./pipe/docker_engine} responde con un JSON vacío
 *       que apunta a otro pipe interno ({@code docker_cli}) en vez de servir la API — hace
 *       falta apuntar directo al pipe del motor Linux:
 *       {@code ~/.testcontainers.properties} con
 *       {@code docker.host=npipe:////./pipe/docker_engine_linux}.</li>
 *   <li>Con eso, docker-java negocia con la versión de API por defecto de su cliente (1.32),
 *       que este daemon rechaza por antigua (mínimo 1.40) — hace falta fijar una versión
 *       compatible: {@code ~/.docker-java.properties} con {@code api.version=1.41}.</li>
 * </ol>
 * Sin ambos archivos, Testcontainers falla al iniciar el contenedor con
 * "Could not find a valid Docker environment"; con ellos, esta clase corre igual que en CI o
 * en cualquier máquina con Docker Desktop expuesto a Java sin estas particularidades.
 */
@Testcontainers
@SpringBootTest
@Transactional
class EsquemaBaseDatosTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private EmpresaRepositorio empresaRepositorio;

    @Autowired
    private ParametroSistemaRepositorio parametroSistemaRepositorio;

    @Autowired
    private ClienteRepositorio clienteRepositorio;

    @Autowired
    private ProyectoRepositorio proyectoRepositorio;

    @Autowired
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    @Autowired
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    @Test
    void elContextoArrancaConLasMigracionesAplicadasYElEsquemaValidadoPorHibernate() {
        assertThat(contexto).isNotNull();
    }

    @Test
    void deberiaTenerCargadaLaSemillaDeHelpcomYSuTasaDeIva() {
        List<Empresa> empresas = empresaRepositorio.findAll();
        Empresa helpcom = empresas.stream()
            .filter(empresa -> "Helpcom Ltda.".equals(empresa.getRazonSocial()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No se encontró la empresa semilla 'Helpcom Ltda.' (V011)."));
        assertThat(helpcom.isActiva()).isTrue();

        ParametroSistema tasaIva = parametroSistemaRepositorio
            .findByEmpresaIdAndClave(helpcom.getId(), "tasa_iva")
            .orElseThrow(() -> new AssertionError("No se encontró el parámetro 'tasa_iva' de la semilla (V011)."));
        assertThat(new BigDecimal(tasaIva.getValor())).isEqualByComparingTo("0.19");
    }

    @Test
    void deberiaRechazarAcuerdosTraslapados() {
        Proyecto proyecto = crearProyectoPersistido();

        AcuerdoPrecio vigente = nuevoAcuerdo(proyecto, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        acuerdoPrecioRepositorio.saveAndFlush(vigente);

        AcuerdoPrecio traslapado = nuevoAcuerdo(proyecto, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> acuerdoPrecioRepositorio.saveAndFlush(traslapado))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deberiaRechazarDosPropuestasDeCicloParaElMismoProyectoYPeriodo() {
        Proyecto proyecto = crearProyectoPersistido();

        propuestaFacturacionRepositorio.saveAndFlush(
            nuevaPropuesta(proyecto, OrigenPropuesta.CICLO, (short) 2026, (short) 1));

        PropuestaFacturacion segundaDelCiclo =
            nuevaPropuesta(proyecto, OrigenPropuesta.CICLO, (short) 2026, (short) 1);

        assertThatThrownBy(() -> propuestaFacturacionRepositorio.saveAndFlush(segundaDelCiclo))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deberiaPermitirDosPropuestasCsvParaElMismoProyectoYPeriodo() {
        Proyecto proyecto = crearProyectoPersistido();

        assertThatCode(() -> {
            propuestaFacturacionRepositorio.saveAndFlush(
                nuevaPropuesta(proyecto, OrigenPropuesta.CSV, (short) 2026, (short) 1));
            propuestaFacturacionRepositorio.saveAndFlush(
                nuevaPropuesta(proyecto, OrigenPropuesta.CSV, (short) 2026, (short) 1));
        }).doesNotThrowAnyException();
    }

    private Proyecto crearProyectoPersistido() {
        Empresa empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.saveAndFlush(empresa);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresa);
        cliente.setRut("77222333-4");
        cliente.setRazonSocial("Cliente de Prueba Ltda.");
        clienteRepositorio.saveAndFlush(cliente);

        Proyecto proyecto = new Proyecto();
        proyecto.setEmpresa(empresa);
        proyecto.setCliente(cliente);
        proyecto.setNombre("Proyecto de prueba");
        proyecto.setPrecioBaseNeto(new BigDecimal("100000.0000"));
        proyecto.setMonedaPrecio(Moneda.CLP);
        proyecto.setPeriodicidad(Periodicidad.MENSUAL);
        proyecto.setDiaFacturacion((short) 5);
        proyecto.setFechaInicio(LocalDate.of(2026, 1, 1));
        return proyectoRepositorio.saveAndFlush(proyecto);
    }

    private AcuerdoPrecio nuevoAcuerdo(Proyecto proyecto, LocalDate fechaInicio, LocalDate fechaTermino) {
        AcuerdoPrecio acuerdo = new AcuerdoPrecio();
        acuerdo.setEmpresa(proyecto.getEmpresa());
        acuerdo.setProyecto(proyecto);
        acuerdo.setTipo(TipoAcuerdo.DESCUENTO_PORCENTAJE);
        acuerdo.setValor(new BigDecimal("10.0000"));
        acuerdo.setFechaInicio(fechaInicio);
        acuerdo.setFechaTermino(fechaTermino);
        return acuerdo;
    }

    private PropuestaFacturacion nuevaPropuesta(
        Proyecto proyecto, OrigenPropuesta origen, short periodoAnio, short periodoMes) {
        PropuestaFacturacion propuesta = new PropuestaFacturacion();
        propuesta.setEmpresa(proyecto.getEmpresa());
        propuesta.setCliente(proyecto.getCliente());
        propuesta.setProyecto(proyecto);
        propuesta.setOrigen(origen);
        propuesta.setPeriodoAnio(periodoAnio);
        propuesta.setPeriodoMes(periodoMes);
        propuesta.setFechaFacturacion(LocalDate.of(2026, periodoMes, 5));
        propuesta.setDescripcion("Propuesta de prueba");
        propuesta.setMonedaOrigen(Moneda.CLP);
        propuesta.setPrecioBaseNeto(new BigDecimal("100000.0000"));
        propuesta.setNetoClp(new BigDecimal("100000"));
        propuesta.setTasaIva(new BigDecimal("0.1900"));
        propuesta.setIvaClp(new BigDecimal("19000"));
        propuesta.setTotalClp(new BigDecimal("119000"));
        return propuesta;
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }
    }
}
