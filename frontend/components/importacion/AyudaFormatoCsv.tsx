"use client";

import { Boton } from "@/components/ui/Boton";
import { descargarArchivoEnNavegador } from "@/lib/archivos";
import { generarPlantillaCsv } from "@/lib/csv";

const COLUMNAS = [
  {
    columna: "rut_cliente",
    obligatoria: true,
    formato: "99999999-9",
    descripcion: "Identifica al cliente (debe existir).",
  },
  {
    columna: "codigo_proyecto",
    obligatoria: false,
    formato: "texto",
    descripcion: "Vincula a un proyecto registrado, si aplica.",
  },
  {
    columna: "descripcion",
    obligatoria: true,
    formato: "texto",
    descripcion: "Detalle de lo que se factura.",
  },
  {
    columna: "periodo",
    obligatoria: true,
    formato: "AAAA-MM",
    descripcion: "Período del ciclo al que se asocia.",
  },
  {
    columna: "fecha_facturacion",
    obligatoria: true,
    formato: "DD-MM-AAAA",
    descripcion: "Fecha de facturación (define la UF a usar).",
  },
  {
    columna: "moneda",
    obligatoria: true,
    formato: "UF / CLP",
    descripcion: "Moneda del monto neto.",
  },
  {
    columna: "monto_neto",
    obligatoria: true,
    formato: "número (neto)",
    descripcion: "Valor neto en la moneda indicada.",
  },
  { columna: "observacion", obligatoria: false, formato: "texto", descripcion: "Libre." },
] as const;

/** Ayuda de formato + plantilla descargable (modelo-de-datos.md §6), pensada para quien viene de Excel. */
export function AyudaFormatoCsv() {
  function alDescargarPlantilla() {
    descargarArchivoEnNavegador({
      blob: generarPlantillaCsv(),
      nombreArchivo: "plantilla-importacion.csv",
    });
  }

  return (
    <div className="space-y-3 rounded-md border border-linea bg-fondo p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-tinta">Formato del CSV</h2>
        <Boton variante="secundario" onClick={alDescargarPlantilla}>
          Descargar plantilla CSV
        </Boton>
      </div>
      <p className="text-sm text-sutil">
        Archivo UTF-8, primera fila de encabezados, separador de columnas{" "}
        <code className="rounded bg-linea-2 px-1">;</code> (punto y coma), separador decimal{" "}
        <code className="rounded bg-linea-2 px-1">.</code> (punto), sin separador de miles.
      </p>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-xs text-texto">
          <thead>
            <tr className="border-b border-linea text-sutil">
              <th className="py-1 pr-4">Columna</th>
              <th className="py-1 pr-4">Obligatoria</th>
              <th className="py-1 pr-4">Formato</th>
              <th className="py-1">Descripción</th>
            </tr>
          </thead>
          <tbody>
            {COLUMNAS.map((columna) => (
              <tr key={columna.columna} className="border-b border-linea-2 last:border-0">
                <td className="py-1 pr-4 font-mono">{columna.columna}</td>
                <td className="py-1 pr-4">{columna.obligatoria ? "Sí" : "No"}</td>
                <td className="py-1 pr-4">{columna.formato}</td>
                <td className="py-1">{columna.descripcion}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
