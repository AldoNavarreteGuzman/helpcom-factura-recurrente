package cl.helpcom.facturacion.comun.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Envoltorio explícito y estable para listados paginados, en vez de serializar
 * {@link Page} directamente (su forma JSON no es un contrato propio y puede cambiar entre
 * versiones de Spring Data).
 */
public record PaginaRespuestaDto<T>(List<T> contenido, long total, int pagina, int tamano) {

    public static <E, T> PaginaRespuestaDto<T> desde(Page<E> pagina, Function<E, T> mapeo) {
        return new PaginaRespuestaDto<>(
            pagina.getContent().stream().map(mapeo).toList(),
            pagina.getTotalElements(),
            pagina.getNumber(),
            pagina.getSize());
    }
}
