package cl.helpcom.facturacion.facturacion.ciclo;

import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el ciclo de facturación automáticamente el día 1 de cada mes, en la zona horaria
 * de negocio. Se usa {@code @Scheduled} con cron en vez de Quartz: el ciclo es un único
 * disparo mensual sin necesidad de persistencia de triggers, coordinación entre varias
 * instancias más allá del lock de Redis ya existente, ni reprogramación dinámica — Quartz
 * agregaría complejidad operacional (su propio esquema de tablas, gestión de jobs) sin
 * aportar nada que {@code @Scheduled} no resuelva ya para este caso.
 *
 * <p>Se puede deshabilitar con {@code app.ciclo.programado.habilitado=false} (por defecto
 * {@code true}), útil para entornos de prueba donde no se quiere que el ciclo se dispare solo.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ciclo.programado", name = "habilitado", havingValue = "true", matchIfMissing = true)
public class TareaProgramadaCiclo {

    private static final Logger log = LoggerFactory.getLogger(TareaProgramadaCiclo.class);

    private final ServicioCicloFacturacion servicioCicloFacturacion;
    private final ZoneId zonaHorariaNegocio;

    public TareaProgramadaCiclo(ServicioCicloFacturacion servicioCicloFacturacion, ZoneId zonaHorariaNegocio) {
        this.servicioCicloFacturacion = servicioCicloFacturacion;
        this.zonaHorariaNegocio = zonaHorariaNegocio;
    }

    @Scheduled(cron = "0 0 1 1 * *", zone = "${facturacion.zona-horaria}")
    public void ejecutarCicloAutomatico() {
        LocalDate hoy = LocalDate.now(zonaHorariaNegocio);
        log.info("Disparando la ejecución automática del ciclo de facturación para {}-{}.",
            hoy.getYear(), hoy.getMonthValue());
        servicioCicloFacturacion.ejecutarCiclo(hoy.getYear(), hoy.getMonthValue(), DisparoCiclo.AUTOMATICO);
    }
}
