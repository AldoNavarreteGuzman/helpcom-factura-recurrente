package cl.helpcom.facturacion.informes.dto;

import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import java.util.List;

/**
 * Filtros del informe de facturación (todos opcionales, se combinan con AND).
 *
 * <p>{@code periodoAnio}/{@code periodoMes} filtran un período exacto. {@code anioMesDesde}/
 * {@code anioMesHasta} filtran un rango de períodos para informes multi-período, codificados
 * como {@code AAAAMM} (p. ej. 202601 para enero de 2026), ambos límites inclusive. Ambos
 * mecanismos pueden combinarse (se intersectan), aunque lo usual es usar uno u otro.
 */
public record FiltroInformeFacturacion(
    Integer periodoAnio,
    Integer periodoMes,
    Integer anioMesDesde,
    Integer anioMesHasta,
    Long clienteId,
    List<EstadoPropuesta> estados,
    OrigenPropuesta origen,
    Boolean facturada
) {
}
