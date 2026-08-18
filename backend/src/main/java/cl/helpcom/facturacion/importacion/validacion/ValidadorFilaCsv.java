package cl.helpcom.facturacion.importacion.validacion;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.comun.util.RutChileno;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.importacion.csv.FilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valida una {@link FilaCsv} completa (modelo-de-datos.md §6): formato de cada columna,
 * existencia y estado del cliente/proyecto referenciados, y consistencia entre
 * {@code fecha_facturacion} y {@code periodo}. También aplica la regla de negocio de
 * idempotencia CSV-vs-CICLO (ver el mensaje de esa validación más abajo): no es una regla de
 * formato, pero vive aquí porque debe evaluarse exactamente igual en previsualizar y en
 * confirmar (arquitectura-tecnica.md §10, estrategia de re-validar en confirmar).
 *
 * <p><b>Decisión — idempotencia CSV vs. CICLO:</b> si la fila referencia un proyecto que ya
 * tiene una propuesta {@code origen=CICLO} para el mismo (proyecto, período), la fila se
 * marca como {@code ERROR} (no {@code ADVERTENCIA}) y no se importa. La sugerencia original
 * era "advertir y no duplicar", pero en este sistema las filas {@code ADVERTENCIA} SÍ se
 * importan (junto con las {@code OK}); para lograr "no duplicar" de verdad la fila debe
 * quedar en el único estado que efectivamente bloquea la importación: {@code ERROR}. No hay
 * restricción de base de datos para este caso (el índice único parcial solo cubre
 * {@code origen='CICLO'}), así que la regla es enteramente de negocio, aplicada aquí.
 */
@Component
public class ValidadorFilaCsv {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern FORMATO_PERIODO = Pattern.compile("^(\\d{4})-(\\d{2})$");

    private final ClienteRepositorio clienteRepositorio;
    private final ProyectoRepositorio proyectoRepositorio;
    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;

    public ValidadorFilaCsv(
        ClienteRepositorio clienteRepositorio,
        ProyectoRepositorio proyectoRepositorio,
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio) {
        this.clienteRepositorio = clienteRepositorio;
        this.proyectoRepositorio = proyectoRepositorio;
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
    }

    public FilaValidada validar(FilaCsv fila, Long empresaId) {
        List<String> mensajes = new ArrayList<>();
        EstadoAcumulado estado = new EstadoAcumulado();

        Cliente cliente = validarCliente(fila, empresaId, mensajes, estado);
        validarDescripcion(fila, mensajes, estado);

        PeriodoResuelto periodoResuelto = validarPeriodo(fila, mensajes, estado);
        LocalDate fechaFacturacion = validarFechaFacturacion(fila, mensajes, estado);
        validarConsistenciaFechaPeriodo(fila, periodoResuelto, fechaFacturacion, mensajes, estado);

        Moneda moneda = validarMoneda(fila, mensajes, estado);
        BigDecimal montoNeto = validarMontoNeto(fila, mensajes, estado);

        Proyecto proyecto = validarProyecto(fila, empresaId, cliente, periodoResuelto, mensajes, estado);

        return new FilaValidada(
            fila, estado.valor(), mensajes, cliente, proyecto,
            periodoResuelto == null ? null : periodoResuelto.anio(),
            periodoResuelto == null ? null : periodoResuelto.mes(),
            fechaFacturacion, moneda, montoNeto);
    }

