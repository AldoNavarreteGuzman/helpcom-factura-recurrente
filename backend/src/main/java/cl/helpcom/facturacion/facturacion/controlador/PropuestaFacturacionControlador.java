package cl.helpcom.facturacion.facturacion.controlador;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.facturacion.servicio.ServicioPropuestaFacturacion;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PropuestaFacturacionControlador {

    private final ServicioPropuestaFacturacion servicioPropuestaFacturacion;

    public PropuestaFacturacionControlador(ServicioPropuestaFacturacion servicioPropuestaFacturacion) {
        this.servicioPropuestaFacturacion = servicioPropuestaFacturacion;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/propuestas")
    public PaginaRespuestaDto<PropuestaFacturacionRespuestaDto> listar(
        @RequestParam(required = false) Integer periodoAnio,
        @RequestParam(required = false) Integer periodoMes,
        @RequestParam(required = false) Long clienteId,
        @RequestParam(required = false) EstadoPropuesta estado,
        @RequestParam(required = false) OrigenPropuesta origen,
        Pageable pageable) {
        return PaginaRespuestaDto.desde(
            servicioPropuestaFacturacion.listar(periodoAnio, periodoMes, clienteId, estado, origen, pageable),
            dto -> dto);
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/api/v1/propuestas/{id}/anular")
    public ResponseEntity<Void> anular(@PathVariable Long id) {
        servicioPropuestaFacturacion.anular(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PatchMapping("/api/v1/propuestas/{id}/reprocesar-uf")
    public PropuestaFacturacionRespuestaDto reprocesarUf(@PathVariable Long id) {
        return servicioPropuestaFacturacion.reprocesarUf(id);
    }
}
