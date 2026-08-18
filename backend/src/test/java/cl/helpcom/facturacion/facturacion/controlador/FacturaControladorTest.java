package cl.helpcom.facturacion.facturacion.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaResumenDto;
import cl.helpcom.facturacion.facturacion.servicio.DescargaArchivo;
import cl.helpcom.facturacion.facturacion.servicio.ServicioFactura;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FacturaControlador.class)
@Import(SeguridadConfig.class)
class FacturaControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioFactura servicioFactura;

    private FacturaRespuestaDto facturaDeEjemplo() {
        PropuestaResumenDto propuesta = new PropuestaResumenDto(
            100L, 2026, 2, LocalDate.of(2026, 2, 15), "Servicio de prueba",
            new BigDecimal("100000"), new BigDecimal("19000"), new BigDecimal("119000"), EstadoPropuesta.FACTURADA);
        return new FacturaRespuestaDto(
            500L, "F-001", LocalDate.of(2026, 3, 1), null, 10L, "Cliente de Prueba SpA",
            false, null, List.of(propuesta));
    }

    @Test
    void deberiaRetornar201ConLocationAlCrearUnaFacturaValida() throws Exception {
        when(servicioFactura.crear(any())).thenReturn(facturaDeEjemplo());

        mockMvc.perform(post("/api/v1/facturas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"numeroFactura":"F-001","fechaFactura":"2026-03-01","propuestaIds":[100]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").value(500))
            .andExpect(jsonPath("$.propuestas[0].id").value(100));
    }

    @Test
    void deberiaRetornar409ConProblemJsonCuandoElNumeroYaExiste() throws Exception {
        when(servicioFactura.crear(any()))
            .thenThrow(new ReglaNegocioException("Ya existe una factura con ese número.", "FACTURA_DUPLICADA"));

        mockMvc.perform(post("/api/v1/facturas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"numeroFactura":"F-001","fechaFactura":"2026-03-01","propuestaIds":[100]}
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("FACTURA_DUPLICADA"));
    }

    @Test
    void deberiaRetornar400ConListaDeErroresCuandoFaltanLasPropuestas() throws Exception {
        mockMvc.perform(post("/api/v1/facturas")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"numeroFactura":"F-001","fechaFactura":"2026-03-01","propuestaIds":[]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errores", org.hamcrest.Matchers.hasItem("propuestaIds")));
    }

    @Test
    void deberiaRetornarElDetalleDeUnaFactura() throws Exception {
        when(servicioFactura.obtener(500L)).thenReturn(facturaDeEjemplo());

        mockMvc.perform(get("/api/v1/facturas/500")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.numeroFactura").value("F-001"))
            .andExpect(jsonPath("$.tienePdf").value(false));
    }

    @Test
    void deberiaRetornar404ConProblemJsonCuandoLaFacturaNoExiste() throws Exception {
        when(servicioFactura.obtener(999L))
            .thenThrow(new RecursoNoEncontradoException("No existe una factura con id 999", "FACTURA_NO_ENCONTRADA"));

        mockMvc.perform(get("/api/v1/facturas/999")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("FACTURA_NO_ENCONTRADA"));
    }

    @Test
    void deberiaSubirElPdfPorMultipart() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "informe.pdf", "application/pdf", "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8));
        FacturaRespuestaDto respuesta = new FacturaRespuestaDto(
            500L, "F-001", LocalDate.of(2026, 3, 1), null, 10L, "Cliente de Prueba SpA",
            true, "informe.pdf", List.of());
        when(servicioFactura.subirPdf(eq(500L), eq("informe.pdf"), eq("application/pdf"), any()))
            .thenReturn(respuesta);

        mockMvc.perform(multipart("/api/v1/facturas/500/pdf")
                .file(archivo)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tienePdf").value(true))
            .andExpect(jsonPath("$.nombreArchivoPdf").value("informe.pdf"));
    }

    @Test
    void deberiaDescargarElPdfConLosEncabezadosCorrectos() throws Exception {
        byte[] contenido = "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8);
        when(servicioFactura.descargarPdf(500L))
            .thenReturn(new DescargaArchivo("informe.pdf", "application/pdf", new ByteArrayInputStream(contenido)));

        mockMvc.perform(get("/api/v1/facturas/500/pdf")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("informe.pdf")))
            .andExpect(content().bytes(contenido));
    }

    @Test
    void deberiaListarFacturasFiltrandoPorRangoDeFechas() throws Exception {
        when(servicioFactura.listar(
                eq(null), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 3, 31)), eq(null), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(facturaDeEjemplo())));

        mockMvc.perform(get("/api/v1/facturas")
                .param("fechaDesde", "2026-01-01")
                .param("fechaHasta", "2026-03-31")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[0].id").value(500))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deberiaRechazarConForbiddenAUnUsuarioSinRolesReconocidos() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/500")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVITADO"))))
            .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
