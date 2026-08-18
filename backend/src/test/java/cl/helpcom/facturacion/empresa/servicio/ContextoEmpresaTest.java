package cl.helpcom.facturacion.empresa.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContextoEmpresaTest {

    private final ContextoEmpresa contextoEmpresa = new ContextoEmpresa();

    @Test
    void deberiaRetornarSiempreElEmpresaIdFijoDeHelpcom() {
        assertThat(contextoEmpresa.obtenerEmpresaId()).isEqualTo(1L);
    }
}
