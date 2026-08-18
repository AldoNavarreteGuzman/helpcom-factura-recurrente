package cl.helpcom.facturacion.comun.util;

import java.util.Optional;

/**
 * Validación y normalización de RUT chileno (con dígito verificador, módulo 11).
 * Formato canónico usado en todo el sistema: {@code NNNNNNNN-D} (sin puntos, sin espacios,
 * verificador en mayúscula si es 'K'), el mismo que espera la columna {@code rut_cliente}
 * del CSV de importación (modelo-de-datos.md §6).
 */
public final class RutChileno {

    private RutChileno() {
    }

    /**
     * Acepta el RUT en cualquier formato de entrada habitual (con o sin puntos, con o sin
     * guion, con espacios) y retorna el formato canónico si el dígito verificador es
     * correcto; {@link Optional#empty()} si el formato o el dígito verificador son inválidos.
     */
    public static Optional<String> normalizar(String rutBruto) {
        if (rutBruto == null) {
            return Optional.empty();
        }
        String limpio = rutBruto.replaceAll("[^0-9kK]", "").toUpperCase();
        if (limpio.length() < 2) {
            return Optional.empty();
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        char digitoVerificador = limpio.charAt(limpio.length() - 1);
        if (cuerpo.isEmpty() || !cuerpo.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        if (digitoVerificador != calcularDigitoVerificador(cuerpo)) {
            return Optional.empty();
        }
        return Optional.of(cuerpo + "-" + digitoVerificador);
    }

    public static boolean esValido(String rutBruto) {
        return normalizar(rutBruto).isPresent();
    }

    private static char calcularDigitoVerificador(String cuerpo) {
        int suma = 0;
        int multiplicador = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * multiplicador;
            multiplicador = multiplicador == 7 ? 2 : multiplicador + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) {
            return '0';
        }
        if (resto == 10) {
            return 'K';
        }
        return Character.forDigit(resto, 10);
    }
}
