package cl.helpcom.facturacion.uf.dominio;

import java.time.LocalDate;

/**
 * Señala que no fue posible obtener el valor UF de una fecha por ninguna vía (caché,
 * persistencia ni fuente externa). El llamador (el ciclo de facturación) la traduce al
 * estado {@code PENDIENTE_UF} en vez de fallar el proceso completo.
 */
public class ValorUfNoDisponibleException extends RuntimeException {

    private final LocalDate fecha;

    public ValorUfNoDisponibleException(LocalDate fecha) {
        super("No fue posible obtener el valor de la UF para la fecha " + fecha);
        this.fecha = fecha;
    }

    public LocalDate getFecha() {
        return fecha;
    }
}
