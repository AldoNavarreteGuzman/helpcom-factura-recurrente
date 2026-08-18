package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.Factura;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import jakarta.persistence.criteria.Join;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composición de filtros dinámicos y opcionales para el listado de facturas. Cada método
 * retorna {@code null} cuando el filtro no aplica; combínalos SIEMPRE con {@link #combinar},
 * nunca encadenando {@code .and(...)} a mano (ver el porqué en
 * {@code PropuestaFacturacionEspecificaciones}).
 */
public final class FacturaEspecificaciones {

    /** Más reciente primero por defecto (estandares-de-codigo.md §3.8); {@code id} desempata facturas de la misma fecha. */
    public static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "fechaFactura")
        .and(Sort.by(Sort.Direction.DESC, "id"));

    private FacturaEspecificaciones() {
    }

    @SafeVarargs
    public static Specification<Factura> combinar(Specification<Factura>... especificaciones) {
        return Specification.allOf(Arrays.stream(especificaciones).filter(Objects::nonNull).toList());
    }

    public static Specification<Factura> paraEmpresa(Long empresaId) {
        return (root, query, cb) -> cb.equal(root.get("empresa").get("id"), empresaId);
    }

    public static Specification<Factura> conNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        String patron = "%" + numero.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("numeroFactura")), patron);
    }

    /**
     * Filtro de rango (ambos límites inclusive, ambos opcionales) — reemplaza al filtro de
     * fecha EXACTA que tenía este listado, para quedar consistente con el rango de períodos
     * del informe de facturación (`PropuestaFacturacionEspecificaciones.conRangoPeriodo`).
     */
    public static Specification<Factura> conFechaDesde(LocalDate fechaDesde) {
        if (fechaDesde == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaFactura"), fechaDesde);
    }

    public static Specification<Factura> conFechaHasta(LocalDate fechaHasta) {
        if (fechaHasta == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaFactura"), fechaHasta);
    }

    public static Specification<Factura> conCliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Factura, PropuestaFacturacion> propuestas = root.join("propuestas");
            return cb.equal(propuestas.get("cliente").get("id"), clienteId);
        };
    }
}
