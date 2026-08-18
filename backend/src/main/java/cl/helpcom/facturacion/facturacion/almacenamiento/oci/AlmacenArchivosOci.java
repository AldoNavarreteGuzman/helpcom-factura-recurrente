package cl.helpcom.facturacion.facturacion.almacenamiento.oci;

import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivos;
import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivosNoDisponibleException;
import cl.helpcom.facturacion.facturacion.almacenamiento.GeneradorClaveObjeto;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import java.io.InputStream;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Adaptador contra OCI Object Storage, usando su API S3-compatible con el SDK de AWS (ver
 * {@code pom.xml} para la justificación de esa elección sobre el SDK completo de OCI). Solo
 * se instancia cuando {@code app.almacenamiento.tipo=oci}, así que no puede romper el
 * arranque de los perfiles que no lo usan (local/dev/test con almacén local) — no hay forma
 * de ejercitar esta clase en este entorno de desarrollo (sin credenciales ni bucket de OCI
 * a mano), así que quedó escrita con el mismo cuidado que el resto pero sin poder probarla
 * de punta a punta aquí; su contraparte local sí está completamente cubierta por pruebas.
 */
@Component
@ConditionalOnProperty(prefix = "app.almacenamiento", name = "tipo", havingValue = "oci")
public class AlmacenArchivosOci implements AlmacenArchivos {

    private static final Logger log = LoggerFactory.getLogger(AlmacenArchivosOci.class);

    private final S3Client cliente;
    private final String bucket;

    public AlmacenArchivosOci(PropiedadesAlmacenamiento propiedades) {
        PropiedadesAlmacenamiento.Oci configuracionOci = propiedades.oci();
        this.bucket = configuracionOci.bucket();
        this.cliente = S3Client.builder()
            .endpointOverride(URI.create(configuracionOci.endpoint()))
            .region(Region.of(configuracionOci.region()))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                configuracionOci.credenciales().accessKeyId(),
                configuracionOci.credenciales().secretAccessKey())))
            // OCI Object Storage (S3-compatible) exige acceso "path-style"
            // (https://endpoint/bucket/clave), no el "virtual-hosted-style" que AWS S3 usa
            // por defecto (https://bucket.endpoint/clave).
            .forcePathStyle(true)
            .build();
    }

    @Override
    public ReferenciaArchivo guardar(String nombreOriginal, String tipoContenido, byte[] contenido) {
        String claveObjeto = GeneradorClaveObjeto.generar(nombreOriginal);
        try {
            cliente.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(claveObjeto)
                    .contentType(tipoContenido)
                    .contentLength((long) contenido.length)
                    .build(),
                RequestBody.fromBytes(contenido));
        } catch (SdkException ex) {
            throw new AlmacenArchivosNoDisponibleException(
                "No se pudo subir el archivo '" + nombreOriginal + "' a OCI Object Storage.", ex);
        }
        return new ReferenciaArchivo(claveObjeto, nombreOriginal, tipoContenido, contenido.length);
    }

    @Override
    public InputStream obtener(String claveObjeto) {
        try {
            return cliente.getObject(GetObjectRequest.builder().bucket(bucket).key(claveObjeto).build());
        } catch (SdkException ex) {
            throw new AlmacenArchivosNoDisponibleException(
                "No se pudo descargar el archivo con clave '" + claveObjeto + "' de OCI Object Storage.", ex);
        }
    }

    @Override
    public void eliminar(String claveObjeto) {
        try {
            cliente.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(claveObjeto).build());
        } catch (SdkException ex) {
            log.warn("No se pudo eliminar el archivo con clave '{}' de OCI Object Storage: {}", claveObjeto, ex.getMessage());
        }
    }
}
