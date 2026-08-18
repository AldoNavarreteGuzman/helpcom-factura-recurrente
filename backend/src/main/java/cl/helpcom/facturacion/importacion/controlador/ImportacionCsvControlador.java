package cl.helpcom.facturacion.importacion.controlador;

import cl.helpcom.facturacion.comun.dto.PaginaRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.importacion.servicio.ServicioImportacionCsv;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImportacionCsvControlador {

    private final ServicioImportacionCsv servicioImportacionCsv;

    public ImportacionCsvControlador(ServicioImportacionCsv servicioImportacionCsv) {
        this.servicioImportacionCsv = servicioImportacionCsv;
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @PostMapping(value = "/api/v1/importaciones/previsualizar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacionPreviewRespuestaDto previsualizar(@RequestParam("archivo") MultipartFile archivo) {
        return servicioImportacionCsv.previsualizar(archivo);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @PostMapping(value = "/api/v1/importaciones/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacionCsvRespuestaDto confirmar(@RequestParam("archivo") MultipartFile archivo) {
        return servicioImportacionCsv.confirmar(archivo);
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR','OPERADOR')")
    @GetMapping("/api/v1/importaciones")
    public PaginaRespuestaDto<ImportacionCsvRespuestaDto> listar(Pageable pageable) {
        return PaginaRespuestaDto.desde(servicioImportacionCsv.listar(pageable), dto -> dto);
    }
}
