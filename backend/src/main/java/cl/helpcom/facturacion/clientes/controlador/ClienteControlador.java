package cl.helpcom.facturacion.clientes.controlador;

import cl.helpcom.facturacion.clientes.dto.ClienteRespuestaDto;
import cl.helpcom.facturacion.clientes.dto.ClienteSolicitudDto;
import cl.helpcom.facturacion.clientes.servicio.ClienteServicio;
import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class ClienteControlador {

    private final ClienteServicio clienteServicio;

    public ClienteControlador(ClienteServicio clienteServicio) {
        this.clienteServicio = clienteServicio;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/clientes")
    public PaginaRespuestaDto<ClienteRespuestaDto> listar(
        @RequestParam(required = false) String texto,
        @RequestParam(required = false) Boolean activo,
        Pageable pageable) {
        return PaginaRespuestaDto.desde(clienteServicio.listar(texto, activo, pageable), dto -> dto);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/clientes/{id}")
    public ClienteRespuestaDto obtener(@PathVariable Long id) {
        return clienteServicio.obtener(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/api/v1/clientes")
    public ResponseEntity<ClienteRespuestaDto> crear(@Valid @RequestBody ClienteSolicitudDto solicitud) {
        ClienteRespuestaDto creado = clienteServicio.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(creado.id())
            .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/api/v1/clientes/{id}")
    public ClienteRespuestaDto actualizar(@PathVariable Long id, @Valid @RequestBody ClienteSolicitudDto solicitud) {
        return clienteServicio.actualizar(id, solicitud);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/api/v1/clientes/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
