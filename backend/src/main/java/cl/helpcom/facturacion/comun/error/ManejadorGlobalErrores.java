package cl.helpcom.facturacion.comun.error;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private static final String BASE_URI_ERRORES = "https://helpcom.cl/errores/";

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        return construirProblema(HttpStatus.NOT_FOUND, "Recurso no encontrado", "recurso-no-encontrado", ex);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException ex) {
        return construirProblema(HttpStatus.CONFLICT, "Regla de negocio no cumplida", "regla-negocio", ex);
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail manejarSolicitudInvalida(SolicitudInvalidaException ex) {
        return construirProblema(HttpStatus.BAD_REQUEST, "Solicitud inválida", "solicitud-invalida", ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación.");
        problema.setTitle("Acceso denegado");
        problema.setType(URI.create(BASE_URI_ERRORES + "acceso-denegado"));
        return problema;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE, "El archivo enviado supera el tamaño máximo permitido.");
        problema.setTitle("Archivo demasiado grande");
        problema.setType(URI.create(BASE_URI_ERRORES + "archivo-demasiado-grande"));
        problema.setProperty("codigo", "ARCHIVO_DEMASIADO_GRANDE");
        return problema;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacionArgumentos(MethodArgumentNotValidException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Uno o más campos no cumplen las validaciones requeridas.");
        problema.setTitle("Solicitud inválida");
        problema.setType(URI.create(BASE_URI_ERRORES + "solicitud-invalida"));
        problema.setProperty("codigo", "VALIDACION_CAMPOS");
        problema.setProperty("errores", ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .toList());
        return problema;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex) {
        log.error("Error inesperado no controlado", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Contacte al administrador.");
        problema.setTitle("Error interno");
        problema.setType(URI.create(BASE_URI_ERRORES + "error-interno"));
        return problema;
    }

    private ProblemDetail construirProblema(HttpStatus estado, String titulo, String tipo, ExcepcionAplicacion ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, ex.getMessage());
        problema.setTitle(titulo);
        problema.setType(URI.create(BASE_URI_ERRORES + tipo));
        if (ex.getCodigo() != null) {
            problema.setProperty("codigo", ex.getCodigo());
        }
        return problema;
    }
}
