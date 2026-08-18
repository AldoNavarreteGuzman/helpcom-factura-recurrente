import { describe, expect, it } from "vitest";
import { generarPlantillaCsv } from "./csv";

const BOM_UTF8 = "﻿";

describe("generarPlantillaCsv", () => {
  it("genera un CSV con los encabezados correctos y una fila de ejemplo", async () => {
    const blob = generarPlantillaCsv();
    const texto = (await blob.text()).replace(new RegExp(`^${BOM_UTF8}`), "");
    const lineas = texto.trim().split("\r\n");

    expect(lineas[0]).toBe(
      "rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion",
    );
    expect(lineas).toHaveLength(2);
    expect(lineas[1].split(";")).toHaveLength(8);
  });

  it("incluye el BOM UTF-8 al inicio, para que Excel muestre bien los acentos", async () => {
    // `Blob.text()` decodifica con TextDecoder, que por defecto DESCARTA el BOM del
    // resultado (ignoreBOM: false) — hay que mirar los bytes crudos para verificar que el
    // BOM realmente está en el archivo generado.
    const blob = generarPlantillaCsv();
    const bytes = new Uint8Array(await blob.arrayBuffer());
    expect([bytes[0], bytes[1], bytes[2]]).toEqual([0xef, 0xbb, 0xbf]);
  });
});
