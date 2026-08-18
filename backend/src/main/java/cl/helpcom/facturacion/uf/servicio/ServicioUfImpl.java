package cl.helpcom.facturacion.uf.servicio;

import cl.helpcom.facturacion.uf.cache.CacheUf;
import cl.helpcom.facturacion.uf.dominio.ValorUf;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.fuente.FuenteUf;
import cl.helpcom.facturacion.uf.repositorio.ValorUfRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orden de resolución (ver arquitectura-tecnica.md §8): caché Redis → persistencia
 * ({@code valor_uf}) → fuente externa (mindicador.cl, persistiendo y cacheando el
 * resultado). Si ninguna vía entrega un valor, lanza {@link ValorUfNoDisponibleException}
 * en vez de retornar {@code null}.
 */
@Service
public class ServicioUfImpl implements ServicioUf {

    private static final Logger log = LoggerFactory.getLogger(ServicioUfImpl.class);
    private static final String FUENTE_MINDICADOR = "mindicador.cl";

    private final CacheUf cacheUf;
    private final ValorUfRepositorio valorUfRepositorio;
    private final FuenteUf fuenteUf;

    public ServicioUfImpl(CacheUf cacheUf, ValorUfRepositorio valorUfRepositorio, FuenteUf fuenteUf) {
        this.cacheUf = cacheUf;
        this.valorUfRepositorio = valorUfRepositorio;
        this.fuenteUf = fuenteUf;
    }

    /**
     * {@code noRollbackFor} es necesario porque {@code ValorUfNoDisponibleException} es un
     * resultado ESPERADO, no un error: {@code ArmadorPropuesta} la captura y degrada la
     * propuesta a {@code PENDIENTE_UF} en vez de propagarla (arquitectura-tecnica.md §9).
     * Pero este método corre dentro de la MISMA transacción {@code REQUIRES_NEW} por
     * proyecto que abre {@code ServicioCicloFacturacion} (propagación por defecto, se une a
     * la del llamador) — sin este atributo, el aspecto transaccional de ESTE método marca esa
     * transacción compartida como {@code rollback-only} en cuanto la excepción la atraviesa,
     * aunque el llamador la atrape y siga normalmente. El resultado (visto solo contra
     * Postgres real, nunca con mocks/H2): al llegar al commit, {@code TransactionTemplate}
     * revienta con {@code UnexpectedRollbackException} — no capturado en ninguna parte del
     * camino feliz — y el proyecto queda contado como {@code ERROR} en vez de
     * {@code PENDIENTE_UF}, violando el contrato documentado de "el ciclo continúa, nunca se
     * inventan cifras".
     */
    @Override
    @Transactional(noRollbackFor = ValorUfNoDisponibleException.class)
    public BigDecimal obtenerValorUf(LocalDate fecha) {
        Optional<BigDecimal> valorEnCache = cacheUf.obtener(fecha);
        if (valorEnCache.isPresent()) {
            return valorEnCache.get();
        }

        Optional<BigDecimal> valorPersistido = valorUfRepositorio.findById(fecha).map(ValorUf::getValor);
        if (valorPersistido.isPresent()) {
            BigDecimal valor = valorPersistido.get();
            cacheUf.guardar(fecha, valor);
            return valor;
        }

        Optional<BigDecimal> valorExterno = fuenteUf.consultarUf(fecha);
        if (valorExterno.isPresent()) {
            BigDecimal valor = valorExterno.get();
            persistir(fecha, valor);
            cacheUf.guardar(fecha, valor);
            return valor;
        }

        log.warn("No se pudo obtener el valor UF para la fecha {} por ninguna vía (caché, persistencia ni fuente externa).", fecha);
        throw new ValorUfNoDisponibleException(fecha);
    }

    private void persistir(LocalDate fecha, BigDecimal valor) {
        ValorUf valorUf = new ValorUf();
        valorUf.setFecha(fecha);
        valorUf.setValor(valor);
        valorUf.setFuente(FUENTE_MINDICADOR);
        valorUf.setRegistradoEn(OffsetDateTime.now());
        valorUfRepositorio.save(valorUf);
    }
}
