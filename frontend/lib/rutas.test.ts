import { describe, expect, it } from "vitest";
import { esRutaPublica } from "./rutas";

describe("esRutaPublica", () => {
  it("considera pública la ruta de login", () => {
    expect(esRutaPublica("/login")).toBe(true);
  });

  it("considera pública una subruta de login", () => {
    expect(esRutaPublica("/login/algo")).toBe(true);
  });

  it("no considera pública la raíz protegida", () => {
    expect(esRutaPublica("/")).toBe(false);
  });

  it("no considera pública una ruta de un módulo de negocio", () => {
    expect(esRutaPublica("/clientes")).toBe(false);
  });

  it("no confunde una ruta que solo empieza igual con /login", () => {
    expect(esRutaPublica("/loginado")).toBe(false);
  });
});
