package cl.helpcom.facturacion.importacion.dominio;

import cl.helpcom.facturacion.comun.dominio.EntidadAuditable;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.facturacion.dominio.Archivo;
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

@Entity
@Table(name = "importacion_csv")
@Getter
@Setter
@NoArgsConstructor
public class ImportacionCsv extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archivo_id")
    private Archivo archivo;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "fecha_importacion", nullable = false)
    private OffsetDateTime fechaImportacion;

    @Column(name = "total_filas", nullable = false)
    private Integer totalFilas = 0;

    @Column(name = "filas_ok", nullable = false)
    private Integer filasOk = 0;

    @Column(name = "filas_error", nullable = false)
    private Integer filasError = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoImportacionCsv estado;
}
