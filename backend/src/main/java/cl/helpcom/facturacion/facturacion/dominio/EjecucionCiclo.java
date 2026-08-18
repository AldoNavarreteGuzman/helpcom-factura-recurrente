package cl.helpcom.facturacion.facturacion.dominio;

import cl.helpcom.facturacion.empresa.dominio.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trazabilidad de cada corrida del proceso del día 1. No tiene columnas de auditoría
 * estándar (creado_por/en, modificado_por/en); su propio registro es {@code ejecutado_por}
 * / {@code ejecutado_en}, por lo que no extiende {@code EntidadAuditable}.
 */
@Entity
@Table(name = "ejecucion_ciclo")
@Getter
@Setter
@NoArgsConstructor
public class EjecucionCiclo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "periodo_anio", nullable = false)
    private Short periodoAnio;

    @Column(name = "periodo_mes", nullable = false)
    private Short periodoMes;

    @Column(name = "ejecutado_en", nullable = false)
    private OffsetDateTime ejecutadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "disparo", nullable = false, length = 15)
    private DisparoCiclo disparo;

    @Column(name = "cantidad_generadas", nullable = false)
    private Integer cantidadGeneradas = 0;

    @Column(name = "cantidad_pendientes_uf", nullable = false)
    private Integer cantidadPendientesUf = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoEjecucionCiclo estado;

    @Column(name = "observacion", length = 500)
    private String observacion;

    @Column(name = "ejecutado_por", nullable = false, length = 120)
    private String ejecutadoPor;
}
