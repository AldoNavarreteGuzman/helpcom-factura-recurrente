package cl.helpcom.facturacion.informes.servicio;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.comun.util.OrdenPorDefecto;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionEspecificaciones;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.informes.dto.FiltroInformeFacturacion;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionClienteResumenDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionDetalleDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionResumenDto;
import cl.helpcom.facturacion.informes.repositorio.FilaResumenPorCliente;
import cl.helpcom.facturacion.informes.repositorio.FilaResumenPorEstado;
import cl.helpcom.facturacion.informes.repositorio.RepositorioResumenFacturacion;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informe de "lo que se va a facturar" (arquitectura-tecnica.md, módulo informes): solo
 * lectura y agregación sobre {@code propuesta_facturacion}, no crea ni modifica datos.
 *
 * <p>Ver la política de totales (qué estados se excluyen y por qué) en el javadoc de
 * {@link InformeFacturacionResumenDto}.
 */
@Service
public class ServicioInformeFacturacion {

    private static final String[] ENCABEZADOS_CSV = {
        "id", "cliente", "proyecto", "descripcion", "periodo", "fecha_facturacion", "moneda_origen",
        "valor_uf", "neto_clp", "iva_clp", "total_clp", "estado", "origen", "numero_factura"
    };
    private static final CSVFormat FORMATO_CSV = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setDelimiter(';')
        .setHeader(ENCABEZADOS_CSV)
        .build();

    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;
    private final RepositorioResumenFacturacion repositorioResumenFacturacion;
    private final ContextoEmpresa contextoEmpresa;

