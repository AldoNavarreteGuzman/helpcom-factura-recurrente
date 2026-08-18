import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ENLACES_NAV } from "@/lib/navegacion";
import { BarraInferior } from "./BarraInferior";

const mockUseSession = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
}));

const CANTIDAD_PRINCIPAL = 4;

/**
 * Los primeros `CANTIDAD_PRINCIPAL` enlaces son accesos directos (role "link"); el resto vive
 * dentro del menú "Más" con `role="menuitem"` — ese `role` explícito reemplaza el "link"
 * implícito del `<a>`, así que hay que consultarlos por su rol real, no por el que tendrían
 * fuera de un menú.
 */
function verificarTodosLosEnlacesVisibles() {
  const principales = ENLACES_NAV.slice(0, CANTIDAD_PRINCIPAL);
  const resto = ENLACES_NAV.slice(CANTIDAD_PRINCIPAL);

  for (const enlace of principales) {
    expect(screen.getByRole("link", { name: enlace.etiqueta })).toBeInTheDocument();
  }
  for (const enlace of resto) {
    expect(screen.getByRole("menuitem", { name: enlace.etiqueta })).toBeInTheDocument();
  }
}

describe("BarraInferior", () => {
  it("muestra todos los enlaces (directos + dentro de \"Más\") para un usuario OPERADOR", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });

    render(<BarraInferior />);

    await userEvent.click(screen.getByRole("button", { name: "Más" }));

    verificarTodosLosEnlacesVisibles();
  });

  it("muestra todos los enlaces para un usuario ADMINISTRADOR", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });

    render(<BarraInferior />);

    await userEvent.click(screen.getByRole("button", { name: "Más" }));

    verificarTodosLosEnlacesVisibles();
  });

  it("no muestra ningún enlace de módulo ni el botón Más para una sesión sin roles reconocidos", () => {
    mockUseSession.mockReturnValue({ data: { roles: [] }, status: "authenticated" });

    render(<BarraInferior />);

    expect(screen.queryAllByRole("link")).toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Más" })).not.toBeInTheDocument();
  });

  it("marca como página actual el enlace directo de la ruta activa", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });

    render(<BarraInferior />);

    expect(screen.getByRole("link", { name: "Panel" })).toHaveAttribute("aria-current", "page");
  });

  it("el menú \"Más\" empieza cerrado y se puede volver a cerrar", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });

    render(<BarraInferior />);

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Más" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "Más" }));
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});
