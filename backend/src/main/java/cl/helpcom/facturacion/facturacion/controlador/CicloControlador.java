package cl.helpcom.facturacion.facturacion.controlador;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.facturacion.ciclo.ResultadoCiclo;
import cl.helpcom.facturacion.facturacion.ciclo.ServicioCicloFacturacion;
import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.EjecutarCicloSolicitudDto;
import cl.helpcom.facturacion.facturacion.servicio.ServicioEjecucionCiclo;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CicloControlador {

    private final ServicioCicloFacturacion servicioCicloFacturacion;
    private final ServicioEjecucionCiclo servicioEjecucionCiclo;
    private final ZoneId zonaHorariaNegocio;

    public CicloControlador(
        ServicioCicloFacturacion servicioCicloFacturacion,
        ServicioEjecucionCiclo servicioEjecucionCiclo,
        ZoneId zonaHorariaNegocio) {
        this.servicioCicloFacturacion = servicioCicloFacturacion;
        this.servicioEjecucionCiclo = servicioEjecucionCiclo;
        this.zonaHorariaNegocio = zonaHorariaNegocio;
    }

    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @PostMapping("/api/v1/ciclos/ejecutar")
    public ResultadoCiclo ejecutar(@Valid @RequestBody(required = false) EjecutarCicloSolicitudDto solicitud) {
        LocalDate hoy = LocalDate.now(zonaHorariaNegocio);
        int anio = resolverAnio(solicitud, hoy);
        int mes = resolverMes(solicitud, hoy);
        return servicioCicloFacturacion.ejecutarCiclo(anio, mes, DisparoCiclo.MANUAL);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/ciclos")
    public PaginaRespuestaDto<EjecucionCicloRespuestaDto> listar(Pageable pageable) {
        return PaginaRespuestaDto.desde(servicioEjecucionCiclo.listar(pageable), dto -> dto);
    }

    private int resolverAnio(EjecutarCicloSolicitudDto solicitud, LocalDate hoy) {
        return (solicitud != null && solicitud.anio() != null) ? solicitud.anio() : hoy.getYear();
    }

    private int resolverMes(EjecutarCicloSolicitudDto solicitud, LocalDate hoy) {
        return (solicitud != null && solicitud.mes() != null) ? solicitud.mes() : hoy.getMonthValue();
    }
}
