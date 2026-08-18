package cl.helpcom.facturacion.clientes.repositorio;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.data.jpa.domain.Specification;

/**
 * Composición de filtros dinámicos y opcionales para el listado de clientes. Cada método
 * retorna {@code null} cuando el filtro no aplica; combínalos SIEMPRE con {@link #combinar},
 * nunca encadenando {@code .and(...)} a mano (ver el porqué en
 * {@code PropuestaFacturacionEspecificaciones}).
 */
public final class ClienteEspecificaciones {

    private ClienteEspecificaciones() {
    }

    @SafeVarargs
    public static Specification<Cliente> combinar(Specification<Cliente>... especificaciones) {
        return Specification.allOf(Arrays.stream(especificaciones).filter(Objects::nonNull).toList());
    }

    public static Specification<Cliente> paraEmpresa(Long empresaId) {
        return (root, query, cb) -> cb.equal(root.get("empresa").get("id"), empresaId);
    }

    public static Specification<Cliente> conTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String patron = "%" + texto.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("razonSocial")), patron),
            cb.like(cb.lower(root.get("rut")), patron));
    }

    public static Specification<Cliente> conActivo(Boolean activo) {
        if (activo == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("activo"), activo);
    }
}
