package cl.helpcom.facturacion.facturacion.almacenamiento.local;

import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivosNoDisponibleException;
import cl.helpcom.facturacion.facturacion.almacenamiento.GeneradorClaveObjeto;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador por defecto (perfiles local/dev/test): guarda los objetos en un directorio del
 * sistema de archivos, configurable con {@code app.almacenamiento.local.ruta}. Ver el
 * esquema de claves en {@link GeneradorClaveObjeto}.
 */
@Component
@ConditionalOnProperty(prefix = "app.almacenamiento", name = "tipo", havingValue = "local", matchIfMissing = true)
public class AlmacenArchivosLocal implements AlmacenArchivos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenArchivosLocal.class);

    private final Path directorioBase;

    public AlmacenArchivosLocal(PropiedadesAlmacenamiento propiedades) {
        this.directorioBase = Path.of(propiedades.local().ruta()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directorioBase);
        } catch (IOException ex) {
            throw new AlmacenArchivosNoDisponibleException(
                "No se pudo preparar el directorio de almacenamiento local: " + directorioBase, ex);
        }
    }

    @Override
    public ReferenciaArchivo guardar(String nombreOriginal, String tipoContenido, byte[] contenido) {
        String claveObjeto = GeneradorClaveObjeto.generar(nombreOriginal);
        Path destino = resolverRuta(claveObjeto);
        try {
            Files.write(destino, contenido);
        } catch (IOException ex) {
            throw new AlmacenArchivosNoDisponibleException(
                "No se pudo guardar el archivo '" + nombreOriginal + "' en el almacén local.", ex);
        }
        return new ReferenciaArchivo(claveObjeto, nombreOriginal, tipoContenido, contenido.length);
    }

    @Override
    public InputStream obtener(String claveObjeto) {
        try {
            return Files.newInputStream(resolverRuta(claveObjeto));
        } catch (IOException ex) {
            throw new AlmacenArchivosNoDisponibleException(
                "No se pudo leer el archivo con clave '" + claveObjeto + "' del almacén local.", ex);
        }
    }

    @Override
    public void eliminar(String claveObjeto) {
        try {
            Files.deleteIfExists(resolverRuta(claveObjeto));
        } catch (IOException ex) {
            log.warn("No se pudo eliminar el archivo con clave '{}' del almacén local: {}", claveObjeto, ex.getMessage());
        }
    }

    /** Defensa contra path traversal: la clave nunca debe poder salir del directorio base. */
    private Path resolverRuta(String claveObjeto) {
        Path ruta = directorioBase.resolve(claveObjeto).normalize();
        if (!ruta.startsWith(directorioBase)) {
            throw new IllegalArgumentException("Clave de objeto inválida: " + claveObjeto);
        }
        return ruta;
    }
}
