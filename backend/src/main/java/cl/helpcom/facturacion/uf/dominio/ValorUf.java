package cl.helpcom.facturacion.uf.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tabla global (sin empresa_id) y sin columnas de auditoría estándar: no extiende
 * {@code EntidadAuditable}. La clave primaria es la fecha misma (natural, sin secuencia).
 */
@Entity
@Table(name = "valor_uf")
@Getter
@Setter
@NoArgsConstructor
public class ValorUf {

    @Id
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "valor", nullable = false, precision = 12, scale = 4)
    private BigDecimal valor;

    @Column(name = "fuente", nullable = false, length = 40)
    private String fuente;

    @Column(name = "registrado_en", nullable = false)
    private OffsetDateTime registradoEn;
}
