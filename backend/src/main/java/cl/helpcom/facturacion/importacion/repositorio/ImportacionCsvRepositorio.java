package cl.helpcom.facturacion.importacion.repositorio;

import cl.helpcom.facturacion.importacion.dominio.ImportacionCsv;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportacionCsvRepositorio extends JpaRepository<ImportacionCsv, Long> {

    /** Más reciente primero por defecto (estandares-de-codigo.md §3.8), como {@code EjecucionCicloRepositorio}. */
    Page<ImportacionCsv> findByEmpresaIdOrderByFechaImportacionDesc(Long empresaId, Pageable pageable);
}