    public ServicioInformeFacturacion(
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio,
        RepositorioResumenFacturacion repositorioResumenFacturacion,
        ContextoEmpresa contextoEmpresa) {
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.repositorioResumenFacturacion = repositorioResumenFacturacion;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public InformeFacturacionRespuestaDto generar(FiltroInformeFacturacion filtro, Pageable pageable) {
        Specification<PropuestaFacturacion> especificacion = construirEspecificacion(filtro);
        Specification<PropuestaFacturacion> especificacionConFactura = PropuestaFacturacionEspecificaciones.combinar(
            especificacion, PropuestaFacturacionEspecificaciones.conFacturaFetch());

        Pageable paginaOrdenada =
            OrdenPorDefecto.aplicar(pageable, PropuestaFacturacionEspecificaciones.ORDEN_POR_DEFECTO);
        Page<PropuestaFacturacion> pagina =
            propuestaFacturacionRepositorio.findAll(especificacionConFactura, paginaOrdenada);
        PaginaRespuestaDto<InformeFacturacionDetalleDto> detalle = PaginaRespuestaDto.desde(pagina, this::aDetalleDto);

        return new InformeFacturacionRespuestaDto(construirResumen(especificacion), detalle);
    }

    @Transactional(readOnly = true)
    public byte[] exportarDetalleCsv(FiltroInformeFacturacion filtro) {
        Specification<PropuestaFacturacion> especificacion = PropuestaFacturacionEspecificaciones.combinar(
            construirEspecificacion(filtro), PropuestaFacturacionEspecificaciones.conFacturaFetch());
        List<PropuestaFacturacion> propuestas = propuestaFacturacionRepositorio.findAll(
            especificacion, Sort.by("periodoAnio", "periodoMes"));

        StringWriter contenido = new StringWriter();
        try (CSVPrinter impresor = new CSVPrinter(contenido, FORMATO_CSV)) {
            for (PropuestaFacturacion propuesta : propuestas) {
                impresor.printRecord(filaCsv(propuesta));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el CSV del informe de facturación.", ex);
        }
        return contenido.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<PropuestaFacturacion> construirEspecificacion(FiltroInformeFacturacion filtro) {
        validarRangoPeriodo(filtro);
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        return PropuestaFacturacionEspecificaciones.combinar(
            PropuestaFacturacionEspecificaciones.paraEmpresa(empresaId),
            PropuestaFacturacionEspecificaciones.conPeriodo(filtro.periodoAnio(), filtro.periodoMes()),
            PropuestaFacturacionEspecificaciones.conRangoPeriodo(filtro.anioMesDesde(), filtro.anioMesHasta()),
            PropuestaFacturacionEspecificaciones.conCliente(filtro.clienteId()),
            PropuestaFacturacionEspecificaciones.conEstados(filtro.estados()),
            PropuestaFacturacionEspecificaciones.conOrigen(filtro.origen()),
            PropuestaFacturacionEspecificaciones.conFacturada(filtro.facturada()));
    }

    private void validarRangoPeriodo(FiltroInformeFacturacion filtro) {
        validarFormatoAnioMes(filtro.anioMesDesde());
        validarFormatoAnioMes(filtro.anioMesHasta());
        if (filtro.anioMesDesde() != null && filtro.anioMesHasta() != null
                && filtro.anioMesDesde() > filtro.anioMesHasta()) {
            throw new SolicitudInvalidaException(
                "anioMesDesde (" + filtro.anioMesDesde() + ") no puede ser posterior a anioMesHasta ("
                    + filtro.anioMesHasta() + ").",
                "RANGO_PERIODO_INVALIDO");
        }
    }

    private void validarFormatoAnioMes(Integer anioMes) {
        if (anioMes == null) {
            return;
        }
        int mes = anioMes % 100;
        if (anioMes < 100001 || mes < 1 || mes > 12) {
            throw new SolicitudInvalidaException(
                "'" + anioMes + "' no es un AAAAMM válido (p. ej. 202601 para enero de 2026).",
                "ANIO_MES_INVALIDO");
        }
    }

    /**
     * Resumen calculado con dos consultas de agregación (SUM/COUNT + GROUP BY en SQL, ver
     * {@link RepositorioResumenFacturacion}): una por estado (para el desglose completo de
     * cantidades y los totales — sumando en Java solo los ≤4 subtotales ya agregados por la
     * base de datos, nunca las propuestas individuales) y otra por cliente, ya filtrada a
     * los estados facturables.
     */
    private InformeFacturacionResumenDto construirResumen(Specification<PropuestaFacturacion> especificacion) {
        List<FilaResumenPorEstado> filasPorEstado = repositorioResumenFacturacion.agregarPorEstado(especificacion);

        Map<EstadoPropuesta, Long> cantidadPorEstado = new EnumMap<>(EstadoPropuesta.class);
        for (EstadoPropuesta estado : EstadoPropuesta.values()) {
            cantidadPorEstado.put(estado, 0L);
        }
        long cantidadTotal = 0;
        BigDecimal netoClp = BigDecimal.ZERO;
        BigDecimal ivaClp = BigDecimal.ZERO;
        BigDecimal totalClp = BigDecimal.ZERO;
        for (FilaResumenPorEstado fila : filasPorEstado) {
            cantidadPorEstado.put(fila.estado(), fila.cantidad());
            cantidadTotal += fila.cantidad();
            if (esFacturable(fila.estado())) {
                netoClp = netoClp.add(fila.netoClp());
                ivaClp = ivaClp.add(fila.ivaClp());
                totalClp = totalClp.add(fila.totalClp());
            }
        }

        Specification<PropuestaFacturacion> especificacionFacturable = PropuestaFacturacionEspecificaciones.combinar(
            especificacion, PropuestaFacturacionEspecificaciones.facturable());
        List<InformeFacturacionClienteResumenDto> porCliente = repositorioResumenFacturacion
            .agregarPorCliente(especificacionFacturable).stream()
            .map(this::aClienteResumenDto)
            .toList();

        return new InformeFacturacionResumenDto(
            cantidadTotal, cantidadPorEstado, cantidadPorEstado.get(EstadoPropuesta.PENDIENTE_UF),
            netoClp, ivaClp, totalClp, porCliente);
    }

    private boolean esFacturable(EstadoPropuesta estado) {
        return estado == EstadoPropuesta.PENDIENTE || estado == EstadoPropuesta.FACTURADA;
    }

    private InformeFacturacionClienteResumenDto aClienteResumenDto(FilaResumenPorCliente fila) {
        return new InformeFacturacionClienteResumenDto(
            fila.clienteId(), fila.clienteRazonSocial(), fila.cantidad(), fila.netoClp(), fila.ivaClp(), fila.totalClp());
    }

    private InformeFacturacionDetalleDto aDetalleDto(PropuestaFacturacion propuesta) {
        Proyecto proyecto = propuesta.getProyecto();
        Factura factura = propuesta.getFactura();
        return new InformeFacturacionDetalleDto(
            propuesta.getId(),
            propuesta.getCliente().getId(),
            propuesta.getCliente().getRazonSocial(),
            proyecto == null ? null : proyecto.getId(),
            proyecto == null ? null : proyecto.getNombre(),
            propuesta.getDescripcion(),
            propuesta.getPeriodoAnio(),
            propuesta.getPeriodoMes(),
            propuesta.getFechaFacturacion(),
            propuesta.getMonedaOrigen(),
            propuesta.getValorUf(),
            propuesta.getNetoClp(),
            propuesta.getIvaClp(),
            propuesta.getTotalClp(),
            propuesta.getEstado(),
            propuesta.getOrigen(),
            factura == null ? null : factura.getId(),
            factura == null ? null : factura.getNumeroFactura(),
            factura == null ? null : factura.getFechaFactura());
    }

    private List<?> filaCsv(PropuestaFacturacion propuesta) {
        Proyecto proyecto = propuesta.getProyecto();
        Factura factura = propuesta.getFactura();
        return List.of(
            propuesta.getId(),
            propuesta.getCliente().getRazonSocial(),
            proyecto == null ? "" : proyecto.getNombre(),
            propuesta.getDescripcion(),
            propuesta.getPeriodoAnio() + "-" + "%02d".formatted(propuesta.getPeriodoMes()),
            propuesta.getFechaFacturacion(),
            propuesta.getMonedaOrigen(),
            propuesta.getValorUf() == null ? "" : propuesta.getValorUf(),
            propuesta.getNetoClp(),
            propuesta.getIvaClp(),
            propuesta.getTotalClp(),
            propuesta.getEstado(),
            propuesta.getOrigen(),
            factura == null ? "" : factura.getNumeroFactura());
    }
}
