import { describe, expect, it } from "vitest";
import { construirQueryString } from "./query";

describe("construirQueryString", () => {
  it("omite valores vacíos, undefined y null", () => {
    expect(construirQueryString({ a: "", b: undefined, c: null, d: "x" })).toBe("?d=x");
  });

  it("serializa un arreglo como parámetros repetidos", () => {
    expect(construirQueryString({ estados: ["PENDIENTE", "FACTURADA"] })).toBe(
      "?estados=PENDIENTE&estados=FACTURADA",
    );
  });

  it("omite elementos vacíos dentro de un arreglo sin omitir el resto", () => {
    expect(construirQueryString({ estados: ["PENDIENTE", "", "FACTURADA"] })).toBe(
      "?estados=PENDIENTE&estados=FACTURADA",
    );
  });

  it("retorna cadena vacía cuando no hay parámetros", () => {
    expect(construirQueryString({ a: undefined, b: [] })).toBe("");
  });
});
