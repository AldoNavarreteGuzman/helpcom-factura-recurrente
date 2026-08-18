import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { ListaProyectos } from "./ListaProyectos";

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

const CLIENTES_VACIO = { contenido: [], total: 0, pagina: 0, tamano: 50 };

const PROYECTO_DE_PRUEBA = {
  id: 1,
  clienteId: 10,
  clienteRazonSocial: "Cliente de Prueba SpA",
  tipoServicioId: null,
  tipoServicioNombre: null,
  codigo: "PRJ-001",
  nombre: "Soporte mensual",
  descripcion: null,
  precioBaseNeto: 12,
  monedaPrecio: "UF",
  periodicidad: "MENSUAL",
  diaFacturacion: 15,
  fechaInicio: "2026-01-01",
  fechaTermino: null,
  activo: true,
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <ListaProyectos />
    </ProveedorNotificaciones>,
  );
}

describe("ListaProyectos", () => {
  beforeEach(() => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    mockObtener.mockReset();
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/clientes")) {
        return Promise.resolve(CLIENTES_VACIO);
      }
      return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 20 });
    });
  });

  it("muestra los proyectos devueltos por el cliente API, con cliente y precio formateados", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/proyectos")) {
        return Promise.resolve({
          contenido: [PROYECTO_DE_PRUEBA],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByText("Soporte mensual")).toBeInTheDocument();
    expect(screen.getByText("Cliente de Prueba SpA")).toBeInTheDocument();
    expect(screen.getByText("PRJ-001")).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Mensual" })).toBeInTheDocument();
  });

  it("envía los filtros como query params al cliente API", async () => {
    renderizar();

    await waitFor(() => {
      const llamadasAProyectos = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/proyectos"));
      expect(llamadasAProyectos.length).toBeGreaterThan(0);
      expect(llamadasAProyectos[0]).toContain("page=0");
      expect(llamadasAProyectos[0]).toContain("size=20");
    });
  });

  it("muestra el mensaje de vacío cuando no hay resultados", async () => {
    renderizar();

    expect(
      await screen.findByText("No hay proyectos que coincidan con los filtros."),
    ).toBeInTheDocument();
  });

  it("un OPERADOR no ve el botón de crear y las acciones de fila quedan deshabilitadas", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/proyectos")) {
        return Promise.resolve({
          contenido: [PROYECTO_DE_PRUEBA],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(screen.queryByRole("button", { name: "+ Nuevo proyecto" })).not.toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Editar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Desactivar" })).toBeDisabled();
  });

  it("muestra el detalle del error cuando falla la carga", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/proyectos")) {
        return Promise.reject(new ErrorApi(500, { detail: "El servidor no responde." }));
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("El servidor no responde.");
    });
  });
});
