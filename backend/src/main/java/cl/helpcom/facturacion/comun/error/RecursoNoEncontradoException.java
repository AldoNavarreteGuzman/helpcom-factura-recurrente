package cl.helpcom.facturacion.comun.error;

public class RecursoNoEncontradoException extends ExcepcionAplicacion {

    public RecursoNoEncontradoException(String mensaje) {
        this(mensaje, null);
    }

    public RecursoNoEncontradoException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}
