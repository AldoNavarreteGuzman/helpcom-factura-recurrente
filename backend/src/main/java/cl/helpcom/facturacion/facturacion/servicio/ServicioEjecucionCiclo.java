package cl.helpcom.facturacion.facturacion.servicio;

import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.dominio.EjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dto.EjecucionCicloRespuestaDto;
import cl.helpcom.facturacion.facturacion.repositorio.EjecucionCicloRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioEjecucionCiclo {

    private final EjecucionCicloRepositorio ejecucionCicloRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public ServicioEjecucionCiclo(EjecucionCicloRepositorio ejecucionCicloRepositorio, ContextoEmpresa contextoEmpresa) {
        this.ejecucionCicloRepositorio = ejecucionCicloRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<EjecucionCicloRespuestaDto> listar(Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        return ejecucionCicloRepositorio.findByEmpresaIdOrderByEjecutadoEnDesc(empresaId, pageable)
            .map(this::aRespuesta);
    }

    private EjecucionCicloRespuestaDto aRespuesta(EjecucionCiclo ejecucion) {
        return new EjecucionCicloRespuestaDto(
            ejecucion.getId(),
            ejecucion.getPeriodoAnio(),
            ejecucion.getPeriodoMes(),
            ejecucion.getEjecutadoEn(),
            ejecucion.getDisparo(),
            ejecucion.getCantidadGeneradas(),
            ejecucion.getCantidadPendientesUf(),
            ejecucion.getEstado(),
            ejecucion.getObservacion(),
            ejecucion.getEjecutadoPor());
    }
}
