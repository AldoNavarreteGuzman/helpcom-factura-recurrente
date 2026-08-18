import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BadgeEstadoEjecucionCiclo } from "./BadgeEstadoEjecucionCiclo";

/**
 * `ejecucion_ciclo.estado = ERROR` no es provocable con datos reales sin romper el stack
 * (`ServicioCicloFacturacion`: cada proyecto corre en su propia transacción con captura de
 * `RuntimeException`, así que cualquier falla de negocio queda absorbida como
 * `CON_ADVERTENCIAS`, nunca escala a `ERROR` — ver docs/plan-rediseno.md §8, R5). Por eso este
 * render se cubre acá, en aislamiento, en vez de con una pasada visual sobre datos reales.
 */
describe("BadgeEstadoEjecucionCiclo", () => {
  it("EXITOSA usa el token estado-facturada (verde)", () => {
    render(<BadgeEstadoEjecucionCiclo estado="EXITOSA" />);

    const badge = screen.getByText("Exitosa");
    expect(badge).toHaveClass("bg-estado-facturada/10", "text-estado-facturada");
  });

  it("CON_ADVERTENCIAS usa el token estado-sin-uf (ámbar)", () => {
    render(<BadgeEstadoEjecucionCiclo estado="CON_ADVERTENCIAS" />);

    const badge = screen.getByText("Con advertencias");
    expect(badge).toHaveClass("bg-estado-sin-uf/10", "text-estado-sin-uf");
  });

  it("ERROR usa el token estado-error (rojo)", () => {
    render(<BadgeEstadoEjecucionCiclo estado="ERROR" />);

    const badge = screen.getByText("Error");
    expect(badge).toHaveClass("bg-estado-error/10", "text-estado-error");
  });
});
