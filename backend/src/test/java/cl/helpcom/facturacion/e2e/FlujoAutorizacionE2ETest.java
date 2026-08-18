package cl.helpcom.facturacion.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/**
 * E2E-8 (docs/qa.md): autorización real por rol contra el contexto completo (filtro de
 * seguridad + {@code @PreAuthorize} + controlador), al menos un endpoint por módulo. No repite
 * lo que ya cubren los {@code *ControladorTest} con mocks — aquí importa que la cadena
 * COMPLETA (incluida la persistencia real) respete la regla, no solo el filtro aislado.
 */
@Tag("e2e")
class FlujoAutorizacionE2ETest extends SoporteE2E {

    @Test
    void unaSolicitudSinAutenticarDeberiaRecibirUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/clientes"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unRolNoReconocidoDeberiaRecibirForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/clientes").with(sinRolesReconocidos()))
            .andExpect(status().isForbidden());
    }

    @Test
    void clientes_soloAdministradorPuedeCrear() throws Exception {
        String cuerpo = """
            {"rut":"%s","razonSocial":"Cliente Autorización SpA","activo":true}
            """.formatted(rutDePrueba());

        mockMvc.perform(post("/api/v1/clientes").with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clientes").with(administrador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated());
    }

    @Test
    void tiposServicio_soloAdministradorPuedeCrear() throws Exception {
        String cuerpo = """
            {"nombre":"Consultoría E2E","activo":true}
            """;

        mockMvc.perform(post("/api/v1/tipos-servicio").with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/tipos-servicio").with(administrador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated());
    }

    @Test
    void proyectos_soloAdministradorPuedeCrear() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Autorización Proyectos SpA");
        String cuerpo = """
            {"clienteId":%d,"nombre":"Proyecto autorización","precioBaseNeto":100000,"monedaPrecio":"CLP",
             "periodicidad":"MENSUAL","diaFacturacion":5,"fechaInicio":"2026-01-01","fechaTermino":null,"activo":true}
            """.formatted(clienteId);

        mockMvc.perform(post("/api/v1/proyectos").with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proyectos").with(administrador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated());
    }

    @Test
    void acuerdos_soloAdministradorPuedeCrear() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Autorización Acuerdos SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "500000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        String cuerpo = """
            {"tipo":"DESCUENTO_PORCENTAJE","valor":10.0000,"fechaInicio":"2026-01-01","fechaTermino":"2026-06-30"}
            """;

        mockMvc.perform(post("/api/v1/proyectos/{id}/acuerdos", proyecto.id()).with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proyectos/{id}/acuerdos", proyecto.id()).with(administrador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isCreated());
    }

    @Test
    void ciclo_soloAdministradorPuedeEjecutarloPeroAmbosRolesPuedenListarlo() throws Exception {
        mockMvc.perform(post("/api/v1/ciclos/ejecutar").with(operador())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ciclos/ejecutar").with(administrador())
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"anio":2026,"mes":2}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/ciclos").with(operador())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ciclos").with(administrador())).andExpect(status().isOk());
    }

    @Test
    void propuestas_ambosRolesPuedenListarPeroSoloAdministradorPuedeAnular() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Autorización Propuestas SpA");
        crearProyecto(clienteId, "200000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        ejecutarCiclo(2026, 2);
        List<PropuestaFacturacionRespuestaDto> propuestas = listarPropuestas(2026, 2, clienteId, null, null);
        Long propuestaId = propuestas.get(0).id();

        mockMvc.perform(get("/api/v1/propuestas").with(operador())).andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/propuestas/{id}/anular", propuestaId).with(operador()))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/propuestas/{id}/anular", propuestaId).with(administrador()))
            .andExpect(status().isNoContent());
    }

    @Test
    void facturas_unOperadorPuedeCrearYSubirElPdf() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Autorización Facturas SpA");
        crearProyecto(clienteId, "150000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);
        ejecutarCiclo(2026, 2);
        Long propuestaId = listarPropuestas(2026, 2, clienteId, null, null).get(0).id();

        String cuerpoFactura = """
            {"numeroFactura":"F-AUTORIZACION-001","fechaFactura":"2026-03-01","propuestaIds":[%d]}
            """.formatted(propuestaId);
        String respuesta = mockMvc.perform(post("/api/v1/facturas").with(operador())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpoFactura))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        Long facturaId = objectMapper.readTree(respuesta).get("id").asLong();

        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "respaldo.pdf", "application/pdf", "%PDF-1.4".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/facturas/{id}/pdf", facturaId).file(archivo).with(operador()))
            .andExpect(status().isOk());
    }

    @Test
    void importaciones_unOperadorPuedePrevisualizarYConfirmar() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Autorización Importación SpA");
        String rut = obtenerRutDelCliente(clienteId);
        String csv = "rut_cliente;descripcion;periodo;fecha_facturacion;moneda;monto_neto\n"
            + rut + ";Servicio autorización;2026-02;05-02-2026;CLP;100000\n";
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "importacion.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/importaciones/previsualizar").file(archivo).with(operador()))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/importaciones/confirmar").file(archivo).with(operador()))
            .andExpect(status().isOk());
    }

    private String obtenerRutDelCliente(Long clienteId) throws Exception {
        String respuesta = mockMvc.perform(get("/api/v1/clientes/{id}", clienteId).with(administrador()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("rut").asText();
    }
}
