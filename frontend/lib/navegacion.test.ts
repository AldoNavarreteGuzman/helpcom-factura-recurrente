import { describe, expect, it } from "vitest";
import { enlacesVisibles, type EnlaceNav } from "./navegacion";

const ENLACES_DE_PRUEBA: EnlaceNav[] = [
  { href: "/publico", etiqueta: "Público", rolesPermitidos: [] },
  { href: "/operador", etiqueta: "De operador", rolesPermitidos: ["OPERADOR"] },
  { href: "/administrador", etiqueta: "Solo admin", rolesPermitidos: ["ADMINISTRADOR"] },
  { href: "/ambos", etiqueta: "Ambos roles", rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"] },
];

describe("enlacesVisibles", () => {
  it("muestra los enlaces sin restricción de rol a cualquier usuario autenticado", () => {
    const visibles = enlacesVisibles(ENLACES_DE_PRUEBA, []);
    expect(visibles.map((e) => e.href)).toEqual(["/publico"]);
  });

  it("oculta los enlaces de OPERADOR a un usuario que solo es ADMINISTRADOR", () => {
    const visibles = enlacesVisibles(ENLACES_DE_PRUEBA, ["ADMINISTRADOR"]);
    const hrefs = visibles.map((e) => e.href);
    expect(hrefs).toContain("/administrador");
    expect(hrefs).toContain("/ambos");
    expect(hrefs).not.toContain("/operador");
  });

  it("oculta los enlaces de ADMINISTRADOR a un usuario que solo es OPERADOR", () => {
    const visibles = enlacesVisibles(ENLACES_DE_PRUEBA, ["OPERADOR"]);
    const hrefs = visibles.map((e) => e.href);
    expect(hrefs).toContain("/operador");
    expect(hrefs).toContain("/ambos");
    expect(hrefs).not.toContain("/administrador");
  });

  it("muestra todos los enlaces a un usuario con ambos roles", () => {
    const visibles = enlacesVisibles(ENLACES_DE_PRUEBA, ["ADMINISTRADOR", "OPERADOR"]);
    expect(visibles).toHaveLength(ENLACES_DE_PRUEBA.length);
  });
});
