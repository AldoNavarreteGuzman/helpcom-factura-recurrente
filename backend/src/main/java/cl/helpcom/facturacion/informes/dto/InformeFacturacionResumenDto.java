package cl.helpcom.facturacion.informes.dto;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Totales y desgloses de las propuestas que cumplen los filtros del informe.
 *
 * <p><b>Política de totales (decisión de diseño):</b> {@code netoClp}/{@code ivaClp}/
 * {@code totalClp} son la suma de SOLO las propuestas en estado {@code PENDIENTE} o
 * {@code FACTURADA} — "lo que efectivamente se va a facturar o ya se facturó". Se EXCLUYEN
 * por completo (no se suman ni en 0):
 * <ul>
 *   <li>{@code PENDIENTE_UF}: no tiene un monto real todavía (queda en 0 hasta que se pueda
 *       recalcular con la UF disponible); sumarlas —aunque sea en 0— no cambiaría el total,
 *       pero incluir su CANTIDAD junto a los totales sin aclarar que no aportan monto haría
 *       que el informe se leyera como si esas propuestas ya estuvieran contempladas. Por eso
 *       su cantidad se expone aparte en {@code cantidadPendienteUf}, explícitamente.</li>
 *   <li>{@code ANULADA}: una propuesta anulada no se factura, así que no es "lo que se va a
 *       facturar" bajo ninguna definición razonable.</li>
 * </ul>
 * {@code cantidadPorEstado} sí incluye los 4 estados (con 0 si no hay propuestas en ese
 * estado dentro de los filtros aplicados), para que el desglose sea completo y transparente
 * aunque los totales no los sumen a todos.
 */
public record InformeFacturacionResumenDto(
    long cantidadTotal,
    Map<EstadoPropuesta, Long> cantidadPorEstado,
    long cantidadPendienteUf,
    BigDecimal netoClp,
    BigDecimal ivaClp,
    BigDecimal totalClp,
    List<InformeFacturacionClienteResumenDto> porCliente
) {
}
