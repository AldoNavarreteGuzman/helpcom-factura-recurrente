package cl.helpcom.facturacion.importacion.servicio;

import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.armado.EntradaArmadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.Archivo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.ArchivoRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.importacion.csv.FilaCsv;
import cl.helpcom.facturacion.importacion.csv.LectorCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import cl.helpcom.facturacion.importacion.dominio.ImportacionCsv;
import cl.helpcom.facturacion.importacion.dto.ImportacionCsvRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewFilaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewRespuestaDto;
import cl.helpcom.facturacion.importacion.dto.ImportacionPreviewResumenDto;
import cl.helpcom.facturacion.importacion.repositorio.ImportacionCsvRepositorio;
import cl.helpcom.facturacion.importacion.validacion.FilaValidada;
import cl.helpcom.facturacion.importacion.validacion.ValidadorFilaCsv;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importación masiva CSV (arquitectura-tecnica.md §10, modelo-de-datos.md §6): flujo en dos
 * fases, previsualizar y confirmar.
 *
 * <p><b>Estrategia de confirmación — decisión de diseño:</b> de las dos estrategias posibles
 * (reenviar el mismo archivo y re-validar en {@code confirmar}, o que {@code previsualizar}
 * deje el archivo guardado con un id temporal), se eligió la primera: el cliente reenvía el
 * mismo CSV a {@code confirmar}, que vuelve a parsear y validar antes de persistir, todo en
 * una única transacción. Es más simple, no arrastra estado entre dos peticiones HTTP, y
 * re-validar es barato — la alternativa habría requerido gestionar la limpieza de archivos
 * temporales huérfanos (previsualizaciones que nunca se confirman).
 *
 * <p><b>Transaccionalidad — decisión de diseño:</b> a diferencia del ciclo (que procesa cada
 * proyecto en su propia transacción porque es un proceso en segundo plano que debe avanzar
 * pese a fallos puntuales), {@code confirmar} es una operación síncrona iniciada por el
 * usuario: todas las filas válidas se persisten en una única transacción. Si algo falla a
 * mitad de camino, toda la solicitud falla — el usuario simplemente reintenta reenviando el
 * archivo, sin quedar con una importación parcialmente aplicada de forma silenciosa.
 */
@Service
public class ServicioImportacionCsv {

    private static final String TIPO_CONTENIDO_CSV = "text/csv";

    private final LectorCsv lectorCsv;
    private final ValidadorFilaCsv validadorFilaCsv;
    private final ArmadorPropuesta armadorPropuesta;
    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;
    private final ImportacionCsvRepositorio importacionCsvRepositorio;
    private final ArchivoRepositorio archivoRepositorio;
    private final AlmacenArchivos almacenArchivos;
    private final EmpresaRepositorio empresaRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public ServicioImportacionCsv(
        LectorCsv lectorCsv,
        ValidadorFilaCsv validadorFilaCsv,
        ArmadorPropuesta armadorPropuesta,
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio,
        ImportacionCsvRepositorio importacionCsvRepositorio,
        ArchivoRepositorio archivoRepositorio,
        AlmacenArchivos almacenArchivos,
        EmpresaRepositorio empresaRepositorio,
        ContextoEmpresa contextoEmpresa) {
        this.lectorCsv = lectorCsv;
        this.validadorFilaCsv = validadorFilaCsv;
        this.armadorPropuesta = armadorPropuesta;
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.importacionCsvRepositorio = importacionCsvRepositorio;
        this.archivoRepositorio = archivoRepositorio;
        this.almacenArchivos = almacenArchivos;
        this.empresaRepositorio = empresaRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public ImportacionPreviewRespuestaDto previsualizar(MultipartFile archivo) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        List<FilaCsv> filas = lectorCsv.leer(leerBytes(archivo));

        List<ImportacionPreviewFilaDto> filasDto = new ArrayList<>();
        int ok = 0;
        int advertencia = 0;
        int error = 0;
        for (FilaCsv fila : filas) {
            FilaValidada validada = validadorFilaCsv.validar(fila, empresaId);
            ImportacionPreviewFilaDto dto = aPreviewDto(validada, empresaId);
            filasDto.add(dto);
            switch (dto.estado()) {
                case OK -> ok++;
                case ADVERTENCIA -> advertencia++;
                case ERROR -> error++;
            }
        }

        return new ImportacionPreviewRespuestaDto(
            new ImportacionPreviewResumenDto(filas.size(), ok, advertencia, error), filasDto);
    }

    @Transactional
    public ImportacionCsvRespuestaDto confirmar(MultipartFile archivo) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        byte[] contenido = leerBytes(archivo);
        List<FilaCsv> filas = lectorCsv.leer(contenido);
        Empresa empresa = empresaRepositorio.getReferenceById(empresaId);

        int filasOk = 0;
        int filasError = 0;
        int cantidadPendienteUf = 0;
        List<PropuestaFacturacion> propuestas = new ArrayList<>();
        for (FilaCsv fila : filas) {
            FilaValidada validada = validadorFilaCsv.validar(fila, empresaId);
            if (!validada.esValida()) {
                filasError++;
                continue;
            }
            PropuestaFacturacion propuesta = armar(validada, empresa);
            if (propuesta.getEstado() == EstadoPropuesta.PENDIENTE_UF) {
                cantidadPendienteUf++;
            }
            propuestas.add(propuesta);
            filasOk++;
        }

