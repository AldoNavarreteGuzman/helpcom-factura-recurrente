package cl.helpcom.facturacion.e2e;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.FacturaSolicitudDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import cl.helpcom.facturacion.uf.dominio.ValorUf;
import cl.helpcom.facturacion.uf.fuente.FuenteUf;
import cl.helpcom.facturacion.uf.repositorio.ValorUfRepositorio;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base compartida de la suite E2E (docs/qa.md): un único contenedor Postgres 16 y un único
 * Redis por corrida de JVM (patrón "singleton container" — campos {@code static}, arrancados
 * una sola vez, nunca detenidos explícitamente; Testcontainers/Ryuk los limpia al terminar la
 * JVM). Como todas las clases de flujo extienden esta base SIN agregar configuración propia
 * (mismos {@code @MockitoBean}, mismas propiedades dinámicas), Spring reutiliza el MISMO
 * {@code ApplicationContext} para TODA la suite: un solo arranque de Spring + Flyway para las
 * ~9 clases de flujo, no uno por clase.
 *
 * <p><b>Determinismo:</b>
 * <ul>
 *   <li>{@link FuenteUf} (mindicador.cl) se reemplaza por un mock que SIEMPRE retorna
 *       {@link Optional#empty()}: ningún test de esta suite golpea la red. La UF disponible
 *       para un flujo se siembra directo en {@code valor_uf} vía {@link #sembrarUf}.</li>
 *   <li>{@link JwtDecoder} se reemplaza por un mock sin configurar: el post-processor
 *       {@code jwt()} de spring-security-test no lo invoca (arma la autenticación
 *       directamente), así que basta con que el bean exista para que arranque el contexto.</li>
 *   <li>El almacenamiento usa {@code AlmacenArchivosLocal} apuntando a un directorio temporal
 *       del sistema de archivos, no OCI.</li>
 * </ul>
 *
 * <p><b>Aislamiento entre tests:</b> NO se usa {@code @Transactional} de rollback — el ciclo
 * procesa cada proyecto en su propia transacción {@code REQUIRES_NEW}
 * ({@code ServicioCicloFacturacion}), que igual haría commit aunque el test envolvente
 * revirtiera, dejando datos "fantasma" solo parcialmente limpiados. En su lugar, cada test
 * arranca desde una base conocida: un {@code TRUNCATE ... RESTART IDENTITY CASCADE} de todas
 * las tablas de negocio (menos {@code empresa}/{@code parametro_sistema}, sembradas una sola
 * vez por Flyway V011 y compartidas por toda la suite) más un {@code FLUSHALL} de Redis (para
 * que un valor UF cacheado en un test no contamine a otro que reutilice la misma fecha).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
abstract class SoporteE2E {

    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    private static final Path DIRECTORIO_ALMACENAMIENTO = crearDirectorioTemporal();

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void propiedadesDinamicas(DynamicPropertyRegistry registro) {
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registro.add("app.ciclo.programado.habilitado", () -> "false");
        registro.add("app.almacenamiento.local.ruta", () -> DIRECTORIO_ALMACENAMIENTO.toString());
    }

    private static Path crearDirectorioTemporal() {
        try {
            return Files.createTempDirectory("e2e-almacenamiento-");
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio temporal de almacenamiento para el E2E.", ex);
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ValorUfRepositorio valorUfRepositorio;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected FuenteUf fuenteUf;

    private TransactionTemplate transaccionNueva;
    private long contadorRut = 70_000_000L;

    @BeforeEach
    void limpiarEstadoAntesDeCadaTest() {
        lenient().when(fuenteUf.consultarUf(any())).thenReturn(Optional.empty());
        transaccionNueva().executeWithoutResult(estado -> entityManager.createNativeQuery("""
            TRUNCATE TABLE propuesta_facturacion, ejecucion_ciclo, importacion_csv, factura,
                archivo, acuerdo_precio, proyecto, cliente, tipo_servicio, valor_uf
            RESTART IDENTITY CASCADE
            """).executeUpdate());
        redisTemplate.execute((RedisCallback<Object>) conexion -> {
            conexion.serverCommands().flushAll();
            return null;
        });
    }

    private TransactionTemplate transaccionNueva() {
        if (transaccionNueva == null) {
            transaccionNueva = new TransactionTemplate(transactionManager);
            transaccionNueva.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        }
        return transaccionNueva;
    }

    // ---------------------------------------------------------------------------------------
    // Autenticación de prueba (SeguridadConfig: roles vienen de realm_access.roles → ROLE_*)
    // ---------------------------------------------------------------------------------------

    protected RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    protected RequestPostProcessor operador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"));
    }

    protected RequestPostProcessor sinRolesReconocidos() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_INVITADO"));
    }

    // ---------------------------------------------------------------------------------------
    // RUT de prueba (módulo 11, mismo algoritmo que RutChileno) — evita colisiones entre
    // clientes creados en el mismo test.
    // ---------------------------------------------------------------------------------------

    protected String rutDePrueba() {
        return rutValido(contadorRut++);
    }

    private static String rutValido(long cuerpoNumerico) {
        String cuerpo = Long.toString(cuerpoNumerico);
        int suma = 0;
        int multiplicador = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * multiplicador;
            multiplicador = multiplicador == 7 ? 2 : multiplicador + 1;
        }
        int resto = 11 - (suma % 11);
        char digitoVerificador = resto == 11 ? '0' : resto == 10 ? 'K' : Character.forDigit(resto, 10);
        return cuerpo + "-" + digitoVerificador;
    }

    // ---------------------------------------------------------------------------------------
    // Siembra de UF — directo a la tabla, NUNCA vía FuenteUf (mockeada a Optional.empty()).
    // ---------------------------------------------------------------------------------------

    protected void sembrarUf(LocalDate fecha, String valor) {
        ValorUf valorUf = new ValorUf();
        valorUf.setFecha(fecha);
        valorUf.setValor(new BigDecimal(valor));
        valorUf.setFuente("siembra-e2e");
        valorUf.setRegistradoEn(OffsetDateTime.now());
        valorUfRepositorio.save(valorUf);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers HTTP — todo pasa por MockMvc (controlador → seguridad → servicio → persistencia
    // real), nunca se invoca un servicio directamente.
    // ---------------------------------------------------------------------------------------

    protected Long crearCliente(String rut, String razonSocial) throws Exception {
        String cuerpo = """
            {"rut":"%s","razonSocial":"%s","activo":true}
            """.formatted(rut, razonSocial);
        String respuesta = mockMvc.perform(post("/api/v1/clientes")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("id").asLong();
    }

    protected ProyectoRespuestaDto crearProyecto(
        Long clienteId, String precioBaseNeto, String monedaPrecio, String periodicidad,
        int diaFacturacion, LocalDate fechaInicio, LocalDate fechaTermino) throws Exception {
        String cuerpo = """
            {"clienteId":%d,"nombre":"Proyecto de prueba","precioBaseNeto":%s,"monedaPrecio":"%s",
             "periodicidad":"%s","diaFacturacion":%d,"fechaInicio":"%s","fechaTermino":%s,"activo":true}
            """.formatted(
            clienteId, precioBaseNeto, monedaPrecio, periodicidad, diaFacturacion, fechaInicio,
            fechaTermino == null ? "null" : "\"" + fechaTermino + "\"");
        String respuesta = mockMvc.perform(post("/api/v1/proyectos")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, ProyectoRespuestaDto.class);
    }

    protected AcuerdoPrecioRespuestaDto crearAcuerdo(
        Long proyectoId, TipoAcuerdo tipo, String valor, String moneda,
        LocalDate fechaInicio, LocalDate fechaTermino) throws Exception {
        String cuerpo = """
            {"tipo":"%s","valor":%s,"moneda":%s,"fechaInicio":"%s","fechaTermino":"%s"}
            """.formatted(
            tipo, valor, moneda == null ? "null" : "\"" + moneda + "\"", fechaInicio, fechaTermino);
        String respuesta = mockMvc.perform(post("/api/v1/proyectos/{proyectoId}/acuerdos", proyectoId)
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, AcuerdoPrecioRespuestaDto.class);
    }

    protected ResultActions ejecutarCicloSinAsumirExito(int anio, int mes) throws Exception {
        return mockMvc.perform(post("/api/v1/ciclos/ejecutar")
            .with(administrador())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"anio":%d,"mes":%d}
                """.formatted(anio, mes)));
    }

    protected ResultadoCiclo ejecutarCiclo(int anio, int mes) throws Exception {
        String respuesta = ejecutarCicloSinAsumirExito(anio, mes)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, ResultadoCiclo.class);
    }

    protected List<EjecucionCicloRespuestaDto> listarEjecucionesCiclo() throws Exception {
        String respuesta = mockMvc.perform(get("/api/v1/ciclos").with(administrador()).param("size", "50"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        PaginaRespuestaDto<EjecucionCicloRespuestaDto> pagina = objectMapper.readValue(
            respuesta, objectMapper.getTypeFactory().constructParametricType(
                PaginaRespuestaDto.class, EjecucionCicloRespuestaDto.class));
        return pagina.contenido();
    }

    protected List<PropuestaFacturacionRespuestaDto> listarPropuestas(
        Integer periodoAnio, Integer periodoMes, Long clienteId, EstadoPropuesta estado, OrigenPropuesta origen)
        throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/v1/propuestas")
            .with(administrador())
            .param("size", "200");
        if (periodoAnio != null) {
            peticion = peticion.param("periodoAnio", periodoAnio.toString());
        }
        if (periodoMes != null) {
            peticion = peticion.param("periodoMes", periodoMes.toString());
        }
        if (clienteId != null) {
            peticion = peticion.param("clienteId", clienteId.toString());
        }
        if (estado != null) {
            peticion = peticion.param("estado", estado.name());
        }
        if (origen != null) {
            peticion = peticion.param("origen", origen.name());
        }
        String respuesta = mockMvc.perform(peticion)
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        PaginaRespuestaDto<PropuestaFacturacionRespuestaDto> pagina = objectMapper.readValue(
            respuesta, objectMapper.getTypeFactory().constructParametricType(
                PaginaRespuestaDto.class, PropuestaFacturacionRespuestaDto.class));
        return pagina.contenido();
    }

    protected FacturaRespuestaDto crearFactura(String numeroFactura, LocalDate fecha, List<Long> propuestaIds) throws Exception {
        FacturaSolicitudDto solicitud = new FacturaSolicitudDto(numeroFactura, fecha, null, propuestaIds);
        String respuesta = mockMvc.perform(post("/api/v1/facturas")
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solicitud)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, FacturaRespuestaDto.class);
    }

    protected void subirPdf(Long facturaId, String nombreArchivo, byte[] contenido) throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", nombreArchivo, "application/pdf", contenido);
        mockMvc.perform(multipart("/api/v1/facturas/{id}/pdf", facturaId)
                .file(archivo)
                .with(administrador()))
            .andExpect(status().isOk());
    }

    protected ImportacionPreviewRespuestaDto previsualizarCsv(byte[] contenidoCsv) throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "importacion.csv", "text/csv", contenidoCsv);
        String respuesta = mockMvc.perform(multipart("/api/v1/importaciones/previsualizar")
                .file(archivo)
                .with(operador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, ImportacionPreviewRespuestaDto.class);
    }

    protected ImportacionCsvRespuestaDto confirmarCsv(byte[] contenidoCsv) throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "importacion.csv", "text/csv", contenidoCsv);
        String respuesta = mockMvc.perform(multipart("/api/v1/importaciones/confirmar")
                .file(archivo)
                .with(operador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, ImportacionCsvRespuestaDto.class);
    }

    protected InformeFacturacionRespuestaDto obtenerInforme(String queryString) throws Exception {
        String respuesta = mockMvc.perform(get("/api/v1/informes/facturacion" + queryString)
                .with(administrador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, InformeFacturacionRespuestaDto.class);
    }
}
