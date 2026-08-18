package cl.helpcom.facturacion.facturacion.servicio;

import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.util.OrdenPorDefecto;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.dto.PropuestaFacturacionRespuestaDto;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionEspecificaciones;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicioPropuestaFacturacion {

    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public ServicioPropuestaFacturacion(
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio, ContextoEmpresa contextoEmpresa) {
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<PropuestaFacturacionRespuestaDto> listar(
        Integer periodoAnio, Integer periodoMes, Long clienteId, EstadoPropuesta estado, OrigenPropuesta origen,
        Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Specification<PropuestaFacturacion> especificacion = PropuestaFacturacionEspecificaciones.combinar(
            PropuestaFacturacionEspecificaciones.paraEmpresa(empresaId),
            PropuestaFacturacionEspecificaciones.conPeriodo(periodoAnio, periodoMes),
            PropuestaFacturacionEspecificaciones.conCliente(clienteId),
            PropuestaFacturacionEspecificaciones.conEstado(estado),
            PropuestaFacturacionEspecificaciones.conOrigen(origen),
            PropuestaFacturacionEspecificaciones.conFacturaFetch());

        Pageable paginaOrdenada =
            OrdenPorDefecto.aplicar(pageable, PropuestaFacturacionEspecificaciones.ORDEN_POR_DEFECTO);
        return propuestaFacturacionRepositorio.findAll(especificacion, paginaOrdenada).map(this::aRespuesta);
    }

    /**
     * Solo propuestas PENDIENTE o PENDIENTE_UF pueden anularse. Una propuesta FACTURADA no
     * se anula directamente: hay que desasociarla de su factura primero — funcionalidad de
     * desasociación fuera del alcance de esta etapa, así que por ahora se rechaza con un
     * mensaje explícito en vez de dejarla en un estado inconsistente (ANULADA con
     * factura_id no nulo no tendría sentido).
     */
    @Transactional
    public void anular(Long propuestaId) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        PropuestaFacturacion propuesta = propuestaFacturacionRepositorio.findByIdAndEmpresaId(propuestaId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe una propuesta con id " + propuestaId, "PROPUESTA_NO_ENCONTRADA"));

        if (propuesta.getEstado() == EstadoPropuesta.FACTURADA) {
            throw new ReglaNegocioException(
                "La propuesta " + propuestaId + " ya está facturada; no se puede anular sin "
                    + "desasociarla de su factura primero.",
                "PROPUESTA_FACTURADA_NO_SE_PUEDE_ANULAR");
        }
        if (propuesta.getEstado() != EstadoPropuesta.PENDIENTE && propuesta.getEstado() != EstadoPropuesta.PENDIENTE_UF) {
            throw new ReglaNegocioException(
                "La propuesta " + propuestaId + " está en estado " + propuesta.getEstado()
                    + " y no se puede anular.",
                "PROPUESTA_NO_ANULABLE");
        }
        propuesta.setEstado(EstadoPropuesta.ANULADA);
    }

    private PropuestaFacturacionRespuestaDto aRespuesta(PropuestaFacturacion propuesta) {
        Proyecto proyecto = propuesta.getProyecto();
        Factura factura = propuesta.getFactura();
        return new PropuestaFacturacionRespuestaDto(
            propuesta.getId(),
            propuesta.getCliente().getId(),
            propuesta.getCliente().getRazonSocial(),
            proyecto == null ? null : proyecto.getId(),
            proyecto == null ? null : proyecto.getNombre(),
            propuesta.getOrigen(),
            propuesta.getPeriodoAnio(),
            propuesta.getPeriodoMes(),
            propuesta.getFechaFacturacion(),
            propuesta.getDescripcion(),
            propuesta.getMonedaOrigen(),
            propuesta.getPrecioBaseNeto(),
            propuesta.getAcuerdoTipo(),
            propuesta.getAcuerdoValor(),
            propuesta.getAcuerdoMoneda(),
            propuesta.getValorUf(),
            propuesta.getFechaValorUf(),
            propuesta.getNetoClp(),
            propuesta.getTasaIva(),
            propuesta.getIvaClp(),
            propuesta.getTotalClp(),
            propuesta.getEstado(),
            factura == null ? null : factura.getNumeroFactura(),
            factura == null ? null : factura.getFechaFactura());
    }
}