        EstadoImportacionCsv estado = determinarEstado(filasOk, filasError);

        ReferenciaArchivo referencia = almacenArchivos.guardar(
            nombreArchivoOriginal(archivo), TIPO_CONTENIDO_CSV, contenido);
        Archivo archivoGuardado = new Archivo();
        archivoGuardado.setEmpresa(empresa);
        archivoGuardado.setNombreOriginal(referencia.nombreOriginal());
        archivoGuardado.setClaveObjeto(referencia.claveObjeto());
        archivoGuardado.setTipoContenido(referencia.tipoContenido());
        archivoGuardado.setTamanoBytes(referencia.tamanoBytes());
        archivoRepositorio.save(archivoGuardado);

        ImportacionCsv importacion = new ImportacionCsv();
        importacion.setEmpresa(empresa);
        importacion.setArchivo(archivoGuardado);
        importacion.setNombreArchivo(nombreArchivoOriginal(archivo));
        importacion.setFechaImportacion(OffsetDateTime.now());
        importacion.setTotalFilas(filas.size());
        importacion.setFilasOk(filasOk);
        importacion.setFilasError(filasError);
        importacion.setEstado(estado);
        importacionCsvRepositorio.save(importacion);

        for (PropuestaFacturacion propuesta : propuestas) {
            propuesta.setImportacion(importacion);
            propuestaFacturacionRepositorio.save(propuesta);
        }

        return aRespuesta(importacion, cantidadPendienteUf);
    }

    @Transactional(readOnly = true)
    public Page<ImportacionCsvRespuestaDto> listar(Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        return importacionCsvRepositorio.findByEmpresaIdOrderByFechaImportacionDesc(empresaId, pageable)
            .map(importacion -> aRespuesta(importacion, null));
    }

    private ImportacionPreviewFilaDto aPreviewDto(FilaValidada validada, Long empresaId) {
        FilaCsv fila = validada.fila();
        if (!validada.esValida()) {
            return new ImportacionPreviewFilaDto(
                fila.numeroFila(), validada.estado(), validada.mensajes(),
                fila.rutCliente(), fila.codigoProyecto(), fila.descripcion(), fila.periodo(),
                fila.fechaFacturacion(), fila.moneda(), fila.montoNeto(), fila.observacion(),
                null, null, null);
        }

        PropuestaFacturacion propuesta = armar(validada, empresaRepositorio.getReferenceById(empresaId));

        List<String> mensajes = new ArrayList<>(validada.mensajes());
        EstadoFilaCsv estado = validada.estado();
        if (propuesta.getEstado() == EstadoPropuesta.PENDIENTE_UF) {
            mensajes.add("UF no disponible para la fecha " + validada.fechaFacturacion()
                + ": la propuesta quedará en estado PENDIENTE_UF.");
            estado = EstadoFilaCsv.ADVERTENCIA;
        }

        return new ImportacionPreviewFilaDto(
            fila.numeroFila(), estado, mensajes,
            fila.rutCliente(), fila.codigoProyecto(), fila.descripcion(), fila.periodo(),
            fila.fechaFacturacion(), fila.moneda(), fila.montoNeto(), fila.observacion(),
            propuesta.getNetoClp(), propuesta.getIvaClp(), propuesta.getTotalClp());
    }

    private PropuestaFacturacion armar(FilaValidada validada, Empresa empresa) {
        return armadorPropuesta.armar(new EntradaArmadoPropuesta(
            empresa, validada.cliente(), validada.proyecto(), OrigenPropuesta.CSV,
            validada.periodoAnio(), validada.periodoMes(), validada.fechaFacturacion(),
            validada.fila().descripcion(), validada.montoNeto(), validada.moneda()));
    }

    private EstadoImportacionCsv determinarEstado(int filasOk, int filasError) {
        if (filasOk == 0) {
            return EstadoImportacionCsv.RECHAZADA;
        }
        if (filasError > 0) {
            return EstadoImportacionCsv.PARCIAL;
        }
        return EstadoImportacionCsv.PROCESADA;
    }

    private byte[] leerBytes(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SolicitudInvalidaException("Debe adjuntar un archivo CSV.", "ARCHIVO_REQUERIDO");
        }
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new SolicitudInvalidaException("No se pudo leer el archivo enviado.", "ARCHIVO_ILEGIBLE");
        }
    }

    private String nombreArchivoOriginal(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename();
        return nombre == null || nombre.isBlank() ? "importacion.csv" : nombre;
    }

    /**
     * {@code cantidadPendienteUf} solo se conoce con certeza en el momento de
     * {@link #confirmar}, donde se cuenta en vivo mientras se arman las propuestas — se pasa
     * {@code null} desde {@link #listar} (historial) porque no hay una columna persistida para
     * reconstruirlo después (ver el javadoc de {@link ImportacionCsvRespuestaDto}).
     */
    private ImportacionCsvRespuestaDto aRespuesta(ImportacionCsv importacion, Integer cantidadPendienteUf) {
        return new ImportacionCsvRespuestaDto(
            importacion.getId(), importacion.getNombreArchivo(), importacion.getFechaImportacion(),
            importacion.getTotalFilas(), importacion.getFilasOk(), cantidadPendienteUf,
            importacion.getFilasError(), importacion.getEstado());
    }
}
