package cl.helpcom.facturacion.informes.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionDetalleDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionResumenDto;
import cl.helpcom.facturacion.informes.servicio.ServicioInformeFacturacion;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InformeFacturacionControlador.class)
@Import(SeguridadConfig.class)
class InformeFacturacionControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioInformeFacturacion servicioInformeFacturacion;

    private InformeFacturacionRespuestaDto respuestaDeEjemplo() {
        Map<EstadoPropuesta, Long> cantidadPorEstado = new EnumMap<>(EstadoPropuesta.class);
        for (EstadoPropuesta estado : EstadoPropuesta.values()) {
            cantidadPorEstado.put(estado, 0L);
        }
        cantidadPorEstado.put(EstadoPropuesta.PENDIENTE, 1L);
        InformeFacturacionResumenDto resumen = new InformeFacturacionResumenDto(
            1, cantidadPorEstado, 0, new BigDecimal("100000"), new BigDecimal("19000"), new BigDecimal("119000"),
            List.of());
        InformeFacturacionDetalleDto fila = new InformeFacturacionDetalleDto(
            1L, 10L, "Cliente de Prueba SpA", null, null, "Servicio", 2026, 2, LocalDate.of(2026, 2, 15),
            Moneda.CLP, null, new BigDecimal("100000"), new BigDecimal("19000"), new BigDecimal("119000"),
            EstadoPropuesta.PENDIENTE, OrigenPropuesta.CICLO, null, null, null);
        return new InformeFacturacionRespuestaDto(resumen, new PaginaRespuestaDto<>(List.of(fila), 1, 0, 20));
    }

    @Test
    void deberiaResponderElInformeConResumenYDetalle() throws Exception {
        when(servicioInformeFacturacion.generar(any(), any())).thenReturn(respuestaDeEjemplo());

        mockMvc.perform(get("/api/v1/informes/facturacion")
                .param("periodoAnio", "2026")
                .param("periodoMes", "2")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumen.cantidadTotal").value(1))
            .andExpect(jsonPath("$.resumen.cantidadPendienteUf").value(0))
            .andExpect(jsonPath("$.resumen.totalClp").value(119000))
            .andExpect(jsonPath("$.detalle.contenido[0].descripcion").value("Servicio"));
    }

    @Test
    void amboRolesDeberianPoderLeerElInforme() throws Exception {
        when(servicioInformeFacturacion.generar(any(), any())).thenReturn(respuestaDeEjemplo());

        mockMvc.perform(get("/api/v1/informes/facturacion")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void deberiaRechazarConUnauthorizedSinAutenticar() throws Exception {
        mockMvc.perform(get("/api/v1/informes/facturacion"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deberiaExportarElDetalleComoCsvConNombreGenericoSinFiltroDePeriodo() throws Exception {
        String csv = "id;cliente\n1;Cliente de Prueba SpA\n";
        when(servicioInformeFacturacion.exportarDetalleCsv(any())).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/informes/facturacion/export")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("informe-facturacion.csv")));
    }

    @Test
    void deberiaExportarConNombreDescriptivoParaUnPeriodoExacto() throws Exception {
        String csv = "id;cliente\n1;Cliente de Prueba SpA\n";
        when(servicioInformeFacturacion.exportarDetalleCsv(any())).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/informes/facturacion/export")
                .param("periodoAnio", "2026")
                .param("periodoMes", "2")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "Content-Disposition", org.hamcrest.Matchers.containsString("informe-facturacion-2026-02.csv")));
    }

    /**
     * Regresión: {@code ejecutarFetch} (frontend, {@code lib/clienteApi.ts}) fija
     * {@code Accept: application/json} en TODA solicitud, incluidas las descargas binarias —
     * no es un descuido de este test, es el header real que manda el cliente, así que se fija
     * explícito a propósito (no lo "limpies" pensando que sobra). Con
     * {@code produces = "text/csv"} en el {@code @GetMapping}, ese header no calzaba y Spring
     * lanzaba {@code HttpMediaTypeNotAcceptableException} ANTES de llegar al método del
     * controlador — no manejada por {@code ManejadorGlobalErrores} (no hay handler específico
     * para ella), así que caía al catch-all {@code Exception.class} y devolvía 500 con
     * "Ocurrió un error inesperado. Contacte al administrador." (reportado por el usuario en la
     * pasada visual de R8). El fix quita {@code produces} del mapping — el {@code Content-Type}
     * real de la respuesta lo sigue fijando el {@code ResponseEntity.contentType(...)} del
     * controlador, así que el formato del CSV no cambia.
     */
    @Test
    void deberiaExportarAunConAcceptApplicationJsonComoMandaElClienteReal() throws Exception {
        String csv = "id;cliente\n1;Cliente de Prueba SpA\n";
        when(servicioInformeFacturacion.exportarDetalleCsv(any())).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/informes/facturacion/export")
                .accept(MediaType.APPLICATION_JSON)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    @Test
    void deberiaExportarConNombreDescriptivoParaUnRangoDePeriodos() throws Exception {
        String csv = "id;cliente\n1;Cliente de Prueba SpA\n";
        when(servicioInformeFacturacion.exportarDetalleCsv(any())).thenReturn(csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/informes/facturacion/export")
                .param("anioMesDesde", "202601")
                .param("anioMesHasta", "202603")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(header().string(
                "Content-Disposition",
                org.hamcrest.Matchers.containsString("informe-facturacion-2026-01_2026-03.csv")));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
