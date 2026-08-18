package cl.helpcom.facturacion.clientes.controlador;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.clientes.dto.ClienteRespuestaDto;
import cl.helpcom.facturacion.clientes.servicio.ClienteServicio;
import cl.helpcom.facturacion.comun.config.SeguridadConfig;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
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

@WebMvcTest(ClienteControlador.class)
@Import(SeguridadConfig.class)
class ClienteControladorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteServicio clienteServicio;

    @Test
    void deberiaRetornar201ConLocationAlCrearUnClienteValido() throws Exception {
        ClienteRespuestaDto respuesta = new ClienteRespuestaDto(
            1L, "12345678-5", "Cliente de Prueba SpA", null, null, null, null, null, true);
        when(clienteServicio.crear(any())).thenReturn(respuesta);

        mockMvc.perform(post("/api/v1/clientes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rut":"12345678-5","razonSocial":"Cliente de Prueba SpA","activo":true}
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.rut").value("12345678-5"));
    }

    @Test
    void deberiaRetornar404ConProblemJsonCuandoElClienteNoExiste() throws Exception {
        when(clienteServicio.obtener(99L))
            .thenThrow(new RecursoNoEncontradoException("No existe un cliente con id 99", "CLIENTE_NO_ENCONTRADO"));

        mockMvc.perform(get("/api/v1/clientes/99")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR"))))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
    }

    @Test
    void deberiaRetornar400ConListaDeErroresCuandoElDtoEsInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("VALIDACION_CAMPOS"))
            .andExpect(jsonPath("$.errores").isArray())
            .andExpect(jsonPath("$.errores", hasItem("rut")))
            .andExpect(jsonPath("$.errores", hasItem("razonSocial")));
    }

    @Test
    void deberiaRetornar409ConProblemJsonCuandoElRutEsDuplicado() throws Exception {
        when(clienteServicio.crear(any()))
            .thenThrow(new ReglaNegocioException("Ya existe un cliente con ese RUT.", "CLIENTE_DUPLICADO"));

        mockMvc.perform(post("/api/v1/clientes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rut":"12345678-5","razonSocial":"Cliente de Prueba SpA","activo":true}
                    """))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("CLIENTE_DUPLICADO"));
    }

    @Test
    void deberiaRetornar403CuandoUnOperadorIntentaCrearUnCliente() throws Exception {
        mockMvc.perform(post("/api/v1/clientes")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERADOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rut":"12345678-5","razonSocial":"Cliente de Prueba SpA","activo":true}
                    """))
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
