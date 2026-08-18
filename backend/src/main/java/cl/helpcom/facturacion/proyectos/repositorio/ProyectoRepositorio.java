package cl.helpcom.facturacion.proyectos.repositorio;

import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProyectoRepositorio extends JpaRepository<Proyecto, Long>, JpaSpecificationExecutor<Proyecto> {

    Optional<Proyecto> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Usado por la importación CSV para resolver la columna opcional {@code codigo_proyecto}
     * (modelo-de-datos.md §6) al proyecto registrado.
     */
    Optional<Proyecto> findByEmpresaIdAndCodigo(Long empresaId, String codigo);

    boolean existsByClienteIdAndEmpresaId(Long clienteId, Long empresaId);

    boolean existsByTipoServicioIdAndEmpresaId(Long tipoServicioId, Long empresaId);

    /**
     * Solo los ids: el ciclo de facturación los usa para procesar cada proyecto en su
     * propia transacción, sin arrastrar entidades cargadas de una transacción anterior.
     */
    @Query("select p.id from Proyecto p where p.empresa.id = :empresaId and p.activo = true")
    List<Long> encontrarIdsActivosDeLaEmpresa(@Param("empresaId") Long empresaId);
}
