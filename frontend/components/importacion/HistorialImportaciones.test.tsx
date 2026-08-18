import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { HistorialImportaciones } from "./HistorialImportaciones";

const mockObtener = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
    crear: vi.fn(),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
  },
}));

const IMPORTACION_DE_PRUEBA = {
  id: 1,
  nombreArchivo: "enero-2026.csv",
  fechaImportacion: "2026-01-05T09:00:00-03:00",
  totalFilas: 10,
  filasOk: 8,
  filasError: 2,
  estado: "PARCIAL",
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <HistorialImportaciones />
    </ProveedorNotificaciones>,
  );
}

describe("HistorialImportaciones", () => {
  beforeEach(() => {
    mockObtener.mockReset();
  });

  it("no pide sort explícito: el backend ya ordena por fecha descendente por defecto", async () => {
    mockObtener.mockResolvedValue({ contenido: [], total: 0, pagina: 0, tamano: 20 });
    renderizar();

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls.map((llamada) => llamada[0] as string);
      expect(llamadas.some((ruta) => ruta.includes("page=0"))).toBe(true);
      expect(llamadas.some((ruta) => ruta.includes("sort="))).toBe(false);
    });
  });

  it("muestra las columnas del historial", async () => {
    mockObtener.mockResolvedValue({
      contenido: [IMPORTACION_DE_PRUEBA],
      total: 1,
      pagina: 0,
      tamano: 20,
    });
    renderizar();

    expect(await screen.findByText("enero-2026.csv")).toBeInTheDocument();
    expect(screen.getByText("Parcial")).toBeInTheDocument();
  });

  it("el estado vacío muestra el mensaje correspondiente", async () => {
    mockObtener.mockResolvedValue({ contenido: [], total: 0, pagina: 0, tamano: 20 });
    renderizar();

    expect(await screen.findByText("Todavía no se ha importado ningún CSV.")).toBeInTheDocument();
  });
});
