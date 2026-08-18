package cl.helpcom.facturacion.importacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.dominio.ParametroSistema;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.repositorio.ParametroSistemaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.calculo.CalculadoraFacturacion;
import cl.helpcom.facturacion.facturacion.dominio.Archivo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.ArchivoRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.importacion.csv.LectorCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import cl.helpcom.facturacion.importacion.dominio.ImportacionCsv;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewFilaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.importacion.repositorio.ImportacionCsvRepositorio;
import cl.helpcom.facturacion.importacion.validacion.ValidadorFilaCsv;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.servicio.ServicioUf;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Sociable-unit: {@link LectorCsv}, {@link ValidadorFilaCsv} y {@link ArmadorPropuesta} son
 * instancias reales (como en {@code ServicioCicloFacturacionTest}), conectadas a repositorios
 * mockeados, para ejercitar el flujo completo de previsualizar/confirmar sin base de datos.
 */
@ExtendWith(MockitoExtension.class)
class ServicioImportacionCsvTest {

    private static final Long EMPRESA_ID = 1L;
    private static final String RUT_VALIDO = "11111111-1";
    private static final String RUT_INEXISTENTE = "11222333-9";

    @Mock
    private ClienteRepositorio clienteRepositorio;

    @Mock
    private ProyectoRepositorio proyectoRepositorio;

    @Mock
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    @Mock
    private ImportacionCsvRepositorio importacionCsvRepositorio;

    @Mock
    private ArchivoRepositorio archivoRepositorio;

    @Mock
    private AlmacenArchivos almacenArchivos;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    @Mock
    private ContextoEmpresa contextoEmpresa;

    @Mock
    private AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;

    @Mock
    private ParametroSistemaRepositorio parametroSistemaRepositorio;

    @Mock
    private ServicioUf servicioUf;

    private ServicioImportacionCsv servicio;
    private Empresa empresa;

    @BeforeEach
    void configurar() {
        ArmadorPropuesta armadorPropuesta = new ArmadorPropuesta(
            acuerdoPrecioRepositorio, parametroSistemaRepositorio, servicioUf, new CalculadoraFacturacion());
        ValidadorFilaCsv validadorFilaCsv = new ValidadorFilaCsv(
            clienteRepositorio, proyectoRepositorio, propuestaFacturacionRepositorio);
        servicio = new ServicioImportacionCsv(
            new LectorCsv(), validadorFilaCsv, armadorPropuesta, propuestaFacturacionRepositorio,
            importacionCsvRepositorio, archivoRepositorio, almacenArchivos, empresaRepositorio, contextoEmpresa);

        lenient().when(contextoEmpresa.obtenerEmpresaId()).thenReturn(EMPRESA_ID);

        empresa = new Empresa();
        empresa.setId(EMPRESA_ID);
        lenient().when(empresaRepositorio.getReferenceById(EMPRESA_ID)).thenReturn(empresa);

        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setRut(RUT_VALIDO);
        cliente.setActivo(true);
        lenient().when(clienteRepositorio.findByEmpresaIdAndRut(EMPRESA_ID, RUT_VALIDO)).thenReturn(Optional.of(cliente));

        ParametroSistema tasaIva = new ParametroSistema();
        tasaIva.setClave("tasa_iva");
        tasaIva.setValor("0.19");
        lenient().when(parametroSistemaRepositorio.findByEmpresaIdAndClave(EMPRESA_ID, "tasa_iva"))
            .thenReturn(Optional.of(tasaIva));

        lenient().when(propuestaFacturacionRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AtomicLong secuencia = new AtomicLong(1);
        lenient().when(importacionCsvRepositorio.save(any())).thenAnswer(inv -> {
            ImportacionCsv importacion = inv.getArgument(0);
            importacion.setId(secuencia.getAndIncrement());
            return importacion;
        });

        lenient().when(almacenArchivos.guardar(anyString(), anyString(), any()))
            .thenReturn(new ReferenciaArchivo("clave-objeto.csv", "importacion.csv", "text/csv", 100L));
        lenient().when(archivoRepositorio.save(any())).thenAnswer(inv -> {
            Archivo archivo = inv.getArgument(0);
            archivo.setId(99L);
            return archivo;
        });
    }

    private String csvMixto() {
        return """
            rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
            %s;;Fila OK;2026-02;15-02-2026;UF;12;
            %s;;Fila con fecha fuera de periodo;2026-02;15-03-2026;UF;12;
            %s;;Fila con UF no disponible;2026-05;15-05-2026;UF;12;
            %s;;Fila con RUT inexistente;2026-02;15-02-2026;UF;12;
            %s;;Fila con moneda invalida;2026-02;15-02-2026;USD;12;
            %s;;Fila con monto invalido;2026-02;15-02-2026;UF;0;
            """.formatted(RUT_VALIDO, RUT_VALIDO, RUT_VALIDO, RUT_INEXISTENTE, RUT_VALIDO, RUT_VALIDO);
    }

    private MockMultipartFile archivoCsv(String contenido) {
        return new MockMultipartFile(
            "archivo", "importacion.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void previsualizarDeberiaClasificarUnArchivoConFilasOkAdvertenciaYError() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 5, 15)))
            .thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 5, 15)));

