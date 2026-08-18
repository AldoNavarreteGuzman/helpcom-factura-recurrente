package cl.helpcom.facturacion.comun.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.clientes.controlador.ClienteControlador;
import cl.helpcom.facturacion.clientes.servicio.ClienteServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bug real diagnosticado contra el contenedor vivo (docs/deuda-tecnica.md ítem 4): sin
 * {@code CorsConfigurationSource}, un preflight {@code OPTIONS} real devolvía 401 sin ningún
 * header {@code Access-Control-Allow-*}, así que el navegador bloqueaba TODO fetch con header
 * {@code Authorization} antes de llegar al backend. Cubre el preflight (sin autenticar, con
 * headers CORS correctos) para MÁS de un origen permitido — regresión específica del ajuste
 * de dev (`application-local.yml`: 3000-3002, porque `npm run dev` salta de puerto si el
 * anterior está ocupado) — y que un origen fuera de la lista no los recibe. La propiedad de
 * prueba lleva un espacio después de la coma a propósito, para ejercitar el recorte manual de
 * {@code SeguridadConfig#parsearOrigenes} (un origen sin recortar no matchea el header
 * {@code Origin} exacto que manda el navegador).
 */
@WebMvcTest(ClienteControlador.class)
@Import(SeguridadConfig.class)
@TestPropertySource(properties = "app.cors.origenes-permitidos=http://localhost:3000, http://localhost:3002")
class SeguridadConfigCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteServicio clienteServicio;

    @Test
    void elPreflightDeUnOrigenPermitidoNoExigeAutenticacionYTraeLosHeadersCors() throws Exception {
        mockMvc.perform(options("/api/v1/clientes")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, (String) null));
    }

    @Test
    void elPreflightDeUnSegundoOrigenLocalPermitidoNo3000TambienFunciona() throws Exception {
        mockMvc.perform(options("/api/v1/clientes")
                .header(HttpHeaders.ORIGIN, "http://localhost:3002")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3002"));
    }

    @Test
    void unOrigenFueraDeLaListaNoRecibeHeadersCors() throws Exception {
        mockMvc.perform(get("/api/v1/clientes")
                .header(HttpHeaders.ORIGIN, "http://localhost:9999"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @TestConfiguration
    static class ConfiguracionSeguridadPrueba {

        @Bean
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }
}
