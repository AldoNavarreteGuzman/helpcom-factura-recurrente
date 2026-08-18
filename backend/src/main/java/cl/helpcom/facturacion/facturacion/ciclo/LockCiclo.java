package cl.helpcom.facturacion.facturacion.ciclo;

import java.time.Duration;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Lock distribuido en Redis para evitar que dos ejecuciones del ciclo del mismo período
 * corran en paralelo (arquitectura-tecnica.md §12). Es solo una optimización: la
 * idempotencia real la garantizan {@code existsBy...} y el índice único parcial de la base
 * (ver {@link ServicioCicloFacturacion}), no este lock.
 *
 * <p>Si Redis no está disponible, {@link #adquirir} registra un WARN y retorna {@code true}
 * (se procede sin lock) en vez de bloquear el ciclo completo por una falla de caché. Si
 * Redis SÍ está disponible pero el lock ya está tomado por otra ejecución en curso,
 * {@link #adquirir} retorna {@code false} de verdad: en ese caso el llamador debe omitir la
 * corrida, no continuar.
 */
@Component
public class LockCiclo {

    private static final Logger log = LoggerFactory.getLogger(LockCiclo.class);
    private static final String PREFIJO_CLAVE = "ciclo:lock:";
    private static final String VALOR_LOCK = "1";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public LockCiclo(StringRedisTemplate redisTemplate, @Value("${app.ciclo.lock-ttl:30m}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public boolean adquirir(Long empresaId, int anio, int mes) {
        try {
            Boolean adquirido = redisTemplate.opsForValue().setIfAbsent(clave(empresaId, anio, mes), VALOR_LOCK, ttl);
            return Boolean.TRUE.equals(adquirido);
        } catch (DataAccessException ex) {
            log.warn(
                "No se pudo adquirir el lock de Redis para el ciclo {}-{} de la empresa {}: {}. "
                    + "Se continúa sin lock; la idempotencia real la garantiza la base de datos.",
                anio, mes, empresaId, ex.getMessage());
            return true;
        }
    }

    public void liberar(Long empresaId, int anio, int mes) {
        try {
            redisTemplate.delete(clave(empresaId, anio, mes));
        } catch (DataAccessException ex) {
            log.warn("No se pudo liberar el lock de Redis para el ciclo {}-{} de la empresa {}: {}",
                anio, mes, empresaId, ex.getMessage());
        }
    }

    private String clave(Long empresaId, int anio, int mes) {
        return PREFIJO_CLAVE + empresaId + ":" + YearMonth.of(anio, mes);
    }
}
