package cl.helpcom.facturacion.comun.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Aplica un orden por defecto a un listado paginado cuando quien llama no pidió ninguno
 * (estandares-de-codigo.md §3.8: los listados de eventos/registros temporales ordenan por su
 * fecha/timestamp — o, en su defecto, período — descendente por defecto, más reciente
 * primero, sin que el cliente tenga que pedir {@code sort} explícito). Si el {@link Pageable}
 * ya trae un orden (el cliente pidió {@code sort=...}), se respeta tal cual: este método
 * nunca pisa una preferencia explícita.
 */
public final class OrdenPorDefecto {

    private OrdenPorDefecto() {
    }

    public static Pageable aplicar(Pageable pageable, Sort ordenPorDefecto) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ordenPorDefecto);
    }
}
