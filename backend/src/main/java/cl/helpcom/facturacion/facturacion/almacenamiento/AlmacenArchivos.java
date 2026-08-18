package cl.helpcom.facturacion.facturacion.almacenamiento;

import java.io.InputStream;

/**
 * Puerto hacia el almacenamiento de objetos (arquitectura-tecnica.md §13): el binario del
 * PDF nunca se guarda en la base, solo la referencia. Es deliberadamente un almacén
 * genérico de bytes — no sabe nada de facturas ni valida tipos de contenido; esas reglas
 * son política del llamador (ver {@code ServicioFactura}), no de este puerto.
 *
 * <p>Implementaciones: {@code AlmacenArchivosLocal} (perfiles local/dev/test, por defecto)
 * y {@code AlmacenArchivosOci} (OCI Object Storage, API S3-compatible), seleccionadas por
 * {@code app.almacenamiento.tipo}.
 */
public interface AlmacenArchivos {

    ReferenciaArchivo guardar(String nombreOriginal, String tipoContenido, byte[] contenido);

    InputStream obtener(String claveObjeto);

    void eliminar(String claveObjeto);
}
