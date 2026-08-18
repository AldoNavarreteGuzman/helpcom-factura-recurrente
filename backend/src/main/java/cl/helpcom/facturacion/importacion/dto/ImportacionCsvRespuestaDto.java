package cl.helpcom.facturacion.importacion.dto;

import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import java.time.OffsetDateTime;

/**
 * {@code filasOk} cuenta las filas efectivamente importadas — estado {@code OK} o
 * {@code ADVERTENCIA} en la previsualización, es decir, la suma de las propuestas creadas en
 * {@code PENDIENTE} y en {@code PENDIENTE_UF}; {@code filasError} cuenta las que no se
 * importaron.
 *
 * <p>{@code cantidadPendienteUf} es el SUBCONJUNTO de {@code filasOk} que, al momento de ESTA
 * confirmación, quedó en estado {@code PENDIENTE_UF} (sin valor UF disponible para su fecha) —
 * se lee como {@code filasOk = (filasOk - cantidadPendienteUf) con valor + cantidadPendienteUf
 * sin UF}; {@code filasError} queda aparte, no se solapa con ninguno de los dos. Es el
 * contador REAL (no un estimado): se cuenta durante {@code ServicioImportacionCsv.confirmar},
 * que es donde se crean las propuestas y se conoce su estado final — puede diferir del
 * estimado que mostró la previsualización si, por ejemplo, la UF se volvió disponible entre
 * previsualizar y confirmar (`docs/frontend.md` §7.4, resuelto).
 *
 * <p>Queda en {@code null} en las filas del historial ({@code ServicioImportacionCsv.listar})
 * porque no hay una columna persistida para esto en {@code importacion_csv} — nunca se
 * inventa un valor para no mentir con un 0 que no se verificó; ver la evaluación de si vale la
 * pena persistirlo en `docs/deuda-tecnica.md`.
 */
public record ImportacionCsvRespuestaDto(
    Long id,
    String nombreArchivo,
    OffsetDateTime fechaImportacion,
    int totalFilas,
    int filasOk,
    Integer cantidadPendienteUf,
    int filasError,
    EstadoImportacionCsv estado
) {
}
