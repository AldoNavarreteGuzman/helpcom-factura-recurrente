package cl.helpcom.facturacion.facturacion.servicio;

import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.comun.util.OrdenPorDefecto;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import cl.helpcom.facturacion.facturacion.dominio.Archivo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.FacturaSolicitudDto;
import cl.helpcom.facturacion.facturacion.dto.PropuestaResumenDto;
import cl.helpcom.facturacion.facturacion.repositorio.ArchivoRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaEspecificaciones;
import cl.helpcom.facturacion.facturacion.repositorio.FacturaRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asocia propuestas a facturas y administra el PDF de respaldo (arquitectura-tecnica.md
 * §9 y §13). Una factura puede agrupar varias propuestas del mismo cliente; el PDF es
 * opcional y puede subirse o reemplazarse después de creada la factura.
 */
@Service
public class ServicioFactura {

    private static final Logger log = LoggerFactory.getLogger(ServicioFactura.class);
    private static final String TIPO_CONTENIDO_PDF = "application/pdf";

    private final FacturaRepositorio facturaRepositorio;
    private final ArchivoRepositorio archivoRepositorio;
    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;
    private final EmpresaRepositorio empresaRepositorio;
    private final AlmacenArchivos almacenArchivos;
    private final PropiedadesAlmacenamiento propiedadesAlmacenamiento;
    private final ContextoEmpresa contextoEmpresa;

