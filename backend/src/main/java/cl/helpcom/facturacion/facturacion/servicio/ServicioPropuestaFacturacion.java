package cl.helpcom.facturacion.facturacion.servicio;

import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.util.OrdenPorDefecto;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.armado.EntradaArmadoPropuesta;
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
    private final ArmadorPropuesta armadorPropuesta;

    public ServicioPropuestaFacturacion(
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio, ContextoEmpresa contextoEmpresa,
        ArmadorPropuesta armadorPropuesta) {
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.contextoEmpresa = contextoEmpresa;
        this.armadorPropuesta = armadorPropuesta;
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

    /**
     * Reprocesa una propuesta {@code PENDIENTE_UF}: reintenta la obtención de UF (con el
     * reintento con backoff de {@code FuenteUfMindicador}, deuda-tecnica.md ítem 8) y, si ahora
     * está disponible, completa el snapshot que {@link ArmadorPropuesta} dejó a propósito
     * incompleto la primera vez — no se muta un snapshot ya calculado: {@code PENDIENTE_UF} es,
     * por diseño, el único estado que nunca llegó a tener uno real (deuda-tecnica.md ítem 8,
     * arquitectura-tecnica.md §9).
     *
     * <p><b>Guard duro — solo {@code PENDIENTE_UF} es reprocesable:</b> {@code PENDIENTE} ya
     * tiene un snapshot real (invariante verificada en {@code FlujoIdempotenciaCicloE2ETest} —
     * ni siquiera una re-ejecución del ciclo la toca); {@code FACTURADA} puede estar reflejada
     * en un documento real fuera del sistema (mismo criterio que {@link #anular}); {@code
     * ANULADA} es una decisión de negocio ya tomada. Mutar cualquiera de esas tres violaría la
     * regla de oro del snapshot inmutable.
     *
     * <p>Si la UF sigue sin estar disponible, la propuesta queda igual en {@code PENDIENTE_UF},
     * sin error — es seguro reintentar cuantas veces haga falta, sin efectos acumulativos.
     *
     * <p>El acuerdo de precio vigente se RE-RESUELVE contra la fecha de facturación al momento
     * del reproceso (no se reutiliza el {@code acuerdo_id} que ya tenía la fila) — si el acuerdo
     * cambió legítimamente entre el intento original y el reproceso, el snapshot final refleja
     * la realidad actual, no una desactualizada.
     */
    @Transactional
    public PropuestaFacturacionRespuestaDto reprocesarUf(Long propuestaId) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        PropuestaFacturacion propuesta = propuestaFacturacionRepositorio.findByIdAndEmpresaId(propuestaId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe una propuesta con id " + propuestaId, "PROPUESTA_NO_ENCONTRADA"));

        if (propuesta.getEstado() != EstadoPropuesta.PENDIENTE_UF) {
            throw new ReglaNegocioException(
                "La propuesta " + propuestaId + " está en estado " + propuesta.getEstado()
                    + " y no se puede reprocesar; solo aplica a propuestas PENDIENTE_UF.",
                "PROPUESTA_NO_REPROCESABLE");
        }

        EntradaArmadoPropuesta entrada = new EntradaArmadoPropuesta(
            propuesta.getEmpresa(), propuesta.getCliente(), propuesta.getProyecto(), propuesta.getOrigen(),
            propuesta.getPeriodoAnio(), propuesta.getPeriodoMes(), propuesta.getFechaFacturacion(),
            propuesta.getDescripcion(), propuesta.getPrecioBaseNeto(), propuesta.getMonedaOrigen());
        PropuestaFacturacion recalculada = armadorPropuesta.armar(entrada);

        propuesta.setAcuerdo(recalculada.getAcuerdo());
        propuesta.setAcuerdoTipo(recalculada.getAcuerdoTipo());
        propuesta.setAcuerdoValor(recalculada.getAcuerdoValor());
        propuesta.setAcuerdoMoneda(recalculada.getAcuerdoMoneda());
        propuesta.setTasaIva(recalculada.getTasaIva());
        propuesta.setValorUf(recalculada.getValorUf());
        propuesta.setFechaValorUf(recalculada.getFechaValorUf());
        propuesta.setNetoClp(recalculada.getNetoClp());
        propuesta.setIvaClp(recalculada.getIvaClp());
        propuesta.setTotalClp(recalculada.getTotalClp());
        propuesta.setEstado(recalculada.getEstado());

        // modificado_por/modificado_en se sellan automáticamente al hacer flush (Spring Data
        // JPA Auditing sobre EntidadAuditable, igual que cualquier otra mutación de dominio —
        // ver AuditoriaConfig): ninguna asignación manual acá, mismo mecanismo que anular().
        return aRespuesta(propuesta);
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
