package cl.helpcom.facturacion.facturacion.almacenamiento.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Configuración del almacén de archivos. {@code tipo} selecciona el adaptador (ver
 * {@code @ConditionalOnProperty} en {@code AlmacenArchivosLocal}/{@code AlmacenArchivosOci});
 * por defecto {@code LOCAL}. {@code tamanoMaximo} y la validación del tipo de contenido
 * (solo {@code application/pdf}) las aplica {@code ServicioFactura}, no los adaptadores: el
 * almacén en sí es un almacén genérico de bytes, reutilizable a futuro para otros archivos
 * (p. ej. el CSV de importación).
 */
@ConfigurationProperties(prefix = "app.almacenamiento")
public record PropiedadesAlmacenamiento(TipoAlmacen tipo, DataSize tamanoMaximo, Local local, Oci oci) {

    private static final DataSize TAMANO_MAXIMO_POR_DEFECTO = DataSize.ofMegabytes(10);

    public PropiedadesAlmacenamiento {
        if (tipo == null) {
            tipo = TipoAlmacen.LOCAL;
        }
        if (tamanoMaximo == null) {
            tamanoMaximo = TAMANO_MAXIMO_POR_DEFECTO;
        }
        if (local == null) {
            local = new Local(null);
        }
        if (oci == null) {
            oci = new Oci(null, null, null, null, null);
        }
    }

    public record Local(String ruta) {
        private static final String RUTA_POR_DEFECTO = "./almacenamiento-local";

        public Local {
            if (ruta == null || ruta.isBlank()) {
                ruta = RUTA_POR_DEFECTO;
            }
        }
    }

    /** Solo se usan (y se exigen) cuando {@code tipo = OCI}. */
    public record Oci(String bucket, String namespace, String region, String endpoint, Credenciales credenciales) {

        public record Credenciales(String accessKeyId, String secretAccessKey) {
        }
    }
}
