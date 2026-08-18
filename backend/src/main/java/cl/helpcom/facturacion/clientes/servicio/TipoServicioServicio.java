package cl.helpcom.facturacion.clientes.servicio;

import cl.helpcom.facturacion.clientes.dominio.TipoServicio;
import cl.helpcom.facturacion.clientes.dto.TipoServicioRespuestaDto;
import cl.helpcom.facturacion.clientes.dto.TipoServicioSolicitudDto;
import cl.helpcom.facturacion.clientes.repositorio.TipoServicioRepositorio;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TipoServicioServicio {

    private final TipoServicioRepositorio tipoServicioRepositorio;
    private final ProyectoRepositorio proyectoRepositorio;
    private final EmpresaRepositorio empresaRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public TipoServicioServicio(
        TipoServicioRepositorio tipoServicioRepositorio,
        ProyectoRepositorio proyectoRepositorio,
        EmpresaRepositorio empresaRepositorio,
        ContextoEmpresa contextoEmpresa) {
        this.tipoServicioRepositorio = tipoServicioRepositorio;
        this.proyectoRepositorio = proyectoRepositorio;
        this.empresaRepositorio = empresaRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<TipoServicioRespuestaDto> listar(Boolean activo, Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Page<TipoServicio> pagina = activo == null
            ? tipoServicioRepositorio.findByEmpresaId(empresaId, pageable)
            : tipoServicioRepositorio.findByEmpresaIdAndActivo(empresaId, activo, pageable);
        return pagina.map(this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public TipoServicioRespuestaDto obtener(Long id) {
        return aRespuesta(buscarOLanzar(id));
    }

    @Transactional
    public TipoServicioRespuestaDto crear(TipoServicioSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        validarNombreNoDuplicado(empresaId, solicitud.nombre(), null);

        TipoServicio tipoServicio = new TipoServicio();
        tipoServicio.setEmpresa(empresaRepositorio.getReferenceById(empresaId));
        tipoServicio.setNombre(solicitud.nombre());
        tipoServicio.setActivo(solicitud.activo());

        return aRespuesta(tipoServicioRepositorio.save(tipoServicio));
    }

    @Transactional
    public TipoServicioRespuestaDto actualizar(Long id, TipoServicioSolicitudDto solicitud) {
        TipoServicio tipoServicio = buscarOLanzar(id);
        validarNombreNoDuplicado(tipoServicio.getEmpresa().getId(), solicitud.nombre(), id);

        tipoServicio.setNombre(solicitud.nombre());
        tipoServicio.setActivo(solicitud.activo());

        return aRespuesta(tipoServicio);
    }

    /**
     * Si el tipo de servicio está referenciado por algún proyecto, se hace baja lógica
     * (activo = false) en vez de eliminarlo físicamente, para no romper la referencia
     * histórica del proyecto. Si no tiene proyectos asociados, se elimina físicamente.
     */
    @Transactional
    public void eliminar(Long id) {
        TipoServicio tipoServicio = buscarOLanzar(id);
        Long empresaId = tipoServicio.getEmpresa().getId();

        if (proyectoRepositorio.existsByTipoServicioIdAndEmpresaId(id, empresaId)) {
            tipoServicio.setActivo(false);
        } else {
            tipoServicioRepositorio.delete(tipoServicio);
        }
    }

    private void validarNombreNoDuplicado(Long empresaId, String nombre, Long idExcluido) {
        boolean duplicado = idExcluido == null
            ? tipoServicioRepositorio.existsByEmpresaIdAndNombreIgnoreCase(empresaId, nombre)
            : tipoServicioRepositorio.existsByEmpresaIdAndNombreIgnoreCaseAndIdNot(empresaId, nombre, idExcluido);
        if (duplicado) {
            throw new ReglaNegocioException(
                "Ya existe un tipo de servicio con el nombre '" + nombre + "'.", "TIPO_SERVICIO_DUPLICADO");
        }
    }

    private TipoServicio buscarOLanzar(Long id) {
        return tipoServicioRepositorio.findByIdAndEmpresaId(id, contextoEmpresa.obtenerEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe un tipo de servicio con id " + id, "TIPO_SERVICIO_NO_ENCONTRADO"));
    }

    private TipoServicioRespuestaDto aRespuesta(TipoServicio tipoServicio) {
        return new TipoServicioRespuestaDto(tipoServicio.getId(), tipoServicio.getNombre(), tipoServicio.isActivo());
    }
}
