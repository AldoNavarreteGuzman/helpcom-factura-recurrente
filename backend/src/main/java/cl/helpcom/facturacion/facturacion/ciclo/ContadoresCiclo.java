package cl.helpcom.facturacion.facturacion.ciclo;

/**
 * Contadores acumulados al recorrer los proyectos de un ciclo. {@code erroresItem} no tiene
 * columna propia en {@code ejecucion_ciclo}; se usa solo para decidir el estado final
 * (promueve a {@code CON_ADVERTENCIAS} si hubo fallas puntuales) y para el texto de
 * {@code observacion}.
 */
record ContadoresCiclo(int generadas, int pendientesUf, int erroresItem) {

    static ContadoresCiclo vacio() {
        return new ContadoresCiclo(0, 0, 0);
    }

    ContadoresCiclo conGenerada() {
        return new ContadoresCiclo(generadas + 1, pendientesUf, erroresItem);
    }

    ContadoresCiclo conPendienteUf() {
        return new ContadoresCiclo(generadas + 1, pendientesUf + 1, erroresItem);
    }

    ContadoresCiclo conErrorItem() {
        return new ContadoresCiclo(generadas, pendientesUf, erroresItem + 1);
    }
}
