package cl.helpcom.facturacion.comun.config;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "proveedorAuditor", dateTimeProviderRef = "proveedorFechaHora")
public class AuditoriaConfig {

    private static final String USUARIO_SISTEMA = "sistema";

    @Bean
    public AuditorAware<String> proveedorAuditor() {
        return () -> {
            Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
            if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || autenticacion instanceof AnonymousAuthenticationToken) {
                return Optional.of(USUARIO_SISTEMA);
            }
            return Optional.of(autenticacion.getName());
        };
    }

    /**
     * El {@link DateTimeProvider} por defecto de Spring Data no sabe convertir su
     * {@code LocalDateTime} interno a {@code OffsetDateTime} (el tipo que usa
     * {@code EntidadAuditable} para creado_en/modificado_en, coherente con TIMESTAMPTZ). Se
     * provee explícitamente para no depender de esa conversión implícita.
     */
    @Bean
    public DateTimeProvider proveedorFechaHora() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
