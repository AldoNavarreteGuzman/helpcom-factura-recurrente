package cl.helpcom.facturacion.comun.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Columnas de auditoría comunes a las tablas de negocio (creado_por/en, modificado_por/en),
 * pobladas por Spring Data JPA Auditing. {@code valor_uf} y {@code ejecucion_ciclo} no las
 * tienen (ver su DDL en modelo-de-datos.md §4) y por lo tanto no extienden esta clase.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadAuditable {

    @CreatedBy
    @Column(name = "creado_por", nullable = false, updatable = false, length = 120)
    private String creadoPor;

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @LastModifiedBy
    @Column(name = "modificado_por", length = 120)
    private String modificadoPor;

    @LastModifiedDate
    @Column(name = "modificado_en")
    private OffsetDateTime modificadoEn;
}
