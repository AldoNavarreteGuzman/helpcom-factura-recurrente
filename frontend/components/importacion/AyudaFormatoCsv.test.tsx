import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { AyudaFormatoCsv } from "./AyudaFormatoCsv";

const mockDescargar = vi.fn();

vi.mock("@/lib/archivos", () => ({
  descargarArchivoEnNavegador: (...args: unknown[]) => mockDescargar(...args),
}));

describe("AyudaFormatoCsv", () => {
  it("el botón descarga una plantilla con los encabezados correctos", async () => {
    mockDescargar.mockReset();
    const usuario = userEvent.setup();
    render(<AyudaFormatoCsv />);

    await usuario.click(screen.getByRole("button", { name: "Descargar plantilla CSV" }));

    expect(mockDescargar).toHaveBeenCalledTimes(1);
    const [{ blob, nombreArchivo }] = mockDescargar.mock.calls[0];
    expect(nombreArchivo).toBe("plantilla-importacion.csv");

    const texto: string = await blob.text();
    const primeraLinea = texto.replace(/^﻿/, "").split("\r\n")[0];
    expect(primeraLinea).toBe(
      "rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion",
    );
  });
});
