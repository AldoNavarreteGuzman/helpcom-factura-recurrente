package cl.helpcom.facturacion.comun.error;

public abstract class ExcepcionAplicacion extends RuntimeException {

    private final String codigo;

    protected ExcepcionAplicacion(String mensaje, String codigo) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
