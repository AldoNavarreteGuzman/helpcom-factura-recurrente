package cl.helpcom.facturacion.facturacion.almacenamiento;

import java.util.UUID;

/**
 * Esquema de claves compartido por los adaptadores: {@code <uuid-v4><extensión-original>}
 * (p. ej. {@code 550e8400-e29b-41d4-a716-446655440000.pdf}), sin subcarpetas por fecha. Se
 * eligió así por simplicidad (regla de oro "simplicidad primero"): el volumen esperado en
 * Etapa 1 (PDFs de facturas de una sola empresa) no justifica una jerarquía de directorios,
 * y el UUID ya garantiza que no hay colisiones.
 */
public final class GeneradorClaveObjeto {

    private GeneradorClaveObjeto() {
    }

    public static String generar(String nombreOriginal) {
        return UUID.randomUUID() + extraerExtension(nombreOriginal);
    }

    private static String extraerExtension(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        int indicePunto = nombreOriginal.lastIndexOf('.');
        return indicePunto >= 0 ? nombreOriginal.substring(indicePunto) : "";
    }
}
