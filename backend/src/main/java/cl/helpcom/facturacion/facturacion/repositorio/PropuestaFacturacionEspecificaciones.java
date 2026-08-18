package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composición de filtros dinámicos y opcionales para el listado de propuestas. Cada método
 * retorna {@code null} cuando el filtro no aplica; combínalos SIEMPRE con {@link #combinar},
 * nunca encadenando {@code .and(...)} a mano — a partir de Spring Data JPA 4.0,
 * {@code Specification.and}/{@code allOf} exigen que cada especificación no sea nula (antes
 * los ignoraban en silencio), así que encadenar directamente revienta con
 * {@code IllegalArgumentException} en cuanto un filtro opcional viene sin usar.
 */
public final class PropuestaFacturacionEspecificaciones {

    /**
     * Más reciente primero por defecto (estandares-de-codigo.md §3.8): las propuestas no
     * tienen un timestamp de negocio propio más natural que su período, así que se ordena por
     * período descendente; {@code id} desempata las del mismo período (aproxima el orden de
     * creación, ya que los ids son autoincrementales). No aplica a la exportación CSV del
     * informe, que ya ordena explícito y ascendente a propósito (cronológico para el reporte).
     */
    public static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "periodoAnio")
        .and(Sort.by(Sort.Direction.DESC, "periodoMes"))
        .and(Sort.by(Sort.Direction.DESC, "id"));

    private PropuestaFacturacionEspecificaciones() {
    }

    /** Combina especificaciones con AND, descartando las que sean {@code null} (ver la nota de clase). */
    @SafeVarargs
    public static Specification<PropuestaFacturacion> combinar(Specification<PropuestaFacturacion>... especificaciones) {
        return Specification.allOf(Arrays.stream(especificaciones).filter(Objects::nonNull).toList());
    }

    public static Specification<PropuestaFacturacion> paraEmpresa(Long empresaId) {
        return (root, query, cb) -> cb.equal(root.get("empresa").get("id"), empresaId);
    }

    public static Specification<PropuestaFacturacion> conPeriodo(Integer periodoAnio, Integer periodoMes) {
        if (periodoAnio == null && periodoMes == null) {
            return null;
        }
        return (root, query, cb) -> {
            var predicados = cb.conjunction();
            if (periodoAnio != null) {
                predicados = cb.and(predicados, cb.equal(root.get("periodoAnio"), periodoAnio.shortValue()));
            }
            if (periodoMes != null) {
                predicados = cb.and(predicados, cb.equal(root.get("periodoMes"), periodoMes.shortValue()));
            }
            return predicados;
        };
    }

    public static Specification<PropuestaFacturacion> conCliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
    }

    public static Specification<PropuestaFacturacion> conEstado(EstadoPropuesta estado) {
        if (estado == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("estado"), estado);
    }

    public static Specification<PropuestaFacturacion> conEstados(List<EstadoPropuesta> estados) {
        if (estados == null || estados.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.<EstadoPropuesta>get("estado").in(estados);
    }

    public static Specification<PropuestaFacturacion> conOrigen(OrigenPropuesta origen) {
        if (origen == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("origen"), origen);
    }

    /**
     * Fetch join LEFT de {@code factura} para evitar N+1 al mostrar numero/fecha de factura
     * en un listado (la relación es LAZY y se necesita en cada fila). Se agrega como una
     * especificación más porque {@link #combinar} es el único punto por el que pasan todas
     * las consultas de listado; su predicado es un {@code true} trivial, el trabajo real es
     * el efecto secundario del {@code fetch}. Se omite en la consulta de {@code COUNT} que
     * genera {@code findAll(Specification, Pageable)} (pide resultado {@code Long}, donde un
     * fetch no es válido) — de ahí el guard sobre {@code getResultType()}.
     */
    public static Specification<PropuestaFacturacion> conFacturaFetch() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("factura", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    /**
     * {@code true}: solo propuestas asociadas a una factura ({@code estado = FACTURADA}).
     * {@code false}: solo las que aún no se facturan (cualquier otro estado).
     */
    public static Specification<PropuestaFacturacion> conFacturada(Boolean facturada) {
        if (facturada == null) {
            return null;
        }
        return (root, query, cb) -> facturada
            ? cb.equal(root.get("estado"), EstadoPropuesta.FACTURADA)
            : cb.notEqual(root.get("estado"), EstadoPropuesta.FACTURADA);
    }

    /**
     * Rango de períodos, ambos límites inclusive, codificados como {@code AAAAMM} (p. ej.
     * 202601 para enero de 2026) — usado por el informe de facturación (informes/) para
     * reportes multi-período. El orden cronológico se preserva con esta codificación porque
     * {@code periodo_mes} está siempre entre 1 y 12.
     */
    public static Specification<PropuestaFacturacion> conRangoPeriodo(Integer anioMesDesde, Integer anioMesHasta) {
        if (anioMesDesde == null && anioMesHasta == null) {
            return null;
        }
        return (root, query, cb) -> {
            Expression<Integer> anio = root.<Short>get("periodoAnio").as(Integer.class);
            Expression<Integer> mes = root.<Short>get("periodoMes").as(Integer.class);
            Expression<Integer> ordinal = cb.sum(cb.prod(anio, 100), mes);

            var predicado = cb.conjunction();
            if (anioMesDesde != null) {
                predicado = cb.and(predicado, cb.greaterThanOrEqualTo(ordinal, anioMesDesde));
            }
            if (anioMesHasta != null) {
                predicado = cb.and(predicado, cb.lessThanOrEqualTo(ordinal, anioMesHasta));
            }
            return predicado;
        };
    }

    /**
     * Estados que representan facturación efectiva o pendiente de concretarse:
     * {@code PENDIENTE} y {@code FACTURADA}. Excluye {@code PENDIENTE_UF} (sin monto — no se
     * inventan cifras) y {@code ANULADA} (no se factura). Usado por el informe de
     * facturación (informes/) para calcular los totales de "lo que se va a facturar".
     */
    public static Specification<PropuestaFacturacion> facturable() {
        return (root, query, cb) ->
            root.<EstadoPropuesta>get("estado").in(List.of(EstadoPropuesta.PENDIENTE, EstadoPropuesta.FACTURADA));
    }
}
