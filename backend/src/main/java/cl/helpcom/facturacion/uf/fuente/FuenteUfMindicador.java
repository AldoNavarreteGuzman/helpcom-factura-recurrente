package cl.helpcom.facturacion.uf.fuente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador HTTP hacia mindicador.cl. El campo {@code fecha} de la serie se recibe y guarda
 * como texto (no como tipo fecha de Jackson) y se parsea a mano con {@code OffsetDateTime}:
 * evita depender de qué motor/versión de Jackson quede configurado como conversor por
 * defecto del {@code RestClient} para el soporte de {@code java.time}.
 *
 * <p><b>Reintento con backoff ante fallo transitorio (deuda-tecnica.md ítem 8):</b>
 * mindicador.cl es una API pública de terceros, sin SLA. Se verificaron en vivo, contra el
 * servicio real, DOS modos de falla transitoria distintos, ambos intermitentes para la MISMA
 * fecha (no ligados a fechas puntuales) — confirmado repitiendo la misma llamada varias veces
 * seguidas:
 * <ul>
 *   <li>Cuelgues hasta el timeout de conexión pese a que el TCP/TLS conecta al instante
 *       (verificado comparando contenedor vs. host: mismo patrón en ambos, así que es un
 *       problema del lado de mindicador.cl, no de nuestra red) — {@link
 *       org.springframework.web.client.ResourceAccessException}.</li>
 *   <li><b>Hallazgo real durante la verificación de este arreglo:</b> a veces responde
 *       {@code 200 OK} con el JSON correcto en el cuerpo pero el header {@code Content-Type}
 *       mal declarado como {@code text/html} en vez de {@code application/json} — el
 *       {@code RestClient} no logra parsear el cuerpo ({@code UnknownContentTypeException}) y
 *       la fecha en cuestión SÍ tenía UF publicada. Confirmado intermitente repitiendo la
 *       misma URL: unas veces con el header correcto, otras no.</li>
 * </ul>
 * Como {@link cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion} es un snapshot
 * inmutable y el índice único parcial del ciclo (`uq_prop_ciclo_periodo`) bloquea cualquier
 * regeneración posterior para ese (proyecto, período) — incluso si la propuesta se anula — un
 * solo fallo transitorio en la única ventana de intento dejaba la propuesta en {@code
 * PENDIENTE_UF} de forma PERMANENTE, sin ningún camino de recuperación por dominio.
 *
 * <p>{@link #MAX_INTENTOS} y {@link #ESPERA_BASE} cubren cualquier {@link RestClientException}
 * EXCEPTO {@link HttpClientErrorException} (4xx): un 4xx es una respuesta real y bien formada
 * del servidor rechazando la solicitud (posible error de nuestro lado: formato de URL) — no un
 * síntoma de mindicador.cl fallando momentáneamente, así que reintentarlo no cambiaría nada. Una
 * respuesta 200 sin la fecha en la serie (UF real y legítimamente no publicada todavía) tampoco
 * se reintenta, porque ese camino ni siquiera lanza excepción — sigue siendo, a propósito, un
 * {@code PENDIENTE_UF} legítimo (arquitectura-tecnica.md §8/§9): el reintento reduce el {@code
 * PENDIENTE_UF} a "de verdad no disponible", no lo elimina.
 */
@Component
public class FuenteUfMindicador implements FuenteUf {

    private static final Logger log = LoggerFactory.getLogger(FuenteUfMindicador.class);
    private static final DateTimeFormatter FORMATO_RUTA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * 3 intentos en total (1 + 2 reintentos): los éxitos observados contra mindicador.cl
     * completan en 1,5-3,4 s (muy por debajo del timeout de 5 s de {@code PropiedadesUf}), y
     * la tasa de éxito por intento medida ronda 60-90% incluso en sus momentos más
     * degradados — con eso, la probabilidad de agotar 3 intentos seguidos es baja sin alargar
     * demasiado el peor caso. El costo está acotado: {@link
     * cl.helpcom.facturacion.uf.servicio.ServicioUfImpl} cachea por fecha, así que dentro de un
     * mismo ciclo cada fecha distinta paga este costo una sola vez, sin importar cuántos
     * proyectos se facturen ese día.
     */
    private static final int MAX_INTENTOS = 3;

    /**
     * Backoff lineal corto: 500 ms antes del 2º intento, 1000 ms antes del 3º — sub-segundo a
     * ~1 s, como corresponde a un fallo que se espera que se resuelva solo en segundos, no en
     * minutos (no es un backoff exponencial de reintento agresivo tipo circuit-breaker: esto
     * corre síncrono dentro del ciclo/importación CSV, con solo 2 pausas como máximo por fecha
     * distinta).
     */
    private static final Duration ESPERA_BASE = Duration.ofMillis(500);

    private final RestClient restClienteMindicador;
    private final ZoneId zonaHorariaNegocio;

    public FuenteUfMindicador(RestClient restClienteMindicador, ZoneId zonaHorariaNegocio) {
        this.restClienteMindicador = restClienteMindicador;
        this.zonaHorariaNegocio = zonaHorariaNegocio;
    }

    @Override
    public Optional<BigDecimal> consultarUf(LocalDate fecha) {
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                RespuestaUfMindicadorDto respuesta = restClienteMindicador.get()
                    .uri("/uf/{fecha}", fecha.format(FORMATO_RUTA))
                    .retrieve()
                    .body(RespuestaUfMindicadorDto.class);

                return extraerValorDelDia(respuesta, fecha);
            } catch (HttpClientErrorException ex) {
                // 4xx: respuesta real y bien formada rechazando la solicitud — no un síntoma
                // de mindicador.cl fallando momentáneamente. No se reintenta.
                log.warn("No se pudo consultar el valor UF en mindicador.cl para la fecha {}: {}", fecha, ex.getMessage());
                return Optional.empty();
            } catch (RestClientException ex) {
                // Todo lo demás (timeout/conexión, 5xx, Content-Type mal declarado en un 200
                // con JSON válido — ver el hallazgo documentado en la clase) es un fallo
                // TRANSITORIO del lado de mindicador.cl: se reintenta.
                log.warn(
                    "Fallo transitorio consultando el valor UF en mindicador.cl para la fecha {} "
                        + "(intento {}/{}): {}",
                    fecha, intento, MAX_INTENTOS, ex.getMessage());
                if (intento == MAX_INTENTOS) {
                    return Optional.empty();
                }
                esperarAntesDelSiguienteIntento(intento);
            }
        }
        return Optional.empty();
    }

    private void esperarAntesDelSiguienteIntento(int intentoActual) {
        try {
            Thread.sleep(ESPERA_BASE.multipliedBy(intentoActual).toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Optional<BigDecimal> extraerValorDelDia(RespuestaUfMindicadorDto respuesta, LocalDate fecha) {
        if (respuesta == null || respuesta.serie() == null) {
            return Optional.empty();
        }
        return respuesta.serie().stream()
            .filter(item -> correspondeALaFecha(item, fecha))
            .map(ItemSerieUfDto::valor)
            .findFirst();
    }

    private boolean correspondeALaFecha(ItemSerieUfDto item, LocalDate fecha) {
        if (item.fecha() == null) {
            return false;
        }
        try {
            LocalDate fechaItem = OffsetDateTime.parse(item.fecha())
                .atZoneSameInstant(zonaHorariaNegocio)
                .toLocalDate();
            return fechaItem.equals(fecha);
        } catch (RuntimeException ex) {
            log.warn("No se pudo interpretar la fecha '{}' recibida de mindicador.cl: {}", item.fecha(), ex.getMessage());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaUfMindicadorDto(List<ItemSerieUfDto> serie) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ItemSerieUfDto(String fecha, BigDecimal valor) {
    }
}
