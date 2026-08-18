package cl.helpcom.facturacion.proyectos.servicio;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.AcuerdoPrecioSolicitudDto;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcuerdoPrecioServicio {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;
    private final ProyectoRepositorio proyectoRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public AcuerdoPrecioServicio(
        AcuerdoPrecioRepositorio acuerdoPrecioRepositorio,
        ProyectoRepositorio proyectoRepositorio,
        ContextoEmpresa contextoEmpresa) {
        this.acuerdoPrecioRepositorio = acuerdoPrecioRepositorio;
        this.proyectoRepositorio = proyectoRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public List<AcuerdoPrecioRespuestaDto> listar(Long proyectoId) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        buscarProyectoOLanzar(proyectoId, empresaId);
        return acuerdoPrecioRepositorio.findByProyectoIdAndEmpresaId(proyectoId, empresaId).stream()
            .map(this::aRespuesta)
            .toList();
    }

    @Transactional(readOnly = true)
    public AcuerdoPrecioRespuestaDto obtener(Long proyectoId, Long id) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        buscarProyectoOLanzar(proyectoId, empresaId);
        return aRespuesta(buscarAcuerdoOLanzar(proyectoId, id, empresaId));
    }

    @Transactional
    public AcuerdoPrecioRespuestaDto crear(Long proyectoId, AcuerdoPrecioSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Proyecto proyecto = buscarProyectoOLanzar(proyectoId, empresaId);

        LocalDate fechaTermino = validarYResolverFechaTermino(solicitud);
        validarMoneda(solicitud.tipo(), solicitud.moneda());
        validarPorcentaje(solicitud.tipo(), solicitud.valor());
        validarSinTraslape(proyectoId, solicitud.fechaInicio(), fechaTermino, null);

        AcuerdoPrecio acuerdo = new AcuerdoPrecio();
        acuerdo.setEmpresa(proyecto.getEmpresa());
        acuerdo.setProyecto(proyecto);
        aplicarCampos(acuerdo, solicitud, fechaTermino);

        return aRespuesta(guardarOTraducirTraslape(acuerdo));
    }

    @Transactional
    public AcuerdoPrecioRespuestaDto actualizar(Long proyectoId, Long id, AcuerdoPrecioSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        buscarProyectoOLanzar(proyectoId, empresaId);
        AcuerdoPrecio acuerdo = buscarAcuerdoOLanzar(proyectoId, id, empresaId);

        LocalDate fechaTermino = validarYResolverFechaTermino(solicitud);
        validarMoneda(solicitud.tipo(), solicitud.moneda());
        validarPorcentaje(solicitud.tipo(), solicitud.valor());
        validarSinTraslape(proyectoId, solicitud.fechaInicio(), fechaTermino, id);

        aplicarCampos(acuerdo, solicitud, fechaTermino);

        return aRespuesta(guardarOTraducirTraslape(acuerdo));
    }

    @Transactional
    public void eliminar(Long proyectoId, Long id) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        buscarProyectoOLanzar(proyectoId, empresaId);
        AcuerdoPrecio acuerdo = buscarAcuerdoOLanzar(proyectoId, id, empresaId);
        acuerdoPrecioRepositorio.delete(acuerdo);
    }

    /**
     * Debe venir exactamente una de las dos formas de vigencia. Si viene {@code mesesPactados},
     * la fecha de término se calcula como el último día del mes N contado desde el inicio
     * (una vigencia de N meses termina justo un día antes de que se cumpla el mes N+1, para
     * no dejar un día de traslape con el siguiente acuerdo).
     */
    private LocalDate validarYResolverFechaTermino(AcuerdoPrecioSolicitudDto solicitud) {
        boolean tieneFechaTermino = solicitud.fechaTermino() != null;
        boolean tieneMeses = solicitud.mesesPactados() != null;
        if (tieneFechaTermino == tieneMeses) {
            throw new SolicitudInvalidaException(
                "Debe indicar exactamente una forma de vigencia: fecha_termino o meses_pactados.",
                "ACUERDO_VIGENCIA_INVALIDA");
        }

        LocalDate fechaTermino = tieneMeses
            ? solicitud.fechaInicio().plusMonths(solicitud.mesesPactados()).minusDays(1)
            : solicitud.fechaTermino();

        if (fechaTermino.isBefore(solicitud.fechaInicio())) {
            throw new SolicitudInvalidaException(
                "La fecha de término debe ser igual o posterior a la fecha de inicio.", "ACUERDO_VIGENCIA_INVALIDA");
        }
        return fechaTermino;
    }

    private void validarMoneda(TipoAcuerdo tipo, Moneda moneda) {
        if (tipo == TipoAcuerdo.DESCUENTO_PORCENTAJE) {
            if (moneda != null) {
                throw new SolicitudInvalidaException(
                    "La moneda debe omitirse para un descuento porcentual.", "ACUERDO_MONEDA_INVALIDA");
            }
        } else if (moneda == null) {
            throw new SolicitudInvalidaException(
                "La moneda es obligatoria (UF o CLP) para este tipo de acuerdo.", "ACUERDO_MONEDA_INVALIDA");
        }
    }

    private void validarPorcentaje(TipoAcuerdo tipo, BigDecimal valor) {
        if (tipo == TipoAcuerdo.DESCUENTO_PORCENTAJE
            && (valor.compareTo(BigDecimal.ZERO) <= 0 || valor.compareTo(CIEN) > 0)) {
            throw new SolicitudInvalidaException(
                "El descuento porcentual debe ser mayor que 0 y menor o igual a 100.", "ACUERDO_PORCENTAJE_INVALIDO");
        }
    }

    private void validarSinTraslape(Long proyectoId, LocalDate fechaInicio, LocalDate fechaTermino, Long idExcluido) {
        List<AcuerdoPrecio> traslapados =
            acuerdoPrecioRepositorio.buscarTraslapados(proyectoId, fechaInicio, fechaTermino, idExcluido);
        if (!traslapados.isEmpty()) {
            lanzarTraslape(traslapados.get(0));
        }
    }

    private AcuerdoPrecio guardarOTraducirTraslape(AcuerdoPrecio acuerdo) {
        try {
            AcuerdoPrecio guardado = acuerdoPrecioRepositorio.save(acuerdo);
            acuerdoPrecioRepositorio.flush();
            return guardado;
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad ante condiciones de carrera: la validación de servicio ya
            // cubre la mayoría de los casos, pero la exclusion constraint de base
            // (ex_acuerdo_no_traslape) es la última garantía ante escrituras concurrentes.
            throw new ReglaNegocioException(
                "La vigencia se traslapa con otro acuerdo del mismo proyecto.", "ACUERDO_TRASLAPADO");
        }
    }

    private void lanzarTraslape(AcuerdoPrecio conflicto) {
        throw new ReglaNegocioException(
            "La vigencia se traslapa con el acuerdo " + conflicto.getId()
                + " (" + conflicto.getFechaInicio() + " a " + conflicto.getFechaTermino() + ").",
            "ACUERDO_TRASLAPADO");
    }

    private void aplicarCampos(AcuerdoPrecio acuerdo, AcuerdoPrecioSolicitudDto solicitud, LocalDate fechaTermino) {
        acuerdo.setTipo(solicitud.tipo());
        acuerdo.setValor(solicitud.valor());
        acuerdo.setMoneda(solicitud.moneda());
        acuerdo.setFechaInicio(solicitud.fechaInicio());
        acuerdo.setFechaTermino(fechaTermino);
        acuerdo.setMesesPactados(solicitud.mesesPactados() == null ? null : solicitud.mesesPactados().shortValue());
        acuerdo.setObservacion(solicitud.observacion());
    }

    private Proyecto buscarProyectoOLanzar(Long proyectoId, Long empresaId) {
        return proyectoRepositorio.findByIdAndEmpresaId(proyectoId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe un proyecto con id " + proyectoId, "PROYECTO_NO_ENCONTRADO"));
    }

    private AcuerdoPrecio buscarAcuerdoOLanzar(Long proyectoId, Long id, Long empresaId) {
        return acuerdoPrecioRepositorio.findByIdAndProyectoIdAndEmpresaId(id, proyectoId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe un acuerdo de precio con id " + id + " para el proyecto " + proyectoId,
                "ACUERDO_NO_ENCONTRADO"));
    }

    private AcuerdoPrecioRespuestaDto aRespuesta(AcuerdoPrecio acuerdo) {
        return new AcuerdoPrecioRespuestaDto(
            acuerdo.getId(),
            acuerdo.getProyecto().getId(),
            acuerdo.getTipo(),
            acuerdo.getValor(),
            acuerdo.getMoneda(),
            acuerdo.getFechaInicio(),
            acuerdo.getFechaTermino(),
            acuerdo.getMesesPactados() == null ? null : acuerdo.getMesesPactados().intValue(),
            acuerdo.getObservacion());
    }
}
