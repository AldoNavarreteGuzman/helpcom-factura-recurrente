package cl.helpcom.facturacion.importacion.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewFilaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewResumenDto;
import cl.helpcom.facturacion.importacion.servicio.ServicioImportacionCsv;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportacionCsvControlador.class)
@Import(SeguridadConfig.class)
class ImportacionCsvControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioImportacionCsv servicioImportacionCsv;

    private MockMultipartFile archivoCsv() {
        return new MockMultipartFile(
            "archivo", "importacion.csv", "text/csv",
            "rut_cliente;descripcion;periodo;fecha_facturacion;moneda;monto_neto\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void deberiaPrevisualizarUnCsvValido() throws Exception {
        ImportacionPreviewFilaDto fila = new ImportacionPreviewFilaDto(
            2, EstadoFilaCsv.OK, List.of(), "11111111-1", null, "Servicio", "2026-02", "15-02-2026", "UF", "12", null,
            new BigDecimal("480000"), new BigDecimal("91200"), new BigDecimal("571200"));
        ImportacionPreviewRespuestaDto respuesta = new ImportacionPreviewRespuestaDto(
            new ImportacionPreviewResumenDto(1, 1, 0, 0), List.of(fila));
        when(servicioImportacionCsv.previsualizar(any())).thenReturn(respuesta);

        mockMvc.perform(multipart("/api/v1/importaciones/previsualizar")
                .file(archivoCsv())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumen.filasOk").value(1))
            .andExpect(jsonPath("$.filas[0].estado").value("OK"))
            .andExpect(jsonPath("$.filas[0].netoClp").value(480000));
    }

    @Test
    void deberiaConfirmarUnaImportacionYDevolverElContadorRealDePendienteUf() throws Exception {
        ImportacionCsvRespuestaDto respuesta = new ImportacionCsvRespuestaDto(
            1L, "importacion.csv", OffsetDateTime.now(), 1, 1, 1, 0, EstadoImportacionCsv.PROCESADA);
        when(servicioImportacionCsv.confirmar(any())).thenReturn(respuesta);

        mockMvc.perform(multipart("/api/v1/importaciones/confirmar")
                .file(archivoCsv())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PROCESADA"))
            .andExpect(jsonPath("$.filasOk").value(1))
            .andExpect(jsonPath("$.cantidadPendienteUf").value(1));
    }

    @Test
    void deberiaListarLasImportacionesPaginadasSinElContadorDePendienteUf() throws Exception {
        // El historial no persiste cantidadPendienteUf (docs/deuda-tecnica.md) — el DTO viaja
        // en null y, con default-property-inclusion=non_null, el campo ni aparece en el JSON.
        ImportacionCsvRespuestaDto respuesta = new ImportacionCsvRespuestaDto(
            1L, "importacion.csv", OffsetDateTime.now(), 5, 4, null, 1, EstadoImportacionCsv.PARCIAL);
        when(servicioImportacionCsv.listar(any())).thenReturn(new PageImpl<>(List.of(respuesta), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/importaciones")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].estado").value("PARCIAL"))
            .andExpect(jsonPath("$.contenido[0].cantidadPendienteUf").doesNotExist());
    }

    @Test
    void deberiaRechazarConForbiddenAUnUsuarioSinRolesReconocidos() throws Exception {
        mockMvc.perform(multipart("/api/v1/importaciones/previsualizar")
                .file(archivoCsv())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVITADO"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void deberiaRetornar400ConProblemJsonCuandoElCsvEstaVacio() throws Exception {
        when(servicioImportacionCsv.previsualizar(any()))
            .thenThrow(new SolicitudInvalidaException("El archivo CSV está vacío.", "CSV_VACIO"));

        mockMvc.perform(multipart("/api/v1/importaciones/previsualizar")
                .file(archivoCsv())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("CSV_VACIO"));
    }

    @Test
    void deberiaRetornar400ConProblemJsonCuandoFaltanColumnasObligatorias() throws Exception {
        when(servicioImportacionCsv.previsualizar(any()))
            .thenThrow(new SolicitudInvalidaException("Faltan columnas obligatorias.", "CSV_COLUMNAS_FALTANTES"));

        mockMvc.perform(multipart("/api/v1/importaciones/previsualizar")
                .file(archivoCsv())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("CSV_COLUMNAS_FALTANTES"));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
