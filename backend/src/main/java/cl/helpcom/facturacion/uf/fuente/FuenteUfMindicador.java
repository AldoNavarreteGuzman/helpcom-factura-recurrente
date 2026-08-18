package cl.helpcom.facturacion.uf.fuente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptador HTTP hacia mindicador.cl. El campo {@code fecha} de la serie se recibe y guarda
 * como texto (no como tipo fecha de Jackson) y se parsea a mano con {@code OffsetDateTime}:
 * evita depender de qué motor/versión de Jackson quede configurado como conversor por
 * defecto del {@code RestClient} para el soporte de {@code java.time}.
 */
@Component
public class FuenteUfMindicador implements FuenteUf {

    private static final Logger log = LoggerFactory.getLogger(FuenteUfMindicador.class);
    private static final DateTimeFormatter FORMATO_RUTA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestClient restClienteMindicador;
    private final ZoneId zonaHorariaNegocio;

    public FuenteUfMindicador(RestClient restClienteMindicador, ZoneId zonaHorariaNegocio) {
        this.restClienteMindicador = restClienteMindicador;
        this.zonaHorariaNegocio = zonaHorariaNegocio;
    }

    @Override
    public Optional<BigDecimal> consultarUf(LocalDate fecha) {
        try {
            RespuestaUfMindicadorDto respuesta = restClienteMindicador.get()
                .uri("/uf/{fecha}", fecha.format(FORMATO_RUTA))
                .retrieve()
                .body(RespuestaUfMindicadorDto.class);

            return extraerValorDelDia(respuesta, fecha);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar el valor UF en mindicador.cl para la fecha {}: {}", fecha, ex.getMessage());
            return Optional.empty();
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
