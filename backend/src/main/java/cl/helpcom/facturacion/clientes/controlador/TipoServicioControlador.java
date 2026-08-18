package cl.helpcom.facturacion.clientes.controlador;

import cl.helpcom.facturacion.clientes.dto.TipoServicioRespuestaDto;
import cl.helpcom.facturacion.clientes.dto.TipoServicioSolicitudDto;
import cl.helpcom.facturacion.clientes.servicio.TipoServicioServicio;
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
public class TipoServicioControlador {

    private final TipoServicioServicio tipoServicioServicio;

    public TipoServicioControlador(TipoServicioServicio tipoServicioServicio) {
        this.tipoServicioServicio = tipoServicioServicio;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/tipos-servicio")
    public PaginaRespuestaDto<TipoServicioRespuestaDto> listar(
        @RequestParam(required = false) Boolean activo, Pageable pageable) {
        return PaginaRespuestaDto.desde(tipoServicioServicio.listar(activo, pageable), dto -> dto);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/tipos-servicio/{id}")
    public TipoServicioRespuestaDto obtener(@PathVariable Long id) {
        return tipoServicioServicio.obtener(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/api/v1/tipos-servicio")
    public ResponseEntity<TipoServicioRespuestaDto> crear(@Valid @RequestBody TipoServicioSolicitudDto solicitud) {
        TipoServicioRespuestaDto creado = tipoServicioServicio.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(creado.id())
            .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/api/v1/tipos-servicio/{id}")
    public TipoServicioRespuestaDto actualizar(
        @PathVariable Long id, @Valid @RequestBody TipoServicioSolicitudDto solicitud) {
        return tipoServicioServicio.actualizar(id, solicitud);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/api/v1/tipos-servicio/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        tipoServicioServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
