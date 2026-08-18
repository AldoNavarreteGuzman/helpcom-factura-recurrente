const ENCABEZADOS_PLANTILLA_CSV = [
  "rut_cliente",
  "codigo_proyecto",
  "descripcion",
  "periodo",
  "fecha_facturacion",
  "moneda",
  "monto_neto",
  "observacion",
];

// RUT con dígito verificador VÁLIDO a propósito (bug real: antes era 76543210-9, dígito
// incorrecto — módulo 11 da 3, no 9 — lo que hacía fallar la fila de ejemplo con "El RUT no
// es válido" apenas se descargaba la plantilla sin editar). Sigue sin corresponder a ningún
// cliente real sembrado — es intencional: la plantilla no depende de qué exista en cada
// entorno; quien la usa reemplaza esta fila con sus propios datos.
const FILA_EJEMPLO_PLANTILLA_CSV = [
  "76543210-3",
  "",
  "Servicio mantención enero",
  "2026-01",
  "15-01-2026",
  "UF",
  "12.5",
  "Calculado en Excel",
];

/**
 * Genera en el cliente el CSV de ejemplo (modelo-de-datos.md §6: separador `;`, decimal `.`,
 * UTF-8, primera fila de encabezados). El BOM inicial es a propósito: Excel —el origen típico
 * de estos datos, según la tarea— lo necesita para mostrar bien los acentos si el usuario abre
 * el archivo ahí antes de reimportarlo; el propio `LectorCsv` del backend ya lo tolera y lo
 * descarta al leer, así que no rompe nada del lado del servidor.
 */
export function generarPlantillaCsv(): Blob {
  const lineas = [ENCABEZADOS_PLANTILLA_CSV.join(";"), FILA_EJEMPLO_PLANTILLA_CSV.join(";")];
  return new Blob(["﻿" + lineas.join("\r\n") + "\r\n"], { type: "text/csv;charset=utf-8" });
}
