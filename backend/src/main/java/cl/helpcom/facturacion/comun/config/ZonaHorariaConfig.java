package cl.helpcom.facturacion.comun.config;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone la zona horaria de negocio ({@code America/Santiago}) como bean, para que el ciclo de
 * facturación y el resto de la lógica de dominio la usen de forma explícita en vez de depender
 * de la zona horaria por defecto de la JVM. Los {@code TIMESTAMPTZ} se persisten siempre en UTC.
 */
@Configuration
public class ZonaHorariaConfig {

    @Bean
    public ZoneId zonaHorariaNegocio(@Value("${facturacion.zona-horaria}") String zonaHorariaNegocio) {
        return ZoneId.of(zonaHorariaNegocio);
    }
}