        ImportacionPreviewRespuestaDto respuesta = servicio.previsualizar(archivoCsv(csvMixto()));

        assertThat(respuesta.resumen().totalFilas()).isEqualTo(6);
        assertThat(respuesta.resumen().filasOk()).isEqualTo(1);
        assertThat(respuesta.resumen().filasAdvertencia()).isEqualTo(2);
        assertThat(respuesta.resumen().filasError()).isEqualTo(3);

        List<ImportacionPreviewFilaDto> filas = respuesta.filas();
        assertThat(filas.get(0).estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(filas.get(0).netoClp()).isEqualByComparingTo("480000");

        assertThat(filas.get(1).estado()).isEqualTo(EstadoFilaCsv.ADVERTENCIA);
        assertThat(filas.get(1).mensajes()).anyMatch(m -> m.contains("no coincide con el período"));

        assertThat(filas.get(2).estado()).isEqualTo(EstadoFilaCsv.ADVERTENCIA);
        assertThat(filas.get(2).mensajes()).anyMatch(m -> m.contains("PENDIENTE_UF"));
        assertThat(filas.get(2).netoClp()).isEqualByComparingTo("0");

        assertThat(filas.get(3).estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(filas.get(3).netoClp()).isNull();
        assertThat(filas.get(4).estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(filas.get(5).estado()).isEqualTo(EstadoFilaCsv.ERROR);
    }

    @Test
    void previsualizarNoDeberiaPersistirNada() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));

        servicio.previsualizar(archivoCsv(csvMixto()));

