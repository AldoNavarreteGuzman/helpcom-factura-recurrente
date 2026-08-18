import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TablaPreviewImportacion } from "./TablaPreviewImportacion";
import type { ImportacionPreview, ImportacionPreviewFila } from "@/types/importacionCsv";

/**
 * Bug real (docs/deuda-tecnica.md): toda la API omite por completo un campo `null` en vez de
 * mandarlo como `"campo":null` (`jackson.default-property-inclusion: non_null`), así que tras
 * `JSON.parse` una fila `ERROR` real trae `netoClp`/`ivaClp`/`totalClp` como `undefined`, no
 * `null` — un chequeo `=== null` no lo detecta. Esta fila reproduce EXACTAMENTE esa forma
 * (los tres campos omitidos del objeto, como haría `JSON.parse` de la respuesta real), no una
 * versión simplificada con `null` explícito.
 */
const FILA_ERROR_SIN_MONTOS: ImportacionPreviewFila = {
  numeroFila: 2,
  estado: "ERROR",
  mensajes: ["El RUT '76543210-9' no es válido."],
  rutCliente: "76543210-9",
  codigoProyecto: null,
  descripcion: "Servicio mantención enero",
  periodo: "2026-01",
  fechaFacturacion: "15-01-2026",
  moneda: "UF",
  montoNeto: "12.5",
  observacion: "Calculado en Excel",
  netoClp: undefined,
  ivaClp: undefined,
  totalClp: undefined,
};

const FILA_OK: ImportacionPreviewFila = {
  numeroFila: 3,
  estado: "OK",
  mensajes: [],
  rutCliente: "77111222-6",
  codigoProyecto: null,
  descripcion: "Soporte anual",
  periodo: "2026-01",
  fechaFacturacion: "20-01-2026",
  moneda: "CLP",
  montoNeto: "850000",
  observacion: null,
  netoClp: 850000,
  ivaClp: 161500,
  totalClp: 1011500,
};

function previewCon(filas: ImportacionPreviewFila[]): ImportacionPreview {
  return {
    resumen: {
      totalFilas: filas.length,
      filasOk: filas.filter((f) => f.estado === "OK").length,
      filasAdvertencia: filas.filter((f) => f.estado === "ADVERTENCIA").length,
      filasError: filas.filter((f) => f.estado === "ERROR").length,
    },
    filas,
  };
}

describe("TablaPreviewImportacion", () => {
  it("una fila ERROR con montos ausentes (undefined, como la respuesta real) muestra guiones, nunca $NaN", async () => {
    render(<TablaPreviewImportacion preview={previewCon([FILA_ERROR_SIN_MONTOS])} />);

    const fila = (await screen.findAllByRole("row")).find(
      (f) => within(f).queryByText(/RUT.*no es válido/) !== null,
    );
    if (!fila) {
      throw new Error("No se encontró la fila de error");
    }

    expect(within(fila).queryByText(/NaN/)).not.toBeInTheDocument();
    // Tres columnas con montos ausentes (Neto, IVA, Total) deben mostrar "—".
    expect(within(fila).getAllByText("—").length).toBeGreaterThanOrEqual(3);
  });

  it("una fila OK con montos reales los formatea con normalidad", async () => {
    render(<TablaPreviewImportacion preview={previewCon([FILA_OK])} />);

    const fila = (await screen.findAllByRole("row")).find(
      (f) => within(f).queryByText("Soporte anual") !== null,
    );
    if (!fila) {
      throw new Error("No se encontró la fila OK");
    }

    expect(within(fila).getByText("$1.011.500")).toBeInTheDocument();
  });
});
