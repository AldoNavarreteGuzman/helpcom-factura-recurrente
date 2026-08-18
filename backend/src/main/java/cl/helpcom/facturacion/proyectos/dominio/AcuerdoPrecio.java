package cl.helpcom.facturacion.proyectos.dominio;

import cl.helpcom.facturacion.comun.dominio.EntidadAuditable;
import cl.helpcom.facturacion.comun.dominio.Moneda;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "acuerdo_precio")
@Getter
@Setter
@NoArgsConstructor
public class AcuerdoPrecio extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 25)
    private TipoAcuerdo tipo;

    @Column(name = "valor", nullable = false, precision = 18, scale = 4)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "moneda", length = 3)
    private Moneda moneda;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_termino", nullable = false)
    private LocalDate fechaTermino;

    @Column(name = "meses_pactados")
    private Short mesesPactados;

    @Column(name = "observacion", length = 300)
    private String observacion;
}
