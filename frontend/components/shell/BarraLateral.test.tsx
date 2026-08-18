import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ENLACES_NAV } from "@/lib/navegacion";
import { BarraLateral } from "./BarraLateral";

const mockUseSession = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
  signOut: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
}));

describe("BarraLateral", () => {
  it("muestra todos los enlaces para un usuario OPERADOR (regla provisional: todos visibles)", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });

    render(<BarraLateral />);

    for (const enlace of ENLACES_NAV) {
      expect(screen.getByRole("link", { name: enlace.etiqueta })).toBeInTheDocument();
    }
  });

  it("muestra todos los enlaces para un usuario ADMINISTRADOR", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });

    render(<BarraLateral />);

    for (const enlace of ENLACES_NAV) {
      expect(screen.getByRole("link", { name: enlace.etiqueta })).toBeInTheDocument();
    }
  });

  it("no muestra ningún enlace de módulo para una sesión sin roles reconocidos", () => {
    mockUseSession.mockReturnValue({ data: { roles: [] }, status: "authenticated" });

    render(<BarraLateral />);

    expect(screen.queryAllByRole("link")).toHaveLength(0);
  });

  it("marca como página actual el enlace de la ruta activa", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });

    render(<BarraLateral />);

    expect(screen.getByRole("link", { name: "Panel" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Clientes" })).not.toHaveAttribute("aria-current");
  });

  it("muestra el nombre del usuario y permite cerrar sesión", () => {
    mockUseSession.mockReturnValue({
      data: { roles: ["ADMINISTRADOR"], user: { name: "Ana Prueba" } },
      status: "authenticated",
    });

    render(<BarraLateral />);

    expect(screen.getByText("Ana Prueba")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cerrar sesión" })).toBeInTheDocument();
  });
});
