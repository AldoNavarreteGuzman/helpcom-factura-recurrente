package cl.helpcom.facturacion.facturacion.ciclo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Determina si un proyecto corresponde facturarse en un período (año-mes) dado y, si
 * corresponde, en qué fecha exacta. Lógica pura (arquitectura-tecnica.md §9): no toca base
 * de datos ni ningún otro componente; recibe todo por parámetros.
 *
 * <p>Reglas:
 * <ul>
 *   <li><b>MENSUAL</b>: corresponde todos los meses en su día de facturación, pero el
 *       primer cobro es el mes SIGUIENTE al mes de {@code fecha_inicio} (sin prorratear el
 *       período parcial inicial).</li>
 *   <li><b>ANUAL</b>: corresponde solo en el mes de {@code fecha_inicio} (aniversario),
 *       partiendo desde ese mismo mes/año (a diferencia de MENSUAL, el mes de inicio SÍ se
 *       factura).</li>
 *   <li>Si el día de facturación no existe en el mes (p. ej. 31 en febrero), se usa el
 *       último día del mes.</li>
 *   <li>No corresponde si el proyecto está inactivo, si el período es anterior a su inicio
 *       efectivo, o si la fecha de facturación resultante cae después de
 *       {@code fecha_termino} (cuando el proyecto tiene término).</li>
 * </ul>
 */
@Component
public class ResolutorPeriodo {

    public Optional<LocalDate> resolver(DatosProyectoPeriodo datos, YearMonth periodo) {
        if (!datos.activo()) {
            return Optional.empty();
        }

        YearMonth mesInicio = YearMonth.from(datos.fechaInicio());
        boolean corresponde = switch (datos.periodicidad()) {
            case MENSUAL -> !periodo.isBefore(mesInicio.plusMonths(1));
            case ANUAL -> !periodo.isBefore(mesInicio) && periodo.getMonthValue() == mesInicio.getMonthValue();
        };
        if (!corresponde) {
            return Optional.empty();
        }

        LocalDate fechaFacturacion = resolverFechaEnElMes(periodo, datos.diaFacturacion());

        if (datos.fechaTermino() != null && fechaFacturacion.isAfter(datos.fechaTermino())) {
            return Optional.empty();
        }

        return Optional.of(fechaFacturacion);
    }

    private LocalDate resolverFechaEnElMes(YearMonth periodo, int diaFacturacion) {
        int diaEfectivo = Math.min(diaFacturacion, periodo.lengthOfMonth());
        return periodo.atDay(diaEfectivo);
    }
}
