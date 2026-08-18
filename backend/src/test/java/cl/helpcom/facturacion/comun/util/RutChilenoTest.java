package cl.helpcom.facturacion.comun.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RutChilenoTest {

    @Test
    void deberiaNormalizarUnRutValidoSinFormatoAlCanonico() {
        Optional<String> resultado = RutChileno.normalizar("12345678-5");

        assertThat(resultado).contains("12345678-5");
    }

    @Test
    void deberiaNormalizarUnRutConPuntosYEspaciosAlFormatoCanonico() {
        Optional<String> resultado = RutChileno.normalizar(" 12.345.678-5 ");

        assertThat(resultado).contains("12345678-5");
    }

    @Test
    void deberiaAceptarDigitoVerificadorKEnMayuscula() {
        Optional<String> resultado = RutChileno.normalizar("6-k");

        assertThat(resultado).contains("6-K");
    }

    @Test
    void deberiaRechazarUnRutConDigitoVerificadorIncorrecto() {
        assertThat(RutChileno.normalizar("12345678-9")).isEmpty();
        assertThat(RutChileno.esValido("12345678-9")).isFalse();
    }

    @Test
    void deberiaRechazarTextoQueNoEsUnRut() {
        assertThat(RutChileno.normalizar("no-es-un-rut")).isEmpty();
        assertThat(RutChileno.normalizar(null)).isEmpty();
        assertThat(RutChileno.normalizar("")).isEmpty();
    }
}
