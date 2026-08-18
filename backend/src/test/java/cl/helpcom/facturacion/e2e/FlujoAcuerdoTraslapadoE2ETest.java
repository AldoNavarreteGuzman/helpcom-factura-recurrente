package cl.helpcom.facturacion.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * E2E-7 (docs/qa.md, arquitectura-tecnica.md §9.1): un proyecto no puede tener dos acuerdos
 * de precio vigentes que se traslapen. Corre contra Postgres real, donde la restricción de
 * exclusión {@code ex_acuerdo_no_traslape} es la última garantía ante escrituras concurrentes
 * (la primera línea de defensa es la validación de {@code AcuerdoPrecioServicio}, ya probada
 * de forma aislada en {@code AcuerdoPrecioServicioTest}; {@code EsquemaBaseDatosTest} prueba
 * la restricción directamente contra el repositorio). Este flujo prueba el camino real de
 * negocio de punta a punta: dos peticiones HTTP consecutivas.
 */
@Tag("e2e")
class FlujoAcuerdoTraslapadoE2ETest extends SoporteE2E {

    @Test
    void unSegundoAcuerdoQueSeTraslapaConElVigenteDeberiaRechazarseCon409() throws Exception {
        Long clienteId = crearCliente(rutDePrueba(), "Cliente Traslape SpA");
        ProyectoRespuestaDto proyecto = crearProyecto(
            clienteId, "500000", "CLP", "MENSUAL", 5, LocalDate.of(2026, 1, 1), null);

        crearAcuerdo(proyecto.id(), TipoAcuerdo.DESCUENTO_PORCENTAJE, "10.0000", null,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        String acuerdoTraslapado = """
            {"tipo":"DESCUENTO_PORCENTAJE","valor":15.0000,"fechaInicio":"2026-06-01","fechaTermino":"2026-12-31"}
            """;
        mockMvc.perform(post("/api/v1/proyectos/{proyectoId}/acuerdos", proyecto.id())
                .with(administrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content(acuerdoTraslapado))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.codigo").value("ACUERDO_TRASLAPADO"));
    }
}
