package cl.helpcom.facturacion.uf.cache;

import cl.helpcom.facturacion.uf.config.PropiedadesUf;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Caché de valores UF por fecha en Redis, clave {@code uf:{yyyy-MM-dd}}. Usa
 * {@link StringRedisTemplate} (serializa como texto plano) para no arrastrar problemas de
 * serialización con {@link BigDecimal}. Si Redis no está disponible, no propaga el error:
 * el caché es una optimización, no un punto único de falla — se registra un WARN y el
 * llamador sigue con persistencia/fuente externa.
 */
@Component
public class CacheUf {

    private static final Logger log = LoggerFactory.getLogger(CacheUf.class);
    private static final String PREFIJO_CLAVE = "uf:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public CacheUf(StringRedisTemplate redisTemplate, PropiedadesUf propiedadesUf) {
        this.redisTemplate = redisTemplate;
        this.ttl = propiedadesUf.ttlCache();
    }

    public Optional<BigDecimal> obtener(LocalDate fecha) {
        try {
            String valor = redisTemplate.opsForValue().get(clave(fecha));
            return Optional.ofNullable(valor).map(BigDecimal::new);
        } catch (DataAccessException ex) {
            log.warn("No se pudo leer el caché Redis de UF para la fecha {}: {}", fecha, ex.getMessage());
            return Optional.empty();
        }
    }

    public void guardar(LocalDate fecha, BigDecimal valor) {
        try {
            if (ttl != null) {
                redisTemplate.opsForValue().set(clave(fecha), valor.toPlainString(), ttl);
            } else {
                // Sin expiración: el valor UF de una fecha ya publicada no cambia nunca.
                redisTemplate.opsForValue().set(clave(fecha), valor.toPlainString());
            }
        } catch (DataAccessException ex) {
            log.warn("No se pudo escribir en el caché Redis de UF para la fecha {}: {}", fecha, ex.getMessage());
        }
    }

    private String clave(LocalDate fecha) {
        return PREFIJO_CLAVE + fecha;
    }
}
