package cl.helpcom.facturacion.facturacion.dominio;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.dominio.EntidadAuditable;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.importacion.dominio.ImportacionCsv;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.dominio.TipoAcuerdo;
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

/**
 * Núcleo del sistema: el borrador de lo que se va a facturar en un período, con el snapshot
 * inmutable del cálculo. {@code valor_uf} y {@code fecha_valor_uf} son columnas simples (no
 * una relación): la propuesta guarda el valor usado como parte de su snapshot, por fecha.
 */
@Entity
@Table(name = "propuesta_facturacion")
@Getter
@Setter
@NoArgsConstructor
public class PropuestaFacturacion extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "importacion_id")
    private ImportacionCsv importacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id")
    private Factura factura;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 15)
    private OrigenPropuesta origen;

    @Column(name = "periodo_anio", nullable = false)
    private Short periodoAnio;

    @Column(name = "periodo_mes", nullable = false)
    private Short periodoMes;

    @Column(name = "fecha_facturacion", nullable = false)
    private LocalDate fechaFacturacion;

    @Column(name = "descripcion", nullable = false, length = 300)
    private String descripcion;

    // --- Snapshot inmutable del cálculo ---

    @Enumerated(EnumType.STRING)
    @Column(name = "moneda_origen", nullable = false, length = 3)
    private Moneda monedaOrigen;

    @Column(name = "precio_base_neto", nullable = false, precision = 18, scale = 4)
    private BigDecimal precioBaseNeto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acuerdo_id")
    private AcuerdoPrecio acuerdo;

    @Enumerated(EnumType.STRING)
    @Column(name = "acuerdo_tipo", length = 25)
    private TipoAcuerdo acuerdoTipo;

    @Column(name = "acuerdo_valor", precision = 18, scale = 4)
    private BigDecimal acuerdoValor;

    @Enumerated(EnumType.STRING)
    @Column(name = "acuerdo_moneda", length = 3)
    private Moneda acuerdoMoneda;

    @Column(name = "valor_uf", precision = 12, scale = 4)
    private BigDecimal valorUf;

    @Column(name = "fecha_valor_uf")
    private LocalDate fechaValorUf;

    @Column(name = "neto_clp", nullable = false, precision = 18, scale = 0)
    private BigDecimal netoClp;

    @Column(name = "tasa_iva", nullable = false, precision = 6, scale = 4)
    private BigDecimal tasaIva;

    @Column(name = "iva_clp", nullable = false, precision = 18, scale = 0)
    private BigDecimal ivaClp;

    @Column(name = "total_clp", nullable = false, precision = 18, scale = 0)
    private BigDecimal totalClp;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoPropuesta estado = EstadoPropuesta.PENDIENTE;
}
