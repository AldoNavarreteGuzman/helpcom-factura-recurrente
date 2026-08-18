package cl.helpcom.facturacion.comun.error;

public class ReglaNegocioException extends ExcepcionAplicacion {

    public ReglaNegocioException(String mensaje) {
        this(mensaje, null);
    }

    public ReglaNegocioException(String mensaje, String codigo) {
        super(mensaje, codigo);
    }
}
