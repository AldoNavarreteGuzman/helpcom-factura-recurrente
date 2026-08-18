export type Moneda = "UF" | "CLP";

export type Periodicidad = "MENSUAL" | "ANUAL";

export type TipoAcuerdo = "DESCUENTO_PORCENTAJE" | "DESCUENTO_MONTO" | "PRECIO_PACTADO";

export type OrigenPropuesta = "CICLO" | "CSV";

export type EstadoPropuesta = "PENDIENTE" | "PENDIENTE_UF" | "FACTURADA" | "ANULADA";

export type DisparoCiclo = "AUTOMATICO" | "MANUAL";

export type EstadoEjecucionCiclo = "EXITOSA" | "CON_ADVERTENCIAS" | "ERROR";

export type EstadoFilaCsv = "OK" | "ADVERTENCIA" | "ERROR";

export type EstadoImportacionCsv = "PROCESADA" | "PARCIAL" | "RECHAZADA";
