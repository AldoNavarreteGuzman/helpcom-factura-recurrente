package cl.helpcom.facturacion.proyectos.controlador;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoSolicitudDto;
import cl.helpcom.facturacion.proyectos.servicio.ProyectoServicio;
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
public class ProyectoControlador {

    private final ProyectoServicio proyectoServicio;

    public ProyectoControlador(ProyectoServicio proyectoServicio) {
        this.proyectoServicio = proyectoServicio;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/proyectos")
    public PaginaRespuestaDto<ProyectoRespuestaDto> listar(
        @RequestParam(required = false) Long clienteId,
        @RequestParam(required = false) Periodicidad periodicidad,
        @RequestParam(required = false) Moneda moneda,
        @RequestParam(required = false) Boolean activo,
        Pageable pageable) {
        return PaginaRespuestaDto.desde(
            proyectoServicio.listar(clienteId, periodicidad, moneda, activo, pageable), dto -> dto);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/proyectos/{id}")
    public ProyectoRespuestaDto obtener(@PathVariable Long id) {
        return proyectoServicio.obtener(id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/api/v1/proyectos")
    public ResponseEntity<ProyectoRespuestaDto> crear(@Valid @RequestBody ProyectoSolicitudDto solicitud) {
        ProyectoRespuestaDto creado = proyectoServicio.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(creado.id())
            .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/api/v1/proyectos/{id}")
    public ProyectoRespuestaDto actualizar(@PathVariable Long id, @Valid @RequestBody ProyectoSolicitudDto solicitud) {
        return proyectoServicio.actualizar(id, solicitud);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/api/v1/proyectos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        proyectoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
