/**
 * Validación y normalización de RUT chileno (módulo 11) — puerto exacto de
 * `RutChileno` del backend (`comun/util/RutChileno.java`) para dar feedback inmediato en el
 * formulario. El backend vuelve a validar y normalizar igual; esto es solo UX.
 */

/**
 * Acepta el RUT en cualquier formato habitual (con o sin puntos, con o sin guion, con
 * espacios) y retorna el formato canónico `NNNNNNNN-D` si el dígito verificador es correcto;
 * `null` si el formato o el dígito verificador son inválidos.
 */
export function normalizarRut(rutBruto: string): string | null {
  const limpio = rutBruto.replace(/[^0-9kK]/g, "").toUpperCase();
  if (limpio.length < 2) {
    return null;
  }
  const cuerpo = limpio.slice(0, -1);
  const digitoVerificador = limpio.slice(-1);
  if (cuerpo.length === 0 || !/^\d+$/.test(cuerpo)) {
    return null;
  }
  if (digitoVerificador !== calcularDigitoVerificador(cuerpo)) {
    return null;
  }
  return `${cuerpo}-${digitoVerificador}`;
}

export function esRutValido(rutBruto: string): boolean {
  return normalizarRut(rutBruto) !== null;
}

/** Formato legible con puntos de miles: `12345678-9` → `12.345.678-9`. */
export function formatearRut(rutCanonico: string): string {
  const separador = rutCanonico.lastIndexOf("-");
  if (separador === -1) {
    return rutCanonico;
  }
  const cuerpo = rutCanonico.slice(0, separador);
  const digitoVerificador = rutCanonico.slice(separador + 1);
  const cuerpoConPuntos = cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${cuerpoConPuntos}-${digitoVerificador}`;
}

function calcularDigitoVerificador(cuerpo: string): string {
  let suma = 0;
  let multiplicador = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * multiplicador;
    multiplicador = multiplicador === 7 ? 2 : multiplicador + 1;
  }
  const resto = 11 - (suma % 11);
  if (resto === 11) {
    return "0";
  }
  if (resto === 10) {
    return "K";
  }
  return String(resto);
}
