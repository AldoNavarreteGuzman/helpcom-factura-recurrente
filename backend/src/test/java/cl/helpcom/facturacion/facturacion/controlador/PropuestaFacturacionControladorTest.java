package cl.helpcom.facturacion.facturacion.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.facturacion.servicio.ServicioPropuestaFacturacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PropuestaFacturacionControlador.class)
@Import(SeguridadConfig.class)
class PropuestaFacturacionControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioPropuestaFacturacion servicioPropuestaFacturacion;

    @Test
    void deberiaListarPropuestasFiltradasPorPeriodoClienteYEstado() throws Exception {
        PropuestaFacturacionRespuestaDto dto = new PropuestaFacturacionRespuestaDto(
            1L, 10L, "Cliente de Prueba SpA", 100L, "Servicio de prueba",
            OrigenPropuesta.CICLO, 2026, 2, LocalDate.of(2026, 2, 15), "Servicio de prueba",
            Moneda.UF, new BigDecimal("12"), null, null, null,
            new BigDecimal("40000"), LocalDate.of(2026, 2, 15),
            new BigDecimal("480000"), new BigDecimal("0.19"), new BigDecimal("91200"), new BigDecimal("571200"),
            EstadoPropuesta.FACTURADA, "F-001", LocalDate.of(2026, 3, 1));
        when(servicioPropuestaFacturacion.listar(
                eq(2026), eq(2), eq(10L), eq(EstadoPropuesta.PENDIENTE), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/propuestas")
                .param("periodoAnio", "2026")
                .param("periodoMes", "2")
                .param("clienteId", "10")
                .param("estado", "PENDIENTE")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].totalClp").value(571200))
            .andExpect(jsonPath("$.contenido[0].numeroFactura").value("F-001"))
            .andExpect(jsonPath("$.contenido[0].fechaFactura").value("2026-03-01"));
    }

    @Test
    void deberiaListarPropuestasFiltradasPorOrigen() throws Exception {
        PropuestaFacturacionRespuestaDto dto = new PropuestaFacturacionRespuestaDto(
            2L, 11L, "Cliente CSV SpA", null, null,
            OrigenPropuesta.CSV, 2026, 2, LocalDate.of(2026, 2, 15), "Importado por CSV",
            Moneda.CLP, new BigDecimal("100000"), null, null, null,
            null, null,
            BigDecimal.ZERO, new BigDecimal("0.19"), BigDecimal.ZERO, BigDecimal.ZERO,
            EstadoPropuesta.PENDIENTE_UF, null, null);
        when(servicioPropuestaFacturacion.listar(
                isNull(), isNull(), isNull(), isNull(), eq(OrigenPropuesta.CSV), any()))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/propuestas")
                .param("origen", "CSV")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].origen").value("CSV"))
            .andExpect(jsonPath("$.contenido[0].numeroFactura").doesNotExist());
    }

    @Test
    void deberiaAnularUnaPropuestaComoAdministrador() throws Exception {
        mockMvc.perform(patch("/api/v1/propuestas/100/anular")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isNoContent());

        verify(servicioPropuestaFacturacion).anular(100L);
    }

    @Test
    void deberiaRechazarConForbiddenCuandoUnOperadorIntentaAnular() throws Exception {
        mockMvc.perform(patch("/api/v1/propuestas/100/anular")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void deberiaRetornar409ConProblemJsonCuandoLaPropuestaYaEstaFacturada() throws Exception {
        doThrow(new ReglaNegocioException(
            "La propuesta ya está facturada.", "PROPUESTA_FACTURADA_NO_SE_PUEDE_ANULAR"))
            .when(servicioPropuestaFacturacion).anular(100L);

        mockMvc.perform(patch("/api/v1/propuestas/100/anular")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("PROPUESTA_FACTURADA_NO_SE_PUEDE_ANULAR"));
    }

    @Test
    void deberiaReprocesarUfComoAdministradorYDevolverLaPropuestaActualizada() throws Exception {
        PropuestaFacturacionRespuestaDto dto = new PropuestaFacturacionRespuestaDto(
            64L, 1L, "Cliente de Prueba SpA", 1L, "Soporte mensual",
            OrigenPropuesta.CICLO, 2026, 5, LocalDate.of(2026, 5, 15), "Soporte mensual",
            Moneda.UF, new BigDecimal("12"), null, new BigDecimal("10.0000"), null,
            new BigDecimal("40340.86"), LocalDate.of(2026, 5, 15),
            new BigDecimal("363068"), new BigDecimal("0.19"), new BigDecimal("68983"), new BigDecimal("432051"),
            EstadoPropuesta.PENDIENTE, null, null);
        when(servicioPropuestaFacturacion.reprocesarUf(64L)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/propuestas/64/reprocesar-uf")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.netoClp").value(363068));
    }

    @Test
    void deberiaRechazarConForbiddenCuandoUnOperadorIntentaReprocesar() throws Exception {
        mockMvc.perform(patch("/api/v1/propuestas/64/reprocesar-uf")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void deberiaRetornar409ConProblemJsonCuandoLaPropuestaNoEsReprocesable() throws Exception {
        when(servicioPropuestaFacturacion.reprocesarUf(64L)).thenThrow(new ReglaNegocioException(
            "La propuesta 64 está en estado PENDIENTE y no se puede reprocesar.", "PROPUESTA_NO_REPROCESABLE"));

        mockMvc.perform(patch("/api/v1/propuestas/64/reprocesar-uf")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("PROPUESTA_NO_REPROCESABLE"));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
