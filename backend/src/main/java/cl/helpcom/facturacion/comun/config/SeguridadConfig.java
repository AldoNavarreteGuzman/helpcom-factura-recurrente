package cl.helpcom.facturacion.comun.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadConfig {

    private static final String[] RUTAS_PUBLICAS = {
        "/actuator/health",
        "/actuator/health/**"
    };
    private static final String CLAIM_REALM_ACCESS = "realm_access";
    private static final String CLAIM_ROLES = "roles";
    private static final String PREFIJO_ROL = "ROLE_";

    @Bean
    public SecurityFilterChain cadenaFiltrosSeguridad(
        HttpSecurity http, CorsConfigurationSource fuenteConfiguracionCors) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(fuenteConfiguracionCors))
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(solicitudes -> solicitudes
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .requestMatchers(RUTAS_PUBLICAS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                jwt -> jwt.jwtAuthenticationConverter(conversorAutenticacionJwt())))
            .build();
    }

    /**
     * Sin esto, Spring Security no agrega NUNCA los headers {@code Access-Control-Allow-*} —
     * verificado contra el contenedor real: un preflight {@code OPTIONS} devolvía 401 sin
     * ningún header CORS, así que cualquier `fetch` del navegador con el header
     * {@code Authorization} (todas las llamadas de {@code lib/clienteApiCliente.ts}) quedaba
     * bloqueado por el navegador ANTES de llegar al backend, para cualquier frontend que no
     * pase por el proxy same-origin de solo-dev (`next.config.mjs`). Orígenes por variable de
     * entorno (nunca hardcodeados): {@code CORS_ORIGENES_PERMITIDOS}, lista separada por
     * comas — vacío por defecto en {@code dev}/{@code prod} (cada entorno declara el suyo,
     * un único origen estricto, docs/despliegue.md §5.4); {@code application-local.yml} fija
     * varios puertos típicos de {@code npm run dev} (3000-3002 — Next salta al siguiente
     * libre si el anterior está ocupado), SOLO en el perfil local — nunca se afloja la lista
     * de producción.
     */
    @Bean
    public CorsConfigurationSource fuenteConfiguracionCors(
        @Value("${app.cors.origenes-permitidos:}") String origenesPermitidosCrudos) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(parsearOrigenes(origenesPermitidosCrudos));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }

    /**
     * Parseo manual (en vez de dejar que {@code @Value} convierta directo a {@code List<String>})
     * para poder recortar espacios alrededor de cada origen — una lista escrita como
     * {@code "http://localhost:3000, http://localhost:3001"} (espacio después de la coma, hábito
     * humano común) rompería el match exacto de {@code Origin} si no se recorta, ya que
     * {@code CorsConfiguration} compara el origen tal cual. Entradas vacías (string vacío, o
     * comas de más) se descartan.
     */
    private List<String> parsearOrigenes(String origenesCrudos) {
        return Arrays.stream(origenesCrudos.split(","))
            .map(String::trim)
            .filter(origen -> !origen.isBlank())
            .toList();
    }

    /**
     * Keycloak entrega los roles de realm en el claim {@code realm_access.roles}, no en
     * {@code scope} (que es lo que el conversor por defecto de Spring Security usa). Este
     * conversor los mapea a autoridades {@code ROLE_ADMINISTRADOR} / {@code ROLE_OPERADOR}
     * para que {@code hasRole(...)} y {@code @PreAuthorize} funcionen.
     */
    private JwtAuthenticationConverter conversorAutenticacionJwt() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(this::extraerAutoridadesDeRealmAccess);
        return conversor;
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extraerAutoridadesDeRealmAccess(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null || !(realmAccess.get(CLAIM_ROLES) instanceof Collection<?> roles)) {
            return List.of();
        }
        Set<GrantedAuthority> autoridades = roles.stream()
            .map(Object::toString)
            .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO_ROL + rol))
            .collect(Collectors.toSet());
        return autoridades;
    }
}
