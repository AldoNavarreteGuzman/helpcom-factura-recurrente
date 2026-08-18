package cl.helpcom.facturacion.facturacion.dominio;

import cl.helpcom.facturacion.comun.dominio.EntidadAuditable;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "factura")
@Getter
@Setter
@NoArgsConstructor
public class Factura extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "numero_factura", nullable = false, length = 40)
    private String numeroFactura;

    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fechaFactura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archivo_id")
    private Archivo archivo;

    @Column(name = "observacion", length = 300)
    private String observacion;

    /** Lado inverso de {@code PropuestaFacturacion.factura}; una factura agrupa 1..N. */
    @OneToMany(mappedBy = "factura", fetch = FetchType.LAZY)
    private List<PropuestaFacturacion> propuestas = new ArrayList<>();
}
