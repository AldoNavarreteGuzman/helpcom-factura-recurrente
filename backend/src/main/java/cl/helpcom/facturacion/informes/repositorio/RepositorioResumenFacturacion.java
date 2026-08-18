package cl.helpcom.facturacion.informes.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/**
 * Agregaciones SQL (SUM/COUNT con GROUP BY) para el informe de facturación
 * (informes/): el resumen y el desglose por cliente se calculan en la base de datos, nunca
 * cargando las propuestas en memoria para sumarlas en Java.
 *
 * <p>Reutiliza directamente las {@link Specification} de
 * {@code PropuestaFacturacionEspecificaciones} — las mismas que arma el listado paginado del
 * detalle — para que el resumen y el detalle respondan siempre a los mismos filtros. Por eso
 * usa {@link EntityManager} en vez de un método derivado de Spring Data: una consulta
 * agregada con {@code GROUP BY} que además acepta filtros dinámicos no es expresable con
 * {@code JpaSpecificationExecutor.findAll}, que está pensado para listar entidades, no para
 * proyectar agregados.
 */
@Repository
public class RepositorioResumenFacturacion {

    private final EntityManager entityManager;

    public RepositorioResumenFacturacion(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<FilaResumenPorEstado> agregarPorEstado(Specification<PropuestaFacturacion> especificacion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FilaResumenPorEstado> consulta = cb.createQuery(FilaResumenPorEstado.class);
        Root<PropuestaFacturacion> raiz = consulta.from(PropuestaFacturacion.class);

        consulta.select(cb.construct(
            FilaResumenPorEstado.class,
            raiz.<EstadoPropuesta>get("estado"),
            cb.count(raiz),
            cb.sum(raiz.<BigDecimal>get("netoClp")),
            cb.sum(raiz.<BigDecimal>get("ivaClp")),
            cb.sum(raiz.<BigDecimal>get("totalClp"))));

        Predicate predicado = especificacion.toPredicate(raiz, consulta, cb);
        if (predicado != null) {
            consulta.where(predicado);
        }
        consulta.groupBy(raiz.get("estado"));

        return entityManager.createQuery(consulta).getResultList();
    }

    public List<FilaResumenPorCliente> agregarPorCliente(Specification<PropuestaFacturacion> especificacion) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<FilaResumenPorCliente> consulta = cb.createQuery(FilaResumenPorCliente.class);
        Root<PropuestaFacturacion> raiz = consulta.from(PropuestaFacturacion.class);
        Join<Object, Object> cliente = raiz.join("cliente");

        consulta.select(cb.construct(
            FilaResumenPorCliente.class,
            cliente.<Long>get("id"),
            cliente.<String>get("razonSocial"),
            cb.count(raiz),
            cb.sum(raiz.<BigDecimal>get("netoClp")),
            cb.sum(raiz.<BigDecimal>get("ivaClp")),
            cb.sum(raiz.<BigDecimal>get("totalClp"))));

        Predicate predicado = especificacion.toPredicate(raiz, consulta, cb);
        if (predicado != null) {
            consulta.where(predicado);
        }
        consulta.groupBy(cliente.get("id"), cliente.get("razonSocial"));
        consulta.orderBy(cb.desc(cb.sum(raiz.<BigDecimal>get("totalClp"))));

        return entityManager.createQuery(consulta).getResultList();
    }
}