        verify(propuestaFacturacionRepositorio, never()).save(any());
        verify(importacionCsvRepositorio, never()).save(any());
        verify(almacenArchivos, never()).guardar(anyString(), anyString(), any());
    }

    @Test
    void confirmarDeberiaPersistirSoloLasFilasValidasYQuedarEnEstadoParcial() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 5, 15)))
            .thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 5, 15)));

        ImportacionCsvRespuestaDto respuesta = servicio.confirmar(archivoCsv(csvMixto()));

        assertThat(respuesta.totalFilas()).isEqualTo(6);
        assertThat(respuesta.filasOk()).isEqualTo(3);
        assertThat(respuesta.filasError()).isEqualTo(3);
        assertThat(respuesta.estado()).isEqualTo(EstadoImportacionCsv.PARCIAL);

        verify(propuestaFacturacionRepositorio, times(3)).save(any());
        verify(almacenArchivos).guardar(eq("importacion.csv"), eq("text/csv"), any());
        verify(archivoRepositorio).save(any());

        ArgumentCaptor<ImportacionCsv> captor = ArgumentCaptor.forClass(ImportacionCsv.class);
        verify(importacionCsvRepositorio).save(captor.capture());
        assertThat(captor.getValue().getTotalFilas()).isEqualTo(6);
        assertThat(captor.getValue().getFilasOk()).isEqualTo(3);
        assertThat(captor.getValue().getFilasError()).isEqualTo(3);
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoImportacionCsv.PARCIAL);
    }

    @Test
    void confirmarDeberiaDejarLaFilaConUfNoDisponibleEnPendienteUfConMontosEnCero() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 5, 15)))
            .thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 5, 15)));

        servicio.confirmar(archivoCsv(csvMixto()));

        ArgumentCaptor<PropuestaFacturacion> captor = ArgumentCaptor.forClass(PropuestaFacturacion.class);
        verify(propuestaFacturacionRepositorio, times(3)).save(captor.capture());
        PropuestaFacturacion pendienteUf = captor.getAllValues().stream()
            .filter(p -> p.getEstado() == EstadoPropuesta.PENDIENTE_UF)
            .findFirst()
            .orElseThrow();

        assertThat(pendienteUf.getNetoClp()).isEqualByComparingTo("0");
        assertThat(pendienteUf.getIvaClp()).isEqualByComparingTo("0");
        assertThat(pendienteUf.getTotalClp()).isEqualByComparingTo("0");
        assertThat(pendienteUf.getOrigen()).isEqualTo(OrigenPropuesta.CSV);
    }

    @Test
    void confirmarDeberiaExponerElContadorRealDePendienteUfComoSubconjuntoDeFilasOk() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));
        when(servicioUf.obtenerValorUf(LocalDate.of(2026, 5, 15)))
            .thenThrow(new ValorUfNoDisponibleException(LocalDate.of(2026, 5, 15)));

        ImportacionCsvRespuestaDto respuesta = servicio.confirmar(archivoCsv(csvMixto()));

        // csvMixto: filas 1 y 2 quedan PENDIENTE (con valor real); fila 3 queda PENDIENTE_UF;
        // filas 4-6 son ERROR y no se importan.
        assertThat(respuesta.filasOk()).isEqualTo(3);
        assertThat(respuesta.cantidadPendienteUf()).isEqualTo(1);
        assertThat(respuesta.filasOk() - respuesta.cantidadPendienteUf()).isEqualTo(2);
        assertThat(respuesta.filasError()).isEqualTo(3);
    }

    @Test
    void confirmarDeberiaReflejarUnContadorRealMenorAlEstimadoCuandoLaUfSeVuelveDisponibleEntrePrevisualizarYConfirmar() {
        LocalDate fecha = LocalDate.of(2026, 5, 15);
        String csv = """
            rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
            %s;;Fila cuya UF se resuelve despues;2026-05;15-05-2026;UF;12;
            """.formatted(RUT_VALIDO);

        // La UF no está disponible en la previsualización, pero sí al confirmar (p. ej. se
        // publicó entre medio) — mismo mock, dos llamadas sucesivas con distinta respuesta.
        when(servicioUf.obtenerValorUf(fecha))
            .thenThrow(new ValorUfNoDisponibleException(fecha))
            .thenReturn(new BigDecimal("40000"));

        ImportacionPreviewRespuestaDto preview = servicio.previsualizar(archivoCsv(csv));
        assertThat(preview.filas().get(0).estado()).isEqualTo(EstadoFilaCsv.ADVERTENCIA);
        assertThat(preview.filas().get(0).mensajes()).anyMatch(m -> m.contains("PENDIENTE_UF"));

        ImportacionCsvRespuestaDto respuesta = servicio.confirmar(archivoCsv(csv));

        assertThat(respuesta.filasOk()).isEqualTo(1);
        assertThat(respuesta.cantidadPendienteUf()).isZero();

        ArgumentCaptor<PropuestaFacturacion> captor = ArgumentCaptor.forClass(PropuestaFacturacion.class);
        verify(propuestaFacturacionRepositorio).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoPropuesta.PENDIENTE);
        assertThat(captor.getValue().getNetoClp()).isEqualByComparingTo("480000");
    }

    @Test
    void confirmarDeberiaQuedarProcesadaCuandoNoHayFilasConError() {
        when(servicioUf.obtenerValorUf(any())).thenReturn(new BigDecimal("40000"));
        String csv = """
            rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
            %s;;Fila OK;2026-02;15-02-2026;UF;12;
            """.formatted(RUT_VALIDO);

        ImportacionCsvRespuestaDto respuesta = servicio.confirmar(archivoCsv(csv));

        assertThat(respuesta.estado()).isEqualTo(EstadoImportacionCsv.PROCESADA);
        assertThat(respuesta.filasError()).isZero();
    }

    @Test
    void confirmarDeberiaQuedarRechazadaCuandoTodasLasFilasTienenError() {
        String csv = """
            rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
            %s;;Fila con RUT inexistente;2026-02;15-02-2026;UF;12;
            """.formatted(RUT_INEXISTENTE);

        ImportacionCsvRespuestaDto respuesta = servicio.confirmar(archivoCsv(csv));

        assertThat(respuesta.estado()).isEqualTo(EstadoImportacionCsv.RECHAZADA);
        assertThat(respuesta.filasOk()).isZero();
        verify(propuestaFacturacionRepositorio, never()).save(any());
    }

    @Test
    void listarDeberiaDelegarEnElRepositorioFiltrandoPorEmpresa() {
        ImportacionCsv importacion = new ImportacionCsv();
        importacion.setId(1L);
        importacion.setEmpresa(empresa);
        importacion.setNombreArchivo("importacion.csv");
        importacion.setFechaImportacion(OffsetDateTime.now());
        importacion.setEstado(EstadoImportacionCsv.PROCESADA);
        when(importacionCsvRepositorio.findByEmpresaIdOrderByFechaImportacionDesc(eq(EMPRESA_ID), any()))
            .thenReturn(new PageImpl<>(List.of(importacion)));

        Page<ImportacionCsvRespuestaDto> pagina = servicio.listar(PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getContent().get(0).nombreArchivo()).isEqualTo("importacion.csv");
    }
}
