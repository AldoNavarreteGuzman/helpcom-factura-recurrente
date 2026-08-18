package cl.helpcom.facturacion.proyectos.servicio;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.dominio.TipoServicio;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.clientes.repositorio.TipoServicioRepositorio;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoRespuestaDto;
import cl.helpcom.facturacion.proyectos.dto.ProyectoSolicitudDto;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoEspecificaciones;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProyectoServicio {

    private final ProyectoRepositorio proyectoRepositorio;
    private final ClienteRepositorio clienteRepositorio;
    private final TipoServicioRepositorio tipoServicioRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public ProyectoServicio(
        ProyectoRepositorio proyectoRepositorio,
        ClienteRepositorio clienteRepositorio,
        TipoServicioRepositorio tipoServicioRepositorio,
        ContextoEmpresa contextoEmpresa) {
        this.proyectoRepositorio = proyectoRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.tipoServicioRepositorio = tipoServicioRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<ProyectoRespuestaDto> listar(
        Long clienteId, Periodicidad periodicidad, Moneda moneda, Boolean activo, Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Specification<Proyecto> especificacion = ProyectoEspecificaciones.combinar(
            ProyectoEspecificaciones.paraEmpresa(empresaId),
            ProyectoEspecificaciones.conCliente(clienteId),
            ProyectoEspecificaciones.conPeriodicidad(periodicidad),
            ProyectoEspecificaciones.conMoneda(moneda),
            ProyectoEspecificaciones.conActivo(activo));

        return proyectoRepositorio.findAll(especificacion, pageable).map(this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public ProyectoRespuestaDto obtener(Long id) {
        return aRespuesta(buscarOLanzar(id));
    }

    @Transactional
    public ProyectoRespuestaDto crear(ProyectoSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        validarVigencia(solicitud.fechaInicio(), solicitud.fechaTermino());
        Cliente cliente = buscarClienteOLanzar(empresaId, solicitud.clienteId());
        TipoServicio tipoServicio = buscarTipoServicioSiVieneOLanzar(empresaId, solicitud.tipoServicioId());

        Proyecto proyecto = new Proyecto();
        proyecto.setEmpresa(cliente.getEmpresa());
        aplicarCampos(proyecto, solicitud, cliente, tipoServicio);

        return aRespuesta(proyectoRepositorio.save(proyecto));
    }

    @Transactional
    public ProyectoRespuestaDto actualizar(Long id, ProyectoSolicitudDto solicitud) {
        Proyecto proyecto = buscarOLanzar(id);
        Long empresaId = proyecto.getEmpresa().getId();
        validarVigencia(solicitud.fechaInicio(), solicitud.fechaTermino());
        Cliente cliente = buscarClienteOLanzar(empresaId, solicitud.clienteId());
        TipoServicio tipoServicio = buscarTipoServicioSiVieneOLanzar(empresaId, solicitud.tipoServicioId());

        aplicarCampos(proyecto, solicitud, cliente, tipoServicio);

        return aRespuesta(proyecto);
    }

    @Transactional
    public void eliminar(Long id) {
        Proyecto proyecto = buscarOLanzar(id);
        try {
            proyectoRepositorio.delete(proyecto);
            proyectoRepositorio.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ReglaNegocioException(
                "No se puede eliminar el proyecto porque tiene acuerdos de precio u otras referencias asociadas.",
                "PROYECTO_CON_REFERENCIAS");
        }
    }

    private void validarVigencia(LocalDate fechaInicio, LocalDate fechaTermino) {
        if (fechaTermino != null && fechaTermino.isBefore(fechaInicio)) {
            throw new SolicitudInvalidaException(
                "La fecha de término debe ser igual o posterior a la fecha de inicio.", "FECHA_TERMINO_INVALIDA");
        }
    }

    private Cliente buscarClienteOLanzar(Long empresaId, Long clienteId) {
        return clienteRepositorio.findByIdAndEmpresaId(clienteId, empresaId)
            .orElseThrow(() -> new SolicitudInvalidaException(
                "No existe un cliente con id " + clienteId + ".", "CLIENTE_NO_EXISTE"));
    }

    private TipoServicio buscarTipoServicioSiVieneOLanzar(Long empresaId, Long tipoServicioId) {
        if (tipoServicioId == null) {
            return null;
        }
        return tipoServicioRepositorio.findByIdAndEmpresaId(tipoServicioId, empresaId)
            .orElseThrow(() -> new SolicitudInvalidaException(
                "No existe un tipo de servicio con id " + tipoServicioId + ".", "TIPO_SERVICIO_NO_EXISTE"));
    }

    private void aplicarCampos(Proyecto proyecto, ProyectoSolicitudDto solicitud, Cliente cliente, TipoServicio tipoServicio) {
        proyecto.setCliente(cliente);
        proyecto.setTipoServicio(tipoServicio);
        proyecto.setCodigo(solicitud.codigo());
        proyecto.setNombre(solicitud.nombre());
        proyecto.setDescripcion(solicitud.descripcion());
        proyecto.setPrecioBaseNeto(solicitud.precioBaseNeto());
        proyecto.setMonedaPrecio(solicitud.monedaPrecio());
        proyecto.setPeriodicidad(solicitud.periodicidad());
        proyecto.setDiaFacturacion(solicitud.diaFacturacion().shortValue());
        proyecto.setFechaInicio(solicitud.fechaInicio());
        proyecto.setFechaTermino(solicitud.fechaTermino());
        proyecto.setActivo(solicitud.activo());
    }

    private Proyecto buscarOLanzar(Long id) {
        return proyectoRepositorio.findByIdAndEmpresaId(id, contextoEmpresa.obtenerEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe un proyecto con id " + id, "PROYECTO_NO_ENCONTRADO"));
    }

    private ProyectoRespuestaDto aRespuesta(Proyecto proyecto) {
        TipoServicio tipoServicio = proyecto.getTipoServicio();
        return new ProyectoRespuestaDto(
            proyecto.getId(),
            proyecto.getCliente().getId(),
            proyecto.getCliente().getRazonSocial(),
            tipoServicio == null ? null : tipoServicio.getId(),
            tipoServicio == null ? null : tipoServicio.getNombre(),
            proyecto.getCodigo(),
            proyecto.getNombre(),
            proyecto.getDescripcion(),
            proyecto.getPrecioBaseNeto(),
            proyecto.getMonedaPrecio(),
            proyecto.getPeriodicidad(),
            proyecto.getDiaFacturacion(),
            proyecto.getFechaInicio(),
            proyecto.getFechaTermino(),
            proyecto.isActivo());
    }
}
