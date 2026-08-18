package cl.helpcom.facturacion.facturacion.controlador;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.facturacion.dto.FacturaRespuestaDto;
import cl.helpcom.facturacion.facturacion.dto.FacturaSolicitudDto;
import cl.helpcom.facturacion.facturacion.servicio.DescargaArchivo;
import cl.helpcom.facturacion.facturacion.servicio.ServicioFactura;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class FacturaControlador {

    private final ServicioFactura servicioFactura;

    public FacturaControlador(ServicioFactura servicioFactura) {
        this.servicioFactura = servicioFactura;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/facturas")
    public PaginaRespuestaDto<FacturaRespuestaDto> listar(
        @RequestParam(required = false) String numero,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
        @RequestParam(required = false) Long clienteId,
        Pageable pageable) {
        return PaginaRespuestaDto.desde(
            servicioFactura.listar(numero, fechaDesde, fechaHasta, clienteId, pageable), dto -> dto);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/facturas/{id}")
    public FacturaRespuestaDto obtener(@PathVariable Long id) {
        return servicioFactura.obtener(id);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @PostMapping("/api/v1/facturas")
    public ResponseEntity<FacturaRespuestaDto> crear(@Valid @RequestBody FacturaSolicitudDto solicitud) {
        FacturaRespuestaDto creada = servicioFactura.crear(solicitud);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(creada.id())
            .toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @PostMapping(value = "/api/v1/facturas/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FacturaRespuestaDto subirPdf(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new SolicitudInvalidaException("Debe adjuntar un archivo.", "ARCHIVO_REQUERIDO");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new SolicitudInvalidaException("No se pudo leer el archivo enviado.", "ARCHIVO_ILEGIBLE");
        }
        return servicioFactura.subirPdf(id, archivo.getOriginalFilename(), archivo.getContentType(), contenido);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/facturas/{id}/pdf")
    public ResponseEntity<InputStreamResource> descargarPdf(@PathVariable Long id) {
        DescargaArchivo descarga = servicioFactura.descargarPdf(id);

        ContentDisposition disposicion = ContentDisposition.attachment()
            .filename(descarga.nombreOriginal(), StandardCharsets.UTF_8)
            .build();

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
            .body(new InputStreamResource(descarga.contenido()));
    }
}