    public ServicioFactura(
        FacturaRepositorio facturaRepositorio,
        ArchivoRepositorio archivoRepositorio,
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio,
        EmpresaRepositorio empresaRepositorio,
        AlmacenArchivos almacenArchivos,
        PropiedadesAlmacenamiento propiedadesAlmacenamiento,
        ContextoEmpresa contextoEmpresa) {
        this.facturaRepositorio = facturaRepositorio;
        this.archivoRepositorio = archivoRepositorio;
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.empresaRepositorio = empresaRepositorio;
        this.almacenArchivos = almacenArchivos;
        this.propiedadesAlmacenamiento = propiedadesAlmacenamiento;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<FacturaRespuestaDto> listar(
        String numero, LocalDate fechaDesde, LocalDate fechaHasta, Long clienteId, Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Specification<Factura> especificacion = FacturaEspecificaciones.combinar(
            FacturaEspecificaciones.paraEmpresa(empresaId),
            FacturaEspecificaciones.conNumero(numero),
            FacturaEspecificaciones.conFechaDesde(fechaDesde),
            FacturaEspecificaciones.conFechaHasta(fechaHasta),
            FacturaEspecificaciones.conCliente(clienteId));

        Pageable paginaOrdenada = OrdenPorDefecto.aplicar(pageable, FacturaEspecificaciones.ORDEN_POR_DEFECTO);
        return facturaRepositorio.findAll(especificacion, paginaOrdenada).map(this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public FacturaRespuestaDto obtener(Long id) {
        return aRespuesta(buscarFacturaOLanzar(id));
    }

    @Transactional
    public FacturaRespuestaDto crear(FacturaSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        validarNumeroNoDuplicado(empresaId, solicitud.numeroFactura());

        List<PropuestaFacturacion> propuestas = solicitud.propuestaIds().stream()
            .map(id -> buscarPropuestaFacturableOLanzar(empresaId, id))
            .toList();
        validarMismoCliente(propuestas);

        Factura factura = new Factura();
        factura.setEmpresa(empresaRepositorio.getReferenceById(empresaId));
        factura.setNumeroFactura(solicitud.numeroFactura());
        factura.setFechaFactura(solicitud.fechaFactura());
        factura.setObservacion(solicitud.observacion());

        try {
            facturaRepositorio.save(factura);
            facturaRepositorio.flush();
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad ante condiciones de carrera (uq_factura_numero), igual que
            // en acuerdo_precio: el pre-chequeo de arriba cubre casi todos los casos, esto
            // cubre el que se cuela entre el existsBy... y el insert.
            throw new ReglaNegocioException(
                "Ya existe una factura con el número " + solicitud.numeroFactura() + ".", "FACTURA_DUPLICADA");
        }

        for (PropuestaFacturacion propuesta : propuestas) {
            propuesta.setFactura(factura);
            propuesta.setEstado(EstadoPropuesta.FACTURADA);
        }
        factura.getPropuestas().addAll(propuestas);

        return aRespuesta(factura);
    }

    /**
     * Sube el PDF de respaldo. Si la factura ya tenía uno, lo REEMPLAZA: se elimina el
     * objeto anterior del almacén y su fila {@code archivo} (para no acumular basura
     * huérfana en Object Storage); si esa eliminación falla, se registra un WARN pero no se
     * aborta la operación — el PDF nuevo ya quedó enlazado correctamente, que es lo que le
     * importa al usuario en ese momento.
     */
    @Transactional
    public FacturaRespuestaDto subirPdf(Long facturaId, String nombreOriginal, String tipoContenido, byte[] contenido) {
        validarPdf(tipoContenido, contenido.length);
        Factura factura = buscarFacturaOLanzar(facturaId);
        Archivo anterior = factura.getArchivo();

        ReferenciaArchivo referencia = almacenArchivos.guardar(nombreOriginal, tipoContenido, contenido);

        Archivo archivo = new Archivo();
        archivo.setEmpresa(factura.getEmpresa());
        archivo.setNombreOriginal(referencia.nombreOriginal());
        archivo.setClaveObjeto(referencia.claveObjeto());
        archivo.setTipoContenido(referencia.tipoContenido());
        archivo.setTamanoBytes(referencia.tamanoBytes());
        archivoRepositorio.save(archivo);

        factura.setArchivo(archivo);

        if (anterior != null) {
            try {
                almacenArchivos.eliminar(anterior.getClaveObjeto());
            } catch (RuntimeException ex) {
                log.warn("No se pudo eliminar el PDF anterior (clave {}) de la factura {}: {}",
                    anterior.getClaveObjeto(), facturaId, ex.getMessage());
            }
            archivoRepositorio.delete(anterior);
        }

        return aRespuesta(factura);
    }

    @Transactional(readOnly = true)
    public DescargaArchivo descargarPdf(Long facturaId) {
        Factura factura = buscarFacturaOLanzar(facturaId);
        Archivo archivo = factura.getArchivo();
        if (archivo == null) {
            throw new RecursoNoEncontradoException(
                "La factura " + facturaId + " no tiene un PDF asociado.", "FACTURA_SIN_PDF");
        }
        return new DescargaArchivo(archivo.getNombreOriginal(), archivo.getTipoContenido(),
            almacenArchivos.obtener(archivo.getClaveObjeto()));
    }

    private void validarPdf(String tipoContenido, int tamanoBytes) {
        if (!TIPO_CONTENIDO_PDF.equalsIgnoreCase(tipoContenido)) {
            throw new SolicitudInvalidaException(
                "Solo se aceptan archivos PDF (application/pdf); se recibió: " + tipoContenido,
                "ARCHIVO_TIPO_INVALIDO");
        }
        long maximoBytes = propiedadesAlmacenamiento.tamanoMaximo().toBytes();
        if (tamanoBytes > maximoBytes) {
            throw new SolicitudInvalidaException(
                "El archivo (" + tamanoBytes + " bytes) supera el tamaño máximo permitido ("
                    + propiedadesAlmacenamiento.tamanoMaximo() + ").",
                "ARCHIVO_DEMASIADO_GRANDE");
        }
    }

    private void validarNumeroNoDuplicado(Long empresaId, String numeroFactura) {
        if (facturaRepositorio.existsByEmpresaIdAndNumeroFactura(empresaId, numeroFactura)) {
            throw new ReglaNegocioException(
                "Ya existe una factura con el número " + numeroFactura + ".", "FACTURA_DUPLICADA");
        }
    }

    private PropuestaFacturacion buscarPropuestaFacturableOLanzar(Long empresaId, Long propuestaId) {
        PropuestaFacturacion propuesta = propuestaFacturacionRepositorio.findByIdAndEmpresaId(propuestaId, empresaId)
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe una propuesta con id " + propuestaId, "PROPUESTA_NO_ENCONTRADA"));
        if (propuesta.getEstado() != EstadoPropuesta.PENDIENTE) {
            throw new ReglaNegocioException(
                "La propuesta " + propuestaId + " no se puede facturar: está en estado "
                    + propuesta.getEstado() + ".",
                "PROPUESTA_NO_FACTURABLE");
        }
        return propuesta;
    }

    /**
     * No está en la lista de validaciones original, pero se agrega porque agrupar
     * propuestas de clientes distintos en una sola factura produciría un documento
     * incorrecto (una factura le pertenece a un único cliente).
     */
    private void validarMismoCliente(List<PropuestaFacturacion> propuestas) {
        long clientesDistintos = propuestas.stream()
            .map(p -> p.getCliente().getId())
            .distinct()
            .count();
        if (clientesDistintos > 1) {
            throw new ReglaNegocioException(
                "Las propuestas seleccionadas pertenecen a clientes distintos; una factura no puede "
                    + "agrupar propuestas de más de un cliente.",
                "PROPUESTAS_DE_CLIENTES_DISTINTOS");
        }
    }

    private Factura buscarFacturaOLanzar(Long facturaId) {
        return facturaRepositorio.findByIdAndEmpresaId(facturaId, contextoEmpresa.obtenerEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe una factura con id " + facturaId, "FACTURA_NO_ENCONTRADA"));
    }

    private FacturaRespuestaDto aRespuesta(Factura factura) {
        List<PropuestaResumenDto> resumenes = factura.getPropuestas().stream()
            .map(this::aResumen)
            .toList();
        PropuestaFacturacion primera = factura.getPropuestas().isEmpty() ? null : factura.getPropuestas().get(0);
        Archivo archivo = factura.getArchivo();

        return new FacturaRespuestaDto(
            factura.getId(),
            factura.getNumeroFactura(),
            factura.getFechaFactura(),
            factura.getObservacion(),
            primera == null ? null : primera.getCliente().getId(),
            primera == null ? null : primera.getCliente().getRazonSocial(),
            archivo != null,
            archivo == null ? null : archivo.getNombreOriginal(),
            resumenes);
    }

    private PropuestaResumenDto aResumen(PropuestaFacturacion propuesta) {
        return new PropuestaResumenDto(
            propuesta.getId(),
            propuesta.getPeriodoAnio(),
            propuesta.getPeriodoMes(),
            propuesta.getFechaFacturacion(),
            propuesta.getDescripcion(),
            propuesta.getNetoClp(),
            propuesta.getIvaClp(),
            propuesta.getTotalClp(),
            propuesta.getEstado());
    }
}
