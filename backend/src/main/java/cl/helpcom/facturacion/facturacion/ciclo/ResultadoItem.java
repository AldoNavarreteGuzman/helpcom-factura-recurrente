package cl.helpcom.facturacion.facturacion.ciclo;

/** Desenlace de procesar un proyecto dentro del ciclo. Uso interno, no se persiste. */
enum ResultadoItem {
    GENERADA,
    PENDIENTE_UF,
    YA_EXISTIA,
    OMITIDA,
    ERROR
}
