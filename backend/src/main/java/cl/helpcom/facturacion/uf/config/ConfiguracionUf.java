package cl.helpcom.facturacion.uf.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PropiedadesUf.class)
public class ConfiguracionUf {

    @Bean
    public RestClient restClienteMindicador(RestClient.Builder restClienteBuilder, PropiedadesUf propiedadesUf) {
        SimpleClientHttpRequestFactory fabricaSolicitudes = new SimpleClientHttpRequestFactory();
        fabricaSolicitudes.setConnectTimeout(propiedadesUf.timeoutConexion());
        fabricaSolicitudes.setReadTimeout(propiedadesUf.timeoutLectura());

        return restClienteBuilder
            .baseUrl(propiedadesUf.baseUrl())
            .requestFactory(fabricaSolicitudes)
            .build();
    }
}
