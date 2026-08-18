package cl.helpcom.facturacion.clientes.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.clientes.dto.ClienteRespuestaDto;
import cl.helpcom.facturacion.clientes.dto.ClienteSolicitudDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code ClienteServicio.listar} no tenía ninguna cobertura contra Postgres real (ni contra
 * mocks siquiera — {@code ClienteServicioTest} solo cubre crear/eliminar con Mockito, nunca
 * ejercita {@code ClienteEspecificaciones}/{@code Specification.allOf} de verdad). Cubre el
 * escenario exacto de la pantalla al cargar (listar sin filtros, "Estado = Todos") y la
 * creación con un RUT real — contra PostgreSQL 16 vía Testcontainers, con las 11 migraciones
 * aplicadas, igual que {@code EsquemaBaseDatosTest}. {@code @Transactional} hace rollback al
 * final de cada test, así que ambos quedan aislados entre sí sin duplicar el RUT.
 */
@Testcontainers
@SpringBootTest
@Transactional
class ClienteServicioIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ClienteServicio clienteServicio;

    @Test
    void deberiaCrearUnClienteConRutReal() {
        ClienteSolicitudDto solicitud = new ClienteSolicitudDto(
            "12.335.545-8", "Helpcom Ltda", null, null, null, null, null, true);

        ClienteRespuestaDto creado = clienteServicio.crear(solicitud);

        assertThat(creado.id()).isNotNull();
        assertThat(creado.rut()).isEqualTo("12335545-8");
    }

    @Test
    void deberiaListarSinFiltrosComoLoHaceLaPantallaAlCargar() {
        clienteServicio.crear(new ClienteSolicitudDto(
            "12.335.545-8", "Helpcom Ltda", null, null, null, null, null, true));

        Page<ClienteRespuestaDto> pagina = clienteServicio.listar(null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).isNotEmpty();
    }

    @Test
    void deberiaListarFiltrandoPorActivoYPorTexto() {
        clienteServicio.crear(new ClienteSolicitudDto(
            "12.335.545-8", "Helpcom Ltda", null, null, null, null, null, true));

        Page<ClienteRespuestaDto> porActivo = clienteServicio.listar(null, true, PageRequest.of(0, 20));
        Page<ClienteRespuestaDto> porTexto = clienteServicio.listar("Helpcom", null, PageRequest.of(0, 20));

        assertThat(porActivo.getContent()).isNotEmpty();
        assertThat(porTexto.getContent()).isNotEmpty();
    }
}
