package cl.helpcom.facturacion.facturacion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import cl.helpcom.facturacion.facturacion.dominio.Archivo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.FacturaSolicitudDto;
import cl.helpcom.facturacion.facturacion.repositorio.ArchivoRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ServicioFacturaTest {

    @Mock
    private FacturaRepositorio facturaRepositorio;

    @Mock
    private ArchivoRepositorio archivoRepositorio;

    @Mock
    private PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    @Mock
    private EmpresaRepositorio empresaRepositorio;

    @Mock
    private AlmacenArchivos almacenArchivos;

    @Mock
    private ContextoEmpresa contextoEmpresa;

    private ServicioFactura servicio;
    private Empresa empresa;
    private Cliente cliente;

    @BeforeEach
    void configurar() {
        PropiedadesAlmacenamiento propiedades = new PropiedadesAlmacenamiento(null, DataSize.ofMegabytes(1), null, null);
        servicio = new ServicioFactura(
            facturaRepositorio, archivoRepositorio, propuestaFacturacionRepositorio,
            empresaRepositorio, almacenArchivos, propiedades, contextoEmpresa);

        lenient().when(contextoEmpresa.obtenerEmpresaId()).thenReturn(1L);
        empresa = new Empresa();
        empresa.setId(1L);
        lenient().when(empresaRepositorio.getReferenceById(1L)).thenReturn(empresa);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setEmpresa(empresa);
        cliente.setRazonSocial("Cliente de Prueba SpA");

        lenient().when(facturaRepositorio.save(any())).thenAnswer(inv -> {
            Factura factura = inv.getArgument(0);
            factura.setId(500L);
            return factura;
        });
    }

    private PropuestaFacturacion propuestaPendiente(Long id) {
        PropuestaFacturacion propuesta = new PropuestaFacturacion();
        propuesta.setId(id);
        propuesta.setEmpresa(empresa);
        propuesta.setCliente(cliente);
        propuesta.setEstado(EstadoPropuesta.PENDIENTE);
        propuesta.setPeriodoAnio((short) 2026);
        propuesta.setPeriodoMes((short) 2);
        propuesta.setFechaFacturacion(LocalDate.of(2026, 2, 15));
        propuesta.setDescripcion("Servicio de prueba");
        propuesta.setNetoClp(new BigDecimal("100000"));
        propuesta.setIvaClp(new BigDecimal("19000"));
        propuesta.setTotalClp(new BigDecimal("119000"));
        return propuesta;
    }

    @Test
    void deberiaCrearLaFacturaYDejarLaPropuestaFacturadaConFacturaIdSeteado() {
        PropuestaFacturacion propuesta = propuestaPendiente(100L);
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(propuesta));

        FacturaSolicitudDto solicitud = new FacturaSolicitudDto("F-001", LocalDate.of(2026, 3, 1), null, List.of(100L));

        FacturaRespuestaDto respuesta = servicio.crear(solicitud);

        assertThat(propuesta.getEstado()).isEqualTo(EstadoPropuesta.FACTURADA);
        assertThat(propuesta.getFactura()).isNotNull();
        assertThat(propuesta.getFactura().getId()).isEqualTo(500L);
        assertThat(respuesta.numeroFactura()).isEqualTo("F-001");
        assertThat(respuesta.clienteId()).isEqualTo(10L);
        assertThat(respuesta.propuestas()).hasSize(1);
    }

    @Test
    void deberiaAgruparVariasPropuestasEnUnaSolaFactura() {
        PropuestaFacturacion primera = propuestaPendiente(100L);
        PropuestaFacturacion segunda = propuestaPendiente(101L);
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(primera));
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(101L, 1L)).thenReturn(Optional.of(segunda));

        FacturaSolicitudDto solicitud = new FacturaSolicitudDto(
            "F-002", LocalDate.of(2026, 3, 1), "Agrupa dos períodos", List.of(100L, 101L));

        FacturaRespuestaDto respuesta = servicio.crear(solicitud);

        assertThat(respuesta.propuestas()).hasSize(2);
        assertThat(primera.getEstado()).isEqualTo(EstadoPropuesta.FACTURADA);
        assertThat(segunda.getEstado()).isEqualTo(EstadoPropuesta.FACTURADA);
        assertThat(primera.getFactura()).isSameAs(segunda.getFactura());
    }

    @Test
    void deberiaRechazarConReglaNegocioExceptionCuandoElNumeroYaExiste() {
        when(facturaRepositorio.existsByEmpresaIdAndNumeroFactura(1L, "F-DUP")).thenReturn(true);

        FacturaSolicitudDto solicitud = new FacturaSolicitudDto("F-DUP", LocalDate.of(2026, 3, 1), null, List.of(100L));

        assertThatThrownBy(() -> servicio.crear(solicitud))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("FACTURA_DUPLICADA"));
        verify(facturaRepositorio, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPropuesta.class, names = {"PENDIENTE_UF", "ANULADA", "FACTURADA"})
    void deberiaRechazarPropuestasEnEstadosNoFacturables(EstadoPropuesta estadoNoFacturable) {
        PropuestaFacturacion propuesta = propuestaPendiente(100L);
        propuesta.setEstado(estadoNoFacturable);
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(propuesta));

        FacturaSolicitudDto solicitud = new FacturaSolicitudDto("F-003", LocalDate.of(2026, 3, 1), null, List.of(100L));

        assertThatThrownBy(() -> servicio.crear(solicitud))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("PROPUESTA_NO_FACTURABLE"));
    }

    @Test
    void deberiaRechazarPropuestasDeClientesDistintos() {
        Cliente otroCliente = new Cliente();
        otroCliente.setId(20L);
        otroCliente.setEmpresa(empresa);
        otroCliente.setRazonSocial("Otro Cliente Ltda.");

        PropuestaFacturacion primera = propuestaPendiente(100L);
        PropuestaFacturacion segunda = propuestaPendiente(101L);
        segunda.setCliente(otroCliente);
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(100L, 1L)).thenReturn(Optional.of(primera));
        when(propuestaFacturacionRepositorio.findByIdAndEmpresaId(101L, 1L)).thenReturn(Optional.of(segunda));

        FacturaSolicitudDto solicitud = new FacturaSolicitudDto(
            "F-004", LocalDate.of(2026, 3, 1), null, List.of(100L, 101L));

        assertThatThrownBy(() -> servicio.crear(solicitud))
            .isInstanceOf(ReglaNegocioException.class)
            .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("PROPUESTAS_DE_CLIENTES_DISTINTOS"));
    }

    @Test
    void subirPdfDeberiaCrearElArchivoYEnlazarloALaFactura() {
        Factura factura = new Factura();
        factura.setId(500L);
        factura.setEmpresa(empresa);
        factura.setNumeroFactura("F-005");
        when(facturaRepositorio.findByIdAndEmpresaId(500L, 1L)).thenReturn(Optional.of(factura));
        when(almacenArchivos.guardar(eq("doc.pdf"), eq("application/pdf"), any()))
            .thenReturn(new ReferenciaArchivo("clave-1.pdf", "doc.pdf", "application/pdf", 3));
        when(archivoRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FacturaRespuestaDto respuesta = servicio.subirPdf(500L, "doc.pdf", "application/pdf", new byte[] {1, 2, 3});

        assertThat(respuesta.tienePdf()).isTrue();
        assertThat(respuesta.nombreArchivoPdf()).isEqualTo("doc.pdf");
        assertThat(factura.getArchivo().getClaveObjeto()).isEqualTo("clave-1.pdf");
    }

    @Test
    void subirPdfDeNuevoDeberiaReemplazarElEnlaceYEliminarElAnterior() {
        Archivo anterior = new Archivo();
        anterior.setId(1L);
        anterior.setEmpresa(empresa);
        anterior.setClaveObjeto("clave-anterior.pdf");
        Factura factura = new Factura();
        factura.setId(500L);
        factura.setEmpresa(empresa);
        factura.setNumeroFactura("F-006");
        factura.setArchivo(anterior);
        when(facturaRepositorio.findByIdAndEmpresaId(500L, 1L)).thenReturn(Optional.of(factura));
        when(almacenArchivos.guardar(any(), any(), any()))
            .thenReturn(new ReferenciaArchivo("clave-nueva.pdf", "doc2.pdf", "application/pdf", 5));
        when(archivoRepositorio.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.subirPdf(500L, "doc2.pdf", "application/pdf", new byte[] {1, 2, 3, 4, 5});

        assertThat(factura.getArchivo().getClaveObjeto()).isEqualTo("clave-nueva.pdf");
        verify(almacenArchivos).eliminar("clave-anterior.pdf");
        verify(archivoRepositorio).delete(anterior);
    }

    @Test
    void deberiaRechazarUnArchivoQueNoSeaPdf() {
        assertThatThrownBy(() -> servicio.subirPdf(500L, "imagen.png", "image/png", new byte[] {1}))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ARCHIVO_TIPO_INVALIDO"));
    }

    @Test
    void deberiaRechazarUnArchivoQueSuperaElTamanoMaximo() {
        byte[] archivoGrande = new byte[(int) DataSize.ofMegabytes(1).toBytes() + 1];

        assertThatThrownBy(() -> servicio.subirPdf(500L, "grande.pdf", "application/pdf", archivoGrande))
            .isInstanceOf(SolicitudInvalidaException.class)
            .satisfies(ex -> assertThat(((SolicitudInvalidaException) ex).getCodigo()).isEqualTo("ARCHIVO_DEMASIADO_GRANDE"));
    }
}
