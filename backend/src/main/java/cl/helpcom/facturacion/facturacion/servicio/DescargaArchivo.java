package cl.helpcom.facturacion.facturacion.servicio;

import java.io.InputStream;

/** Lo que el controlador necesita para transmitir el PDF de vuelta al cliente. */
public record DescargaArchivo(String nombreOriginal, String tipoContenido, InputStream contenido) {
}
