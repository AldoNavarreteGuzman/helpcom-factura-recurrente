package cl.helpcom.facturacion.importacion.csv;

/**
 * Una fila del CSV de importación (modelo-de-datos.md §6), tal como viene en el archivo: sin
 * parsear ni validar más allá de recortar espacios y convertir celdas vacías a {@code null}.
 * {@code numeroFila} es el número de línea del archivo (la línea 1 es el encabezado, así que
 * la primera fila de datos es la línea 2), útil para que el usuario ubique el error.
 */
public record FilaCsv(
    int numeroFila,
    String rutCliente,
    String codigoProyecto,
    String descripcion,
    String periodo,
    String fechaFacturacion,
    String moneda,
    String montoNeto,
    String observacion
) {
}
