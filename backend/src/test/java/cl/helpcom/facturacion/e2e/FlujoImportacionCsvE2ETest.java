package cl.helpcom.facturacion.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E-6 (docs/qa.md, modelo-de-datos.md §6, arquitectura-tecnica.md §10): un CSV con las tres
 * combinaciones que importan — fila OK sin proyecto, fila OK con proyecto (para probar que la
 * importación resuelve el acuerdo vigente igual que el ciclo, vía el mismo
 * {@code ArmadorPropuesta}), fila con cliente inexistente (ERROR, no se importa) y fila en UF
 * sin UF sembrada (ADVERTENCIA en previsualización, {@code PENDIENTE_UF} al confirmar) —
 * previsualizada y luego confirmada con el mismo archivo (arquitectura-tecnica.md §10: el
 * cliente reenvía el mismo CSV, no hay estado entre las dos llamadas).
 */
@Tag("e2e")
class FlujoImportacionCsvE2ETest extends SoporteE2E {

    private static final String ENCABEZADO =
        "rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion\n";

    @Test
    void deberiaPrevisualizarYConfirmarUnCsvMixtoConElMismoCalculoDelCiclo() throws Exception {
        String rutClienteA = rutDePrueba();
        String rutClienteB = rutDePrueba();
        String rutInexistente = rutDePrueba(); // válido en formato, pero nunca se crea como cliente.
        Long clienteAId = crearCliente(rutClienteA, "Cliente CSV A SpA");
        crearCliente(rutClienteB, "Cliente CSV B Ltda.");

        ProyectoRespuestaDto proyectoConAcuerdo = crearProyecto(
            clienteAId, "999.0000", "UF", "MENSUAL", 1, LocalDate.of(2025, 1, 1), null);
        crearAcuerdo(proyectoConAcuerdo.id(), TipoAcuerdo.DESCUENTO_PORCENTAJE, "20.0000", null,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        // El código lo asigna el servicio de proyectos con el que se creó; lo leemos de vuelta
        // para armar la fila del CSV (el proyecto se creó sin código explícito → null). Como
        // ValidadorFilaCsv exige codigo_proyecto para resolver el proyecto, creamos el
        // proyecto CON código en su lugar.
        String codigoProyecto = "PRJ-CSV-" + proyectoConAcuerdo.id();

        // Re-creamos el proyecto con código (el helper crearProyecto no admite código; se
        // arma la solicitud a mano solo para esta fila).
        ProyectoRespuestaDto proyectoConCodigo = crearProyectoConCodigo(
            clienteAId, codigoProyecto, "999.0000", "UF", LocalDate.of(2025, 1, 1));
        crearAcuerdo(proyectoConCodigo.id(), TipoAcuerdo.DESCUENTO_PORCENTAJE, "20.0000", null,
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        sembrarUf(LocalDate.of(2026, 2, 5), "42000.0000");
        sembrarUf(LocalDate.of(2026, 2, 12), "45000.0000");
        // 2026-03-20 NO se siembra a propósito (fila 5: PENDIENTE_UF).

        String csv = ENCABEZADO
            + rutClienteA + ";;Servicio A sin proyecto;2026-02;05-02-2026;UF;10;\n"
            + rutClienteB + ";;Servicio B en CLP;2026-02;10-02-2026;CLP;850000;\n"
            + rutClienteA + ";" + codigoProyecto + ";Servicio con acuerdo vigente;2026-02;12-02-2026;UF;10;\n"
            + rutInexistente + ";;Cliente que no existe;2026-02;05-02-2026;UF;10;\n"
            + rutClienteB + ";;Sin UF disponible;2026-03;20-03-2026;UF;8;\n";
        byte[] contenidoCsv = csv.getBytes(StandardCharsets.UTF_8);

        // --- Previsualizar ---
        ImportacionPreviewRespuestaDto preview = previsualizarCsv(contenidoCsv);
        assertThat(preview.resumen().totalFilas()).isEqualTo(5);
        assertThat(preview.resumen().filasOk()).isEqualTo(3);
        assertThat(preview.resumen().filasAdvertencia()).isEqualTo(1);
        assertThat(preview.resumen().filasError()).isEqualTo(1);

        assertThat(preview.filas().get(0).estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(preview.filas().get(0).netoClp()).isEqualByComparingTo("420000"); // 10 × 42.000
        assertThat(preview.filas().get(0).ivaClp()).isEqualByComparingTo("79800");
        assertThat(preview.filas().get(0).totalClp()).isEqualByComparingTo("499800");

        assertThat(preview.filas().get(1).estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(preview.filas().get(1).netoClp()).isEqualByComparingTo("850000");

        // Fila con proyecto+acuerdo: mismo ArmadorPropuesta que el ciclo → 10 × 0,8 × 45.000.
        assertThat(preview.filas().get(2).estado()).isEqualTo(EstadoFilaCsv.OK);
        assertThat(preview.filas().get(2).netoClp()).isEqualByComparingTo("360000");
        assertThat(preview.filas().get(2).ivaClp()).isEqualByComparingTo("68400");
        assertThat(preview.filas().get(2).totalClp()).isEqualByComparingTo("428400");

        assertThat(preview.filas().get(3).estado()).isEqualTo(EstadoFilaCsv.ERROR);
        assertThat(preview.filas().get(3).netoClp()).isNull();

        assertThat(preview.filas().get(4).estado()).isEqualTo(EstadoFilaCsv.ADVERTENCIA);
        assertThat(preview.filas().get(4).netoClp()).isEqualByComparingTo("0");
        assertThat(preview.filas().get(4).mensajes()).anyMatch(m -> m.contains("PENDIENTE_UF"));

        // --- Confirmar (mismo archivo, se re-valida) ---
        ImportacionCsvRespuestaDto confirmacion = confirmarCsv(contenidoCsv);
        assertThat(confirmacion.totalFilas()).isEqualTo(5);
        assertThat(confirmacion.filasOk()).isEqualTo(4); // OK + ADVERTENCIA se importan.
        assertThat(confirmacion.filasError()).isEqualTo(1);
        assertThat(confirmacion.cantidadPendienteUf()).isEqualTo(1);
        assertThat(confirmacion.estado()).isEqualTo(EstadoImportacionCsv.PARCIAL);

        // Las propuestas CSV quedan con el mismo cálculo mostrado en la previsualización.
        List<PropuestaFacturacionRespuestaDto> propuestasFeb =
            listarPropuestas(2026, 2, null, null, OrigenPropuesta.CSV);
        assertThat(propuestasFeb).hasSize(3);
        assertThat(propuestasFeb).extracting(PropuestaFacturacionRespuestaDto::netoClp)
            .usingElementComparator(java.math.BigDecimal::compareTo)
            .containsExactlyInAnyOrder(
                new java.math.BigDecimal("420000"), new java.math.BigDecimal("850000"),
                new java.math.BigDecimal("360000"));

        List<PropuestaFacturacionRespuestaDto> propuestasMar =
            listarPropuestas(2026, 3, null, EstadoPropuesta.PENDIENTE_UF, OrigenPropuesta.CSV);
        assertThat(propuestasMar).hasSize(1);
        assertThat(propuestasMar.get(0).netoClp()).isEqualByComparingTo("0");

        // La fila con cliente inexistente no generó ninguna propuesta (4 CSV, no 5).
        List<PropuestaFacturacionRespuestaDto> todasLasCsv =
            listarPropuestas(null, null, null, null, OrigenPropuesta.CSV);
        assertThat(todasLasCsv).hasSize(4);
    }

    private ProyectoRespuestaDto crearProyectoConCodigo(
        Long clienteId, String codigo, String precioBaseNeto, String monedaPrecio, LocalDate fechaInicio)
        throws Exception {
        String cuerpo = """
            {"clienteId":%d,"codigo":"%s","nombre":"Proyecto con código para CSV","precioBaseNeto":%s,
             "monedaPrecio":"%s","periodicidad":"MENSUAL","diaFacturacion":1,"fechaInicio":"%s",
             "fechaTermino":null,"activo":true}
            """.formatted(clienteId, codigo, precioBaseNeto, monedaPrecio, fechaInicio);
        String respuesta = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/proyectos")
                .with(administrador())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(cuerpo))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(respuesta, ProyectoRespuestaDto.class);
    }
}
