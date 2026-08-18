package cl.helpcom.facturacion.uf.fuente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FuenteUfMindicadorTest {

    private static final ZoneId ZONA_SANTIAGO = ZoneId.of("America/Santiago");

    private MockRestServiceServer servidorMock;
    private FuenteUfMindicador fuenteUfMindicador;

    @BeforeEach
    void configurar() {
        RestClient.Builder constructor = RestClient.builder().baseUrl("https://www.mindicador.cl/api");
        servidorMock = MockRestServiceServer.bindTo(constructor).build();

        fuenteUfMindicador = new FuenteUfMindicador(constructor.build(), ZONA_SANTIAGO);
    }

    @Test
    void deberiaParsearElValorUfDesdeElJsonDeMindicadorParaLaFechaSolicitada() {
        String json = """
            {
              "version": "1.7.0",
              "autor": "mindicador.cl",
              "codigo": "uf",
              "nombre": "Unidad de fomento (UF)",
              "unidad_medida": "Pesos",
              "serie": [
                { "fecha": "2026-01-15T12:00:00.000Z", "valor": 38935.9 }
              ]
            }
            """;
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/15-01-2026"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(LocalDate.of(2026, 1, 15));

        assertThat(valor).isPresent();
        assertThat(valor.get()).isEqualByComparingTo("38935.9");
        servidorMock.verify();
    }

    @Test
    void deberiaRetornarOptionalVacioCuandoLaSerieNoTraeLaFechaSolicitada() {
        String json = """
            {
              "serie": []
            }
            """;
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/01-01-2000"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(LocalDate.of(2000, 1, 1));

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }

    @Test
    void deberiaRetornarOptionalVacioCuandoLaRespuestaEsUnErrorHttp() {
        LocalDate fecha = LocalDate.of(2026, 3, 3);
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/03-03-2026"))
            .andRespond(withServerError());

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }

    @Test
    void deberiaRetornarOptionalVacioCuandoHayUnErrorDeConexion() {
        LocalDate fecha = LocalDate.of(2026, 4, 4);
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/04-04-2026"))
            .andRespond(request -> {
                throw new IOException("Fallo de conexión simulado");
            });

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }
}
