import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PestanasDetalle } from "./PestanasDetalle";

const mockUsePathname = vi.fn();

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
}));

const PESTANAS = [
  { href: "/proyectos/1", etiqueta: "Datos" },
  { href: "/proyectos/1/acuerdos", etiqueta: "Descuentos" },
];

describe("PestanasDetalle", () => {
  it("muestra todas las pestañas", () => {
    mockUsePathname.mockReturnValue("/proyectos/1");

    render(<PestanasDetalle pestanas={PESTANAS} />);

    expect(screen.getByRole("link", { name: "Datos" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Descuentos" })).toBeInTheDocument();
  });

  it("marca como página actual la pestaña que coincide con la ruta", () => {
    mockUsePathname.mockReturnValue("/proyectos/1/acuerdos");

    render(<PestanasDetalle pestanas={PESTANAS} />);

    expect(screen.getByRole("link", { name: "Descuentos" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Datos" })).not.toHaveAttribute("aria-current");
  });
});
