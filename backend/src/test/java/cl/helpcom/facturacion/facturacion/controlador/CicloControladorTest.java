package cl.helpcom.facturacion.facturacion.controlador;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.config.ZonaHorariaConfig;
import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.ciclo.ServicioCicloFacturacion;
import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.servicio.ServicioEjecucionCiclo;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CicloControlador.class)
@Import({SeguridadConfig.class, ZonaHorariaConfig.class})
class CicloControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServicioCicloFacturacion servicioCicloFacturacion;

    @MockitoBean
    private ServicioEjecucionCiclo servicioEjecucionCiclo;

    @Test
    void deberiaEjecutarElCicloComoAdministradorYResponderElResumen() throws Exception {
        ResultadoCiclo resultado = new ResultadoCiclo(
            1L, 2026, 2, DisparoCiclo.MANUAL, EstadoEjecucionCiclo.EXITOSA, 3, 0,
            "Propuestas generadas: 3. Pendientes de UF: 0. Proyectos con error: 0.");
        when(servicioCicloFacturacion.ejecutarCiclo(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(DisparoCiclo.MANUAL)))
            .thenReturn(resultado);

        mockMvc.perform(post("/api/v1/ciclos/ejecutar")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"anio":2026,"mes":2}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cantidadGeneradas").value(3))
            .andExpect(jsonPath("$.estado").value("EXITOSA"));
    }

    @Test
    void deberiaRechazarConForbiddenCuandoUnOperadorIntentaEjecutarElCiclo() throws Exception {
        mockMvc.perform(post("/api/v1/ciclos/ejecutar")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void deberiaListarElHistorialDeEjecucionesParaUnOperador() throws Exception {
        EjecucionCicloRespuestaDto dto = new EjecucionCicloRespuestaDto(
            1L, 2026, 2, OffsetDateTime.now(), DisparoCiclo.AUTOMATICO, 3, 0,
            EstadoEjecucionCiclo.EXITOSA, "ok", "sistema");
        when(servicioEjecucionCiclo.listar(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new PageImpl<>(java.util.List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/ciclos")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.contenido[0].periodoAnio").value(2026));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
