package cl.helpcom.facturacion.uf.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del módulo UF. {@code ttlCache} queda {@code null} si no se especifica:
 * el valor UF de una fecha ya publicada nunca cambia, por lo que el caché por defecto es
 * permanente (sin expiración), ver arquitectura-tecnica.md §8. Se deja configurable por si
 * operaciones decide acotarlo más adelante.
 */
@ConfigurationProperties(prefix = "app.uf")
public record PropiedadesUf(String baseUrl, Duration timeoutConexion, Duration timeoutLectura, Duration ttlCache) {

    private static final String BASE_URL_POR_DEFECTO = "https://www.mindicador.cl/api";
    private static final Duration TIMEOUT_POR_DEFECTO = Duration.ofSeconds(5);

    public PropiedadesUf {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = BASE_URL_POR_DEFECTO;
        }
        if (timeoutConexion == null) {
            timeoutConexion = TIMEOUT_POR_DEFECTO;
        }
        if (timeoutLectura == null) {
            timeoutLectura = TIMEOUT_POR_DEFECTO;
        }
    }
}
