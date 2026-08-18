package cl.helpcom.facturacion.uf.fuente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Puerto hacia una fuente externa del valor UF (p. ej. mindicador.cl, o a futuro la CMF
 * como fuente oficial de contraste). Nunca propaga errores de red: si no puede resolver
 * el valor, retorna {@link Optional#empty()}.
 */
public interface FuenteUf {

    Optional<BigDecimal> consultarUf(LocalDate fecha);
}
