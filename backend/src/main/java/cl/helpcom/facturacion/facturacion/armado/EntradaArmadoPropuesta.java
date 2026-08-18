package cl.helpcom.facturacion.facturacion.armado;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.comun.dominio.Moneda;
import cl.helpcom.facturacion.empresa.dominio.Empresa;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Todo lo que {@link ArmadorPropuesta} necesita para construir una {@code PropuestaFacturacion}
 * a partir de una fuente de datos (un proyecto del ciclo, o una fila del CSV de importación).
 *
 * <p>{@code proyecto} es {@code null} cuando la propuesta no está asociada a ningún proyecto
 * registrado (fila CSV sin {@code codigo_proyecto}); en ese caso no se resuelve ningún acuerdo
 * de precio. {@code precioBaseNeto}/{@code monedaPrecio}/{@code descripcion} se reciben por
 * separado del proyecto (no se leen de {@code proyecto}) porque en la importación CSV el monto
 * y la moneda vienen de la fila, no del precio configurado del proyecto — aun cuando la fila
 * referencie un proyecto existente.
 */
public record EntradaArmadoPropuesta(
    Empresa empresa,
    Cliente cliente,
    Proyecto proyecto,
    OrigenPropuesta origen,
    Short periodoAnio,
    Short periodoMes,
    LocalDate fechaFacturacion,
    String descripcion,
    BigDecimal precioBaseNeto,
    Moneda monedaPrecio
) {
}
