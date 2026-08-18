import type { Session } from "next-auth";
import { describe, expect, it } from "vitest";
import { estaAutorizado } from "./auth.config";

function sesionCon(roles: Session["roles"] = []): Session {
  return {
    user: { name: "Ana" },
    roles,
    expires: "2999-01-01T00:00:00.000Z",
  };
}

describe("estaAutorizado (guard de rutas del middleware)", () => {
  it("redirige (retorna false) cuando no hay sesión en una ruta protegida", () => {
    expect(estaAutorizado("/", null)).toBe(false);
    expect(estaAutorizado("/clientes", null)).toBe(false);
  });

  it("deja pasar una ruta protegida cuando hay sesión con usuario", () => {
    expect(estaAutorizado("/", sesionCon())).toBe(true);
    expect(estaAutorizado("/informes", sesionCon())).toBe(true);
  });

  it("deja pasar /login sin sesión", () => {
    expect(estaAutorizado("/login", null)).toBe(true);
  });

  it("deja pasar /login incluso con sesión (no rompe el flujo si ya está logueado)", () => {
    expect(estaAutorizado("/login", sesionCon())).toBe(true);
  });
});
