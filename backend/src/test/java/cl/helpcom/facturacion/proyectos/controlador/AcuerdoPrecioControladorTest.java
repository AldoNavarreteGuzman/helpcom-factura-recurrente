package cl.helpcom.facturacion.proyectos.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioRespuestaDto;
import cl.helpcom.facturacion.proyectos.servicio.AcuerdoPrecioServicio;
import java.math.BigDecimal;
import java.time.LocalDate;
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

@WebMvcTest(AcuerdoPrecioControlador.class)
@Import(SeguridadConfig.class)
class AcuerdoPrecioControladorTest {

    private static final String CUERPO_ACUERDO_VALIDO = """
        {"tipo":"DESCUENTO_PORCENTAJE","valor":10,"fechaInicio":"2026-01-01","fechaTermino":"2026-06-30"}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcuerdoPrecioServicio acuerdoPrecioServicio;

    @Test
    void deberiaRetornar201ConLocationAlCrearUnAcuerdoValido() throws Exception {
        AcuerdoPrecioRespuestaDto respuesta = new AcuerdoPrecioRespuestaDto(
            5L, 100L, TipoAcuerdo.DESCUENTO_PORCENTAJE, new BigDecimal("10"), null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), null, null);
        when(acuerdoPrecioServicio.crear(eq(100L), any())).thenReturn(respuesta);

        mockMvc.perform(post("/api/v1/proyectos/100/acuerdos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_ACUERDO_VALIDO))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.proyectoId").value(100));
    }

    @Test
    void deberiaRetornar409ConProblemJsonCuandoElAcuerdoSeTraslapaConOtro() throws Exception {
        when(acuerdoPrecioServicio.crear(eq(100L), any())).thenThrow(new ReglaNegocioException(
            "La vigencia se traslapa con el acuerdo 7 (2026-03-01 a 2026-08-31).", "ACUERDO_TRASLAPADO"));

        mockMvc.perform(post("/api/v1/proyectos/100/acuerdos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO_ACUERDO_VALIDO))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("ACUERDO_TRASLAPADO"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("acuerdo 7")));
    }

    @Test
    void deberiaRetornar400CuandoFaltaLaFechaInicio() throws Exception {
        mockMvc.perform(post("/api/v1/proyectos/100/acuerdos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tipo":"DESCUENTO_PORCENTAJE","valor":10,"fechaTermino":"2026-06-30"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("VALIDACION_CAMPOS"))
            .andExpect(jsonPath("$.errores", org.hamcrest.Matchers.hasItem("fechaInicio")));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
