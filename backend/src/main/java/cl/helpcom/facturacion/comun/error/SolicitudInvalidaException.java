package cl.helpcom.facturacion.comun.error;

public class SolicitudInvalidaException extends ExcepcionAplicacion {

    public SolicitudInvalidaException(String mensaje) {
        this(mensaje, null);
    }

    public SolicitudInvalidaException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}
