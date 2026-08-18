import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ListaClientes } from "./ListaClientes";

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
      <ListaClientes />
    </ProveedorNotificaciones>,
  );
}

const CLIENTE_DE_PRUEBA = {
  id: 1,
  rut: "11111111-1",
  razonSocial: "Cliente A",
  nombreFantasia: null,
  giro: null,
  email: null,
  telefono: null,
  direccion: null,
  activo: true,
};

describe("ListaClientes — control de acceso por rol", () => {
  beforeEach(() => {
    mockObtener.mockReset();
  });

  it("un ADMINISTRADOR ve el botón de crear y las acciones de fila habilitadas", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    mockObtener.mockResolvedValueOnce({
      contenido: [CLIENTE_DE_PRUEBA],
      total: 1,
      pagina: 0,
      tamano: 20,
    });

    renderizar();

    expect(await screen.findByRole("button", { name: "+ Nuevo cliente" })).toBeEnabled();
    expect(await screen.findByRole("button", { name: "Editar" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Eliminar" })).toBeEnabled();
  });

  it("un OPERADOR no ve el botón de crear y las acciones de fila quedan deshabilitadas", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });
    mockObtener.mockResolvedValueOnce({
      contenido: [CLIENTE_DE_PRUEBA],
      total: 1,
      pagina: 0,
      tamano: 20,
    });

    renderizar();

    expect(screen.queryByRole("button", { name: "+ Nuevo cliente" })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Editar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Desactivar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Eliminar" })).toBeDisabled();
  });
});