    private Cliente validarCliente(FilaCsv fila, Long empresaId, List<String> mensajes, EstadoAcumulado estado) {
        Optional<String> rutNormalizado = RutChileno.normalizar(fila.rutCliente());
        if (rutNormalizado.isEmpty()) {
            mensajes.add("El RUT '" + textoOVacio(fila.rutCliente()) + "' no es válido.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        Cliente cliente = clienteRepositorio.findByEmpresaIdAndRut(empresaId, rutNormalizado.get()).orElse(null);
        if (cliente == null) {
            mensajes.add("No existe un cliente con RUT " + rutNormalizado.get() + ".");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        if (!cliente.isActivo()) {
            mensajes.add("El cliente con RUT " + rutNormalizado.get() + " está inactivo.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        return cliente;
    }

    private void validarDescripcion(FilaCsv fila, List<String> mensajes, EstadoAcumulado estado) {
        if (esVacio(fila.descripcion())) {
            mensajes.add("La descripción es obligatoria.");
            estado.escalarA(EstadoFilaCsv.ERROR);
        }
    }

    private PeriodoResuelto validarPeriodo(FilaCsv fila, List<String> mensajes, EstadoAcumulado estado) {
        Matcher coincidencia = FORMATO_PERIODO.matcher(textoOVacio(fila.periodo()));
        if (!coincidencia.matches()) {
            mensajes.add("El período '" + textoOVacio(fila.periodo()) + "' no tiene el formato AAAA-MM.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        int anio = Integer.parseInt(coincidencia.group(1));
        int mes = Integer.parseInt(coincidencia.group(2));
        if (mes < 1 || mes > 12) {
            mensajes.add("El período '" + fila.periodo() + "' tiene un mes inválido.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        return new PeriodoResuelto((short) anio, (short) mes);
    }

    private LocalDate validarFechaFacturacion(FilaCsv fila, List<String> mensajes, EstadoAcumulado estado) {
        try {
            return LocalDate.parse(textoOVacio(fila.fechaFacturacion()), FORMATO_FECHA);
        } catch (DateTimeParseException ex) {
            mensajes.add(
                "La fecha de facturación '" + textoOVacio(fila.fechaFacturacion()) + "' no tiene el formato DD-MM-AAAA.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
    }

    private void validarConsistenciaFechaPeriodo(
        FilaCsv fila, PeriodoResuelto periodo, LocalDate fechaFacturacion, List<String> mensajes, EstadoAcumulado estado) {
        if (periodo == null || fechaFacturacion == null) {
            return;
        }
        YearMonth periodoFecha = YearMonth.from(fechaFacturacion);
        if (periodoFecha.getYear() != periodo.anio() || periodoFecha.getMonthValue() != periodo.mes()) {
            mensajes.add("La fecha de facturación (" + fila.fechaFacturacion() + ") no coincide con el período ("
                + fila.periodo() + ").");
            estado.escalarA(EstadoFilaCsv.ADVERTENCIA);
        }
    }

    private Moneda validarMoneda(FilaCsv fila, List<String> mensajes, EstadoAcumulado estado) {
        String monedaBruta = textoOVacio(fila.moneda()).trim().toUpperCase();
        if (monedaBruta.equals("UF")) {
            return Moneda.UF;
        }
        if (monedaBruta.equals("CLP")) {
            return Moneda.CLP;
        }
        mensajes.add("La moneda '" + textoOVacio(fila.moneda()) + "' debe ser UF o CLP.");
        estado.escalarA(EstadoFilaCsv.ERROR);
        return null;
    }

    private BigDecimal validarMontoNeto(FilaCsv fila, List<String> mensajes, EstadoAcumulado estado) {
        BigDecimal montoNeto;
        try {
            montoNeto = new BigDecimal(textoOVacio(fila.montoNeto()).trim());
        } catch (NumberFormatException ex) {
            mensajes.add("El monto neto '" + textoOVacio(fila.montoNeto()) + "' no es un número válido.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        if (montoNeto.compareTo(BigDecimal.ZERO) <= 0) {
            mensajes.add("El monto neto debe ser mayor que cero.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        return montoNeto;
    }

    private Proyecto validarProyecto(
        FilaCsv fila, Long empresaId, Cliente cliente, PeriodoResuelto periodo, List<String> mensajes, EstadoAcumulado estado) {
        if (esVacio(fila.codigoProyecto())) {
            return null;
        }
        String codigo = fila.codigoProyecto().trim();
        Proyecto proyecto = proyectoRepositorio.findByEmpresaIdAndCodigo(empresaId, codigo).orElse(null);
        if (proyecto == null) {
            mensajes.add("No existe un proyecto con código '" + codigo + "'.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        if (cliente != null && !proyecto.getCliente().getId().equals(cliente.getId())) {
            mensajes.add("El proyecto '" + codigo + "' pertenece a otro cliente.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        if (periodo != null && propuestaFacturacionRepositorio.existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
                proyecto.getId(), periodo.anio(), periodo.mes(), OrigenPropuesta.CICLO)) {
            mensajes.add("Ya existe una propuesta del ciclo para el proyecto '" + codigo + "' en el período "
                + fila.periodo() + "; no se importa para evitar duplicarla.");
            estado.escalarA(EstadoFilaCsv.ERROR);
            return null;
        }
        return proyecto;
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private String textoOVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private record PeriodoResuelto(Short anio, Short mes) {
    }

    /**
     * Acumula el peor estado visto (ERROR > ADVERTENCIA > OK) a medida que se ejecutan las
     * validaciones, sin permitir que una validación posterior "mejore" el estado.
     */
    private static final class EstadoAcumulado {
        private EstadoFilaCsv estado = EstadoFilaCsv.OK;

        void escalarA(EstadoFilaCsv nuevo) {
            if (peso(nuevo) > peso(estado)) {
                estado = nuevo;
            }
        }

        EstadoFilaCsv valor() {
            return estado;
        }

        private int peso(EstadoFilaCsv estado) {
            return switch (estado) {
                case OK -> 0;
                case ADVERTENCIA -> 1;
                case ERROR -> 2;
            };
        }
    }
}
