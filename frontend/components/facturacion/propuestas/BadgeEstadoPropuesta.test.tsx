import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BadgeEstadoPropuesta } from "./BadgeEstadoPropuesta";

/** Semántica de color documentada en docs/frontend.md §5.4: neutro/advertencia/éxito/inactivo. */
describe("BadgeEstadoPropuesta", () => {
  it("PENDIENTE usa el token estado-pendiente (azul, neutro)", () => {
    render(<BadgeEstadoPropuesta estado="PENDIENTE" />);

    const badge = screen.getByText("Pendiente");
    expect(badge).toHaveClass("bg-estado-pendiente/10", "text-estado-pendiente");
  });

  it("PENDIENTE_UF usa el token estado-sin-uf (ámbar, advertencia)", () => {
    render(<BadgeEstadoPropuesta estado="PENDIENTE_UF" />);

    const badge = screen.getByText("Pendiente UF");
    expect(badge).toHaveClass("bg-estado-sin-uf/10", "text-estado-sin-uf");
  });

  it("FACTURADA usa el token estado-facturada (verde, éxito)", () => {
    render(<BadgeEstadoPropuesta estado="FACTURADA" />);

    const badge = screen.getByText("Facturada");
    expect(badge).toHaveClass("bg-estado-facturada/10", "text-estado-facturada");
  });

  it("ANULADA usa el token estado-anulada (gris, inactivo)", () => {
    render(<BadgeEstadoPropuesta estado="ANULADA" />);

    const badge = screen.getByText("Anulada");
    expect(badge).toHaveClass("bg-estado-anulada/10", "text-estado-anulada");
  });
});
