package cl.helpcom.facturacion.uf.servicio;

import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Puerto principal del módulo UF: resuelve el valor de la UF (CLP por 1 UF) para una fecha.
 */
public interface ServicioUf {

    /**
     * @throws ValorUfNoDisponibleException si no se pudo obtener el valor por ninguna vía
     *     (caché, persistencia ni fuente externa).
     */
    BigDecimal obtenerValorUf(LocalDate fecha);
}
