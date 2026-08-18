package cl.helpcom.facturacion.informes.controlador;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.informes.dto.FiltroInformeFacturacion;
import cl.helpcom.facturacion.informes.dto.InformeFacturacionRespuestaDto;
import cl.helpcom.facturacion.informes.servicio.ServicioInformeFacturacion;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informe de facturación (informes/): un único endpoint sirve resumen y detalle juntos —se
 * prefirió a separarlos en {@code /resumen} y {@code /detalle} porque ambas partes comparten
 * exactamente los mismos filtros y siempre se consumen juntas en una misma pantalla; separar
 * solo habría duplicado la lista de parámetros sin ganar nada (no hay un caso de uso real
 * hoy que necesite pedir el resumen sin el detalle o viceversa).
 */
@RestController
public class InformeFacturacionControlador {

    private final ServicioInformeFacturacion servicioInformeFacturacion;

    public InformeFacturacionControlador(ServicioInformeFacturacion servicioInformeFacturacion) {
        this.servicioInformeFacturacion = servicioInformeFacturacion;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/informes/facturacion")
    public InformeFacturacionRespuestaDto generar(
        @RequestParam(required = false) Integer periodoAnio,
        @RequestParam(required = false) Integer periodoMes,
        @RequestParam(required = false) Integer anioMesDesde,
        @RequestParam(required = false) Integer anioMesHasta,
        @RequestParam(required = false) Long clienteId,
        @RequestParam(required = false) List<EstadoPropuesta> estados,
        @RequestParam(required = false) OrigenPropuesta origen,
        @RequestParam(required = false) Boolean facturada,
        Pageable pageable) {
        return servicioInformeFacturacion.generar(
            new FiltroInformeFacturacion(
                periodoAnio, periodoMes, anioMesDesde, anioMesHasta, clienteId, estados, origen, facturada),
            pageable);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/informes/facturacion/export")
    public ResponseEntity<byte[]> exportar(
        @RequestParam(required = false) Integer periodoAnio,
        @RequestParam(required = false) Integer periodoMes,
        @RequestParam(required = false) Integer anioMesDesde,
        @RequestParam(required = false) Integer anioMesHasta,
        @RequestParam(required = false) Long clienteId,
        @RequestParam(required = false) List<EstadoPropuesta> estados,
        @RequestParam(required = false) OrigenPropuesta origen,
        @RequestParam(required = false) Boolean facturada) {
        FiltroInformeFacturacion filtro = new FiltroInformeFacturacion(
            periodoAnio, periodoMes, anioMesDesde, anioMesHasta, clienteId, estados, origen, facturada);
        byte[] contenido = servicioInformeFacturacion.exportarDetalleCsv(filtro);

        ContentDisposition disposicion = ContentDisposition.attachment()
            .filename(nombreArchivoExport(filtro), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
            .body(contenido);
    }

    /**
     * Nombre descriptivo según los filtros de período (el resto de los filtros —cliente,
     * estados, origen— no se reflejan en el nombre para no alargarlo). Se arma a partir de
     * campos {@code Integer} ya validados por {@code FiltroInformeFacturacion}/el servicio,
     * nunca de texto libre, así que no hay riesgo de inyección de encabezados; aun así se
     * construye el header con {@link ContentDisposition#attachment()} (el mismo patrón seguro
     * de {@code FacturaControlador.descargarPdf}) por consistencia.
     */
    private String nombreArchivoExport(FiltroInformeFacturacion filtro) {
        if (filtro.periodoAnio() != null && filtro.periodoMes() != null) {
            return "informe-facturacion-%04d-%02d.csv".formatted(filtro.periodoAnio(), filtro.periodoMes());
        }
        if (filtro.periodoAnio() != null) {
            return "informe-facturacion-%04d.csv".formatted(filtro.periodoAnio());
        }
        if (filtro.anioMesDesde() != null && filtro.anioMesHasta() != null) {
            return "informe-facturacion-%s_%s.csv".formatted(
                formatearAnioMes(filtro.anioMesDesde()), formatearAnioMes(filtro.anioMesHasta()));
        }
        if (filtro.anioMesDesde() != null) {
            return "informe-facturacion-desde-%s.csv".formatted(formatearAnioMes(filtro.anioMesDesde()));
        }
        if (filtro.anioMesHasta() != null) {
            return "informe-facturacion-hasta-%s.csv".formatted(formatearAnioMes(filtro.anioMesHasta()));
        }
        return "informe-facturacion.csv";
    }

    private String formatearAnioMes(int anioMes) {
        return "%04d-%02d".formatted(anioMes / 100, anioMes % 100);
    }
}
