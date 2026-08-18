package cl.helpcom.facturacion.proyectos.controlador;

import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioSolicitudDto;
import cl.helpcom.facturacion.proyectos.servicio.AcuerdoPrecioServicio;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class AcuerdoPrecioControlador {

    private final AcuerdoPrecioServicio acuerdoPrecioServicio;

    public AcuerdoPrecioControlador(AcuerdoPrecioServicio acuerdoPrecioServicio) {
        this.acuerdoPrecioServicio = acuerdoPrecioServicio;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/proyectos/{proyectoId}/acuerdos")
    public List<AcuerdoPrecioRespuestaDto> listar(@PathVariable Long proyectoId) {
        return acuerdoPrecioServicio.listar(proyectoId);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/proyectos/{proyectoId}/acuerdos/{id}")
    public AcuerdoPrecioRespuestaDto obtener(@PathVariable Long proyectoId, @PathVariable Long id) {
        return acuerdoPrecioServicio.obtener(proyectoId, id);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/api/v1/proyectos/{proyectoId}/acuerdos")
    public ResponseEntity<AcuerdoPrecioRespuestaDto> crear(
        @PathVariable Long proyectoId, @Valid @RequestBody AcuerdoPrecioSolicitudDto solicitud) {
        AcuerdoPrecioRespuestaDto creado = acuerdoPrecioServicio.crear(proyectoId, solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(creado.id())
            .toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PutMapping("/api/v1/proyectos/{proyectoId}/acuerdos/{id}")
    public AcuerdoPrecioRespuestaDto actualizar(
        @PathVariable Long proyectoId, @PathVariable Long id, @Valid @RequestBody AcuerdoPrecioSolicitudDto solicitud) {
        return acuerdoPrecioServicio.actualizar(proyectoId, id, solicitud);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @DeleteMapping("/api/v1/proyectos/{proyectoId}/acuerdos/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long proyectoId, @PathVariable Long id) {
        acuerdoPrecioServicio.eliminar(proyectoId, id);
        return ResponseEntity.noContent().build();
    }
}
