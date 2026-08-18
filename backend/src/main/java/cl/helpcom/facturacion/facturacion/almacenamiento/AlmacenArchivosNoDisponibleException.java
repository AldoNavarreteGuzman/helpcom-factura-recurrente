package cl.helpcom.facturacion.facturacion.almacenamiento;

/** El almacén de objetos (local u OCI) no pudo completar la operación solicitada. */
public class AlmacenArchivosNoDisponibleException extends RuntimeException {

    public AlmacenArchivosNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
