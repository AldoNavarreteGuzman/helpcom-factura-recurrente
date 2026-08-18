package cl.helpcom.facturacion.proyectos.repositorio;

import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.proyectos.dominio.Periodicidad;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composición de filtros dinámicos y opcionales para el listado de proyectos. Cada método
 * retorna {@code null} cuando el filtro no aplica; combínalos SIEMPRE con {@link #combinar},
 * nunca encadenando {@code .and(...)} a mano (ver el porqué en
 * {@code PropuestaFacturacionEspecificaciones}).
 */
public final class ProyectoEspecificaciones {

    private ProyectoEspecificaciones() {
    }

    @SafeVarargs
    public static Specification<Proyecto> combinar(Specification<Proyecto>... especificaciones) {
        return Specification.allOf(Arrays.stream(especificaciones).filter(Objects::nonNull).toList());
    }

    public static Specification<Proyecto> paraEmpresa(Long empresaId) {
        return (root, query, cb) -> cb.equal(root.get("empresa").get("id"), empresaId);
    }

    public static Specification<Proyecto> conCliente(Long clienteId) {
        if (clienteId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("cliente").get("id"), clienteId);
    }

    public static Specification<Proyecto> conPeriodicidad(Periodicidad periodicidad) {
        if (periodicidad == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("periodicidad"), periodicidad);
    }

    public static Specification<Proyecto> conMoneda(Moneda moneda) {
        if (moneda == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("monedaPrecio"), moneda);
    }

    public static Specification<Proyecto> conActivo(Boolean activo) {
        if (activo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("activo"), activo);
    }
}
