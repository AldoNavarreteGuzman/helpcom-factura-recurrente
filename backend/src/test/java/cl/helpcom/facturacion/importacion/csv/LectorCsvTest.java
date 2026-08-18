package cl.helpcom.facturacion.importacion.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LectorCsvTest {

    private final LectorCsv lector = new LectorCsv();

    @Test
    void deberiaParsearUnCsvValidoConLasOchoColumnas() {
        String csv = """
            rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
            76543210-9;;Servicio mantención enero;2026-01;15-01-2026;UF;12.5;Calculado en Excel
            77111222-3;PRJ-014;Soporte anual;2026-01;20-01-2026;CLP;850000;
            """;

        List<FilaCsv> filas = lector.leer(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(filas).hasSize(2);
        FilaCsv primera = filas.get(0);
        assertThat(primera.numeroFila()).isEqualTo(2);
        assertThat(primera.rutCliente()).isEqualTo("76543210-9");
        assertThat(primera.codigoProyecto()).isNull();
        assertThat(primera.descripcion()).isEqualTo("Servicio mantención enero");
        assertThat(primera.periodo()).isEqualTo("2026-01");
        assertThat(primera.fechaFacturacion()).isEqualTo("15-01-2026");
        assertThat(primera.moneda()).isEqualTo("UF");
        assertThat(primera.montoNeto()).isEqualTo("12.5");
        assertThat(primera.observacion()).isEqualTo("Calculado en Excel");

        FilaCsv segunda = filas.get(1);
        assertThat(segunda.numeroFila()).isEqualTo(3);
        assertThat(segunda.codigoProyecto()).isEqualTo("PRJ-014");
        assertThat(segunda.observacion()).isNull();
    }

    @Test
    void deberiaTolerarElBomUtf8() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(0xEF);
        salida.write(0xBB);
        salida.write(0xBF);
        salida.write(("""
            rut_cliente;descripcion;periodo;fecha_facturacion;moneda;monto_neto
            76543210-9;Servicio;2026-01;15-01-2026;UF;12.5
            """).getBytes(StandardCharsets.UTF_8));

        List<FilaCsv> filas = lector.leer(salida.toByteArray());

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).rutCliente()).isEqualTo("76543210-9");
    }

    @Test
    void deberiaTolerarEspaciosAlrededorDeLosEncabezados() {
        String csv = """
             rut_cliente ; descripcion ; periodo ; fecha_facturacion ; moneda ; monto_neto
            76543210-9;Servicio;2026-01;15-01-2026;UF;12.5
            """;

        List<FilaCsv> filas = lector.leer(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).montoNeto()).isEqualTo("12.5");
    }

    @Test
    void deberiaRechazarUnArchivoVacio() {
        assertThatThrownBy(() -> lector.leer(new byte[0]))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasFieldOrPropertyWithValue("codigo", "CSV_VACIO");
    }

    @Test
    void deberiaRechazarUnArchivoSinFilasDeDatos() {
        String csv = "rut_cliente;descripcion;periodo;fecha_facturacion;moneda;monto_neto\n";

        assertThatThrownBy(() -> lector.leer(csv.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasFieldOrPropertyWithValue("codigo", "CSV_SIN_FILAS");
    }

    @Test
    void deberiaRechazarUnArchivoAlQueLeFaltanColumnasObligatorias() {
        String csv = """
            rut_cliente;descripcion
            76543210-9;Servicio
            """;

        assertThatThrownBy(() -> lector.leer(csv.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasFieldOrPropertyWithValue("codigo", "CSV_COLUMNAS_FALTANTES");
    }
}
