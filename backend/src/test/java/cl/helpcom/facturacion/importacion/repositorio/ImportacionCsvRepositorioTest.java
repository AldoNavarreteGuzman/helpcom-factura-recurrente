package cl.helpcom.facturacion.importacion.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import cl.helpcom.facturacion.comun.config.AuditoriaConfig;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.importacion.dominio.EstadoImportacionCsv;
import cl.helpcom.facturacion.importacion.dominio.ImportacionCsv;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

/**
 * D3 (deuda-tecnica.md ítem 3, estandares-de-codigo.md §3.8): el historial de importaciones
 * debe ordenar por {@code fecha_importacion} descendente sin que el cliente pida {@code sort}.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ImportacionCsvRepositorioTest {

    @Autowired
    private EmpresaRepositorio empresaRepositorio;

    @Autowired
    private ImportacionCsvRepositorio importacionCsvRepositorio;

    private Empresa empresa;

    @BeforeEach
    void configurar() {
        empresa = new Empresa();
        empresa.setRut("76111222-3");
        empresa.setRazonSocial("Empresa de Prueba SpA");
        empresaRepositorio.save(empresa);

        guardarImportacion("import-ene.csv", OffsetDateTime.parse("2026-01-05T10:00:00Z"));
        guardarImportacion("import-mar.csv", OffsetDateTime.parse("2026-03-05T10:00:00Z"));
        guardarImportacion("import-feb.csv", OffsetDateTime.parse("2026-02-05T10:00:00Z"));
    }

    private void guardarImportacion(String nombreArchivo, OffsetDateTime fechaImportacion) {
        ImportacionCsv importacion = new ImportacionCsv();
        importacion.setEmpresa(empresa);
        importacion.setNombreArchivo(nombreArchivo);
        importacion.setFechaImportacion(fechaImportacion);
        importacion.setEstado(EstadoImportacionCsv.PROCESADA);
        importacionCsvRepositorio.save(importacion);
    }

    @Test
    void deberiaOrdenarPorFechaDeImportacionDescendenteSinSortExplicito() {
        Page<ImportacionCsv> pagina = importacionCsvRepositorio.findByEmpresaIdOrderByFechaImportacionDesc(
            empresa.getId(), PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(ImportacionCsv::getNombreArchivo)
            .containsExactly("import-mar.csv", "import-feb.csv", "import-ene.csv");
    }
}
