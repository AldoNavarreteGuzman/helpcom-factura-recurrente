package cl.helpcom.facturacion.proyectos.repositorio;

import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcuerdoPrecioRepositorio extends JpaRepository<AcuerdoPrecio, Long> {

    List<AcuerdoPrecio> findByProyectoIdAndEmpresaId(Long proyectoId, Long empresaId);

    Optional<AcuerdoPrecio> findByIdAndProyectoIdAndEmpresaId(Long id, Long proyectoId, Long empresaId);

    /**
     * Acuerdos del mismo proyecto cuya vigencia [fecha_inicio, fecha_termino] (ambos
     * extremos incluidos, igual que la exclusion constraint {@code ex_acuerdo_no_traslape})
     * se traslapa con el rango dado. {@code idExcluido} permite omitir el propio acuerdo al
     * validar una actualización.
     */
    @Query("""
        select a from AcuerdoPrecio a
        where a.proyecto.id = :proyectoId
          and (:idExcluido is null or a.id <> :idExcluido)
          and a.fechaInicio <= :fechaTermino
          and a.fechaTermino >= :fechaInicio
        """)
    List<AcuerdoPrecio> buscarTraslapados(
        @Param("proyectoId") Long proyectoId,
        @Param("fechaInicio") LocalDate fechaInicio,
        @Param("fechaTermino") LocalDate fechaTermino,
        @Param("idExcluido") Long idExcluido);

    /**
     * El acuerdo del proyecto vigente en la fecha dada. Por diseño hay a lo más uno (lo
     * garantiza {@code ex_acuerdo_no_traslape}).
     */
    @Query("""
        select a from AcuerdoPrecio a
        where a.proyecto.id = :proyectoId
          and a.fechaInicio <= :fecha
          and a.fechaTermino >= :fecha
        """)
    Optional<AcuerdoPrecio> buscarVigente(@Param("proyectoId") Long proyectoId, @Param("fecha") LocalDate fecha);
}
