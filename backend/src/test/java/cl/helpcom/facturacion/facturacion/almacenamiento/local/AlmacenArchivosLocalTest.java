package cl.helpcom.facturacion.facturacion.almacenamiento.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.helpcom.facturacion.facturacion.almacenamiento.AlmacenArchivosNoDisponibleException;
import cl.helpcom.facturacion.facturacion.almacenamiento.ReferenciaArchivo;
import cl.helpcom.facturacion.facturacion.almacenamiento.config.PropiedadesAlmacenamiento;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlmacenArchivosLocalTest {

    @TempDir
    private Path directorioTemporal;

    private AlmacenArchivosLocal almacen;

    @BeforeEach
    void configurar() {
        PropiedadesAlmacenamiento propiedades = new PropiedadesAlmacenamiento(
            null, null, new PropiedadesAlmacenamiento.Local(directorioTemporal.toString()), null);
        almacen = new AlmacenArchivosLocal(propiedades);
    }

    @Test
    void deberiaRecuperarElMismoContenidoQueSeGuardo() throws IOException {
        byte[] contenidoOriginal = "%PDF-1.4 contenido de prueba".getBytes(StandardCharsets.UTF_8);

        ReferenciaArchivo referencia = almacen.guardar("informe.pdf", "application/pdf", contenidoOriginal);

        assertThat(referencia.claveObjeto()).endsWith(".pdf");
        assertThat(referencia.nombreOriginal()).isEqualTo("informe.pdf");
        assertThat(referencia.tamanoBytes()).isEqualTo(contenidoOriginal.length);

        try (InputStream recuperado = almacen.obtener(referencia.claveObjeto())) {
            byte[] contenidoRecuperado = recuperado.readAllBytes();
            assertThat(contenidoRecuperado).isEqualTo(contenidoOriginal);
        }
    }

    @Test
    void cadaArchivoGuardadoDeberiaTenerUnaClaveDistinta() {
        ReferenciaArchivo primero = almacen.guardar("a.pdf", "application/pdf", new byte[] {1, 2, 3});
        ReferenciaArchivo segundo = almacen.guardar("b.pdf", "application/pdf", new byte[] {4, 5, 6});

        assertThat(primero.claveObjeto()).isNotEqualTo(segundo.claveObjeto());
    }

    @Test
    void eliminarDeberiaHacerQueObtenerYaNoEncuentreElArchivo() {
        ReferenciaArchivo referencia = almacen.guardar("temporal.pdf", "application/pdf", new byte[] {9});

        almacen.eliminar(referencia.claveObjeto());

        assertThatThrownBy(() -> almacen.obtener(referencia.claveObjeto()))
            .isInstanceOf(AlmacenArchivosNoDisponibleException.class);
    }

    @Test
    void eliminarUnaClaveInexistenteNoDeberiaLanzarExcepcion() {
        almacen.eliminar("no-existe.pdf");
    }

    @Test
    void obtenerUnaClaveConIntentoDePathTraversalDeberiaRechazarse() {
        assertThatThrownBy(() -> almacen.obtener("../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
