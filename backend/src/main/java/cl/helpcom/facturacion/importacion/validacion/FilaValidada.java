package cl.helpcom.facturacion.importacion.validacion;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.importacion.csv.FilaCsv;
import cl.helpcom.facturacion.importacion.dominio.EstadoFilaCsv;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de validar una {@link FilaCsv}: su estado, los mensajes que explican ese estado,
 * y los datos ya resueltos y tipados (cliente, proyecto, período, fecha, moneda, monto) que
 * necesita {@code ArmadorPropuesta} para calcular. Los campos resueltos son {@code null}
 * cuando no se pudieron parsear o resolver (ver los mensajes para saber por qué).
 */
public record FilaValidada(
    FilaCsv fila,
    EstadoFilaCsv estado,
    List<String> mensajes,
    Cliente cliente,
    Proyecto proyecto,
    Short periodoAnio,
    Short periodoMes,
    LocalDate fechaFacturacion,
    Moneda moneda,
    BigDecimal montoNeto
) {

    public boolean esValida() {
        return estado != EstadoFilaCsv.ERROR;
    }
}
