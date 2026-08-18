import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { ListaTiposServicio } from "./ListaTiposServicio";

const mockUseSession = vi.fn();
const mockObtener = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
}));

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
    crear: vi.fn(),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
  },
}));

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <ListaTiposServicio />
    </ProveedorNotificaciones>,
  );
}

describe("ListaTiposServicio", () => {
  beforeEach(() => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    mockObtener.mockReset();
  });

  it("muestra las filas devueltas por el cliente API", async () => {
    mockObtener.mockResolvedValueOnce({
      contenido: [
        { id: 1, nombre: "Soporte", activo: true },
        { id: 2, nombre: "Mantención", activo: false },
      ],
      total: 2,
      pagina: 0,
      tamano: 20,
    });

    renderizar();

    expect(await screen.findByText("Soporte")).toBeInTheDocument();
    expect(screen.getByText("Mantención")).toBeInTheDocument();
    expect(screen.getByText("Activo")).toBeInTheDocument();
    expect(screen.getByText("Inactivo")).toBeInTheDocument();
  });

  it("muestra el mensaje de vacío cuando no hay resultados", async () => {
    mockObtener.mockResolvedValueOnce({ contenido: [], total: 0, pagina: 0, tamano: 20 });

    renderizar();

    expect(await screen.findByText("No hay tipos de servicio registrados.")).toBeInTheDocument();
  });

  it("muestra el detalle del error cuando falla la carga", async () => {
    mockObtener.mockRejectedValueOnce(new ErrorApi(500, { detail: "El servidor no responde." }));

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("El servidor no responde.");
    });
  });
});
