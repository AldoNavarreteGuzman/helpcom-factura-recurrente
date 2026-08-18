import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { LayoutDetalleProyecto } from "./LayoutDetalleProyecto";

const mockUseSession = vi.fn();
const mockObtener = vi.fn();
const mockUsePathname = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mockUsePathname(),
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

function renderizar(pathname = "/proyectos/1") {
  mockUsePathname.mockReturnValue(pathname);
  return render(
    <ProveedorNotificaciones>
      <LayoutDetalleProyecto proyectoId={1}>
        <div>contenido de la pestaña</div>
      </LayoutDetalleProyecto>
    </ProveedorNotificaciones>,
  );
}

/** `FormularioProyecto` (dentro del modal de "Editar") trae su propio Selector de cliente/tipo
 * de servicio, cada uno con su propio `obtener` — hay que responder según la ruta, igual que ya
 * hace `FormularioProyecto.test.tsx`. */
function mockearRutaProyecto() {
  mockObtener.mockImplementation((ruta: string) => {
    if (ruta.startsWith("/proyectos")) {
      return Promise.resolve(PROYECTO_DE_PRUEBA);
    }
    if (ruta.startsWith("/clientes") || ruta.startsWith("/tipos-servicio")) {
      return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 50 });
    }
    return Promise.reject(new Error("ruta no mockeada: " + ruta));
  });
}

describe("LayoutDetalleProyecto", () => {
  beforeEach(() => {
    mockObtener.mockReset();
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
  });

  it("muestra el nombre del proyecto, el badge de estado y las dos pestañas", async () => {
    mockearRutaProyecto();

    renderizar();

    expect(await screen.findByRole("heading", { name: "Soporte mensual" })).toBeInTheDocument();
    expect(screen.getByText("Activo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Datos" })).toHaveAttribute("href", "/proyectos/1");
    expect(screen.getByRole("link", { name: "Descuentos" })).toHaveAttribute(
      "href",
      "/proyectos/1/acuerdos",
    );
  });

  it("marca la pestaña Descuentos como activa cuando la ruta es /proyectos/1/acuerdos", async () => {
    mockearRutaProyecto();

    renderizar("/proyectos/1/acuerdos");

    expect(await screen.findByRole("link", { name: "Descuentos" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "Datos" })).not.toHaveAttribute("aria-current");
  });

  it("renderiza el contenido de la pestaña (children) bajo la cabecera", async () => {
    mockearRutaProyecto();

    renderizar();

    expect(await screen.findByText("contenido de la pestaña")).toBeInTheDocument();
  });

  it("un ADMINISTRADOR ve el botón Editar; un OPERADOR no", async () => {
    mockearRutaProyecto();
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });

    renderizar();

    await screen.findByRole("heading", { name: "Soporte mensual" });
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument();
  });

  it("el botón Editar abre el formulario de edición del proyecto", async () => {
    mockearRutaProyecto();
    const usuario = userEvent.setup();

    renderizar();

    await usuario.click(await screen.findByRole("button", { name: "Editar" }));

    expect(screen.getByRole("heading", { name: "Editar proyecto" })).toBeInTheDocument();
  });

  it("muestra el detail del error si falla la carga del proyecto", async () => {
    mockObtener.mockRejectedValueOnce(new ErrorApi(404, { detail: "Proyecto no encontrado." }));

    renderizar();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Proyecto no encontrado.");
    });
  });
});
