package cl.helpcom.facturacion.importacion.csv;

import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/**
 * Parseo del CSV de importación (modelo-de-datos.md §6) con Apache Commons CSV: separador
 * {@code ;}, primera fila de encabezados, codificación UTF-8 tolerante a BOM y a espacios
 * alrededor de los nombres de columna. No valida el CONTENIDO de las celdas — solo la
 * estructura del archivo (encabezado presente, columnas obligatorias, al menos una fila de
 * datos); el contenido de cada fila lo valida {@code ValidadorFilaCsv}.
 */
@Component
public class LectorCsv {

    private static final byte[] BOM_UTF8 = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String[] COLUMNAS_REQUERIDAS = {
        "rut_cliente", "descripcion", "periodo", "fecha_facturacion", "moneda", "monto_neto"
    };
    private static final CSVFormat FORMATO = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setDelimiter(';')
        .setIgnoreEmptyLines(true)
        .build();

    public List<FilaCsv> leer(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new SolicitudInvalidaException("El archivo CSV está vacío.", "CSV_VACIO");
        }

        List<CSVRecord> registros;
        try (ByteArrayInputStream entrada = new ByteArrayInputStream(quitarBom(contenido))) {
            CSVParser parser = CSVParser.parse(entrada, StandardCharsets.UTF_8, FORMATO);
            registros = parser.getRecords();
        } catch (IOException ex) {
            throw new SolicitudInvalidaException("No se pudo leer el archivo CSV: " + ex.getMessage(), "CSV_ILEGIBLE");
        }

        if (registros.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo CSV no tiene encabezado.", "CSV_SIN_ENCABEZADO");
        }

        Map<String, Integer> indices = indexarEncabezado(registros.get(0));
        validarColumnasRequeridas(indices);

        List<FilaCsv> filas = new ArrayList<>();
        for (int i = 1; i < registros.size(); i++) {
            CSVRecord registro = registros.get(i);
            filas.add(new FilaCsv(
                i + 1,
                valor(registro, indices, "rut_cliente"),
                valor(registro, indices, "codigo_proyecto"),
                valor(registro, indices, "descripcion"),
                valor(registro, indices, "periodo"),
                valor(registro, indices, "fecha_facturacion"),
                valor(registro, indices, "moneda"),
                valor(registro, indices, "monto_neto"),
                valor(registro, indices, "observacion")));
        }

        if (filas.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo CSV no tiene filas de datos.", "CSV_SIN_FILAS");
        }
        return filas;
    }

    private byte[] quitarBom(byte[] contenido) {
        if (contenido.length >= 3
            && contenido[0] == BOM_UTF8[0] && contenido[1] == BOM_UTF8[1] && contenido[2] == BOM_UTF8[2]) {
            byte[] sinBom = new byte[contenido.length - 3];
            System.arraycopy(contenido, 3, sinBom, 0, sinBom.length);
            return sinBom;
        }
        return contenido;
    }

    private Map<String, Integer> indexarEncabezado(CSVRecord encabezado) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < encabezado.size(); i++) {
            indices.put(encabezado.get(i).trim().toLowerCase(), i);
        }
        return indices;
    }

    private void validarColumnasRequeridas(Map<String, Integer> indices) {
        List<String> faltantes = new ArrayList<>();
        for (String columna : COLUMNAS_REQUERIDAS) {
            if (!indices.containsKey(columna)) {
                faltantes.add(columna);
            }
        }
        if (!faltantes.isEmpty()) {
            throw new SolicitudInvalidaException(
                "Faltan columnas obligatorias en el CSV: " + String.join(", ", faltantes), "CSV_COLUMNAS_FALTANTES");
        }
    }

    private String valor(CSVRecord registro, Map<String, Integer> indices, String columna) {
        Integer indice = indices.get(columna);
        if (indice == null || indice >= registro.size()) {
            return null;
        }
        String valor = registro.get(indice).trim();
        return valor.isEmpty() ? null : valor;
    }
}
