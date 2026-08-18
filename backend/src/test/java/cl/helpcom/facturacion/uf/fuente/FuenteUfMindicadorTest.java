package cl.helpcom.facturacion.uf.fuente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
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
    void deberiaReintentarYRetornarOptionalVacioCuandoLaRespuestaEsSiempreUnErrorHttp5xx() {
        // 5xx es un fallo TRANSITORIO (deuda-tecnica.md ítem 8): se reintenta hasta agotar
        // MAX_INTENTOS (3) antes de degradar — las 3 llamadas deben ocurrir, en orden.
        LocalDate fecha = LocalDate.of(2026, 3, 3);
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/03-03-2026"))
            .andRespond(withServerError());
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/03-03-2026"))
            .andRespond(withServerError());
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/03-03-2026"))
            .andRespond(withServerError());

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }

    @Test
    void deberiaReintentarYRetornarOptionalVacioCuandoHaySiempreUnErrorDeConexion() {
        // Timeout/error de conexión es TRANSITORIO igual que el 5xx: mismo agotamiento de
        // MAX_INTENTOS (3) antes de degradar a PENDIENTE_UF.
        LocalDate fecha = LocalDate.of(2026, 4, 4);
        for (int i = 0; i < 3; i++) {
            servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/04-04-2026"))
                .andRespond(request -> {
                    throw new IOException("Fallo de conexión simulado");
                });
        }

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }

    @Test
    void deberiaRecuperarElValorTrasUnFalloTransitorioSeguidoDeExito() {
        // Caso central del arreglo (deuda-tecnica.md ítem 8): el primer intento falla por
        // timeout/conexión, el segundo responde bien — el reintento debe recuperar el valor
        // real en vez de degradar a PENDIENTE_UF. Contra el código anterior (sin reintento,
        // una sola llamada) este test falla: el primer fallo ya devolvía Optional.empty().
        LocalDate fecha = LocalDate.of(2026, 5, 15);
        String json = """
            {
              "serie": [
                { "fecha": "2026-05-15T04:00:00.000Z", "valor": 40340.86 }
              ]
            }
            """;
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/15-05-2026"))
            .andRespond(request -> {
                throw new IOException("Timeout simulado en el primer intento");
            });
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/15-05-2026"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isPresent();
        assertThat(valor.get()).isEqualByComparingTo("40340.86");
        servidorMock.verify();
    }

    @Test
    void deberiaReintentarYRecuperarCuandoElContentTypeVieneMalDeclarado() {
        // Hallazgo real verificado contra mindicador.cl en vivo (deuda-tecnica.md ítem 8):
        // a veces responde 200 con el JSON correcto en el cuerpo pero Content-Type text/html
        // en vez de application/json — el RestClient no puede parsearlo
        // (UnknownContentTypeException), y la fecha SÍ tenía UF publicada. Es tan transitorio
        // como el 5xx o el timeout: se reintenta y se recupera.
        LocalDate fecha = LocalDate.of(2026, 1, 19);
        String json = """
            {
              "serie": [
                { "fecha": "2026-01-19T03:00:00.000Z", "valor": 39736.85 }
              ]
            }
            """;
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/19-01-2026"))
            .andRespond(withSuccess(json, MediaType.TEXT_HTML));
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/19-01-2026"))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isPresent();
        assertThat(valor.get()).isEqualByComparingTo("39736.85");
        servidorMock.verify();
    }

    @Test
    void noDeberiaReintentarUnErrorHttp4xxYDeberiaRetornarOptionalVacioDeInmediato() {
        // Un 4xx no es un problema momentáneo de mindicador.cl (es una respuesta real, aunque
        // de error) — no se reintenta. Solo 1 llamada esperada: si el código reintentara,
        // MockRestServiceServer fallaría por no tener una segunda expectativa registrada.
        LocalDate fecha = LocalDate.of(2026, 6, 6);
        servidorMock.expect(requestTo("https://www.mindicador.cl/api/uf/06-06-2026"))
            .andRespond(withBadRequest());

        Optional<BigDecimal> valor = fuenteUfMindicador.consultarUf(fecha);

        assertThat(valor).isEmpty();
        servidorMock.verify();
    }
}
