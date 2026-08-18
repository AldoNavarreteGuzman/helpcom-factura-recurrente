import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ListaFacturas } from "./ListaFacturas";

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

const CLIENTES_VACIO = { contenido: [], total: 0, pagina: 0, tamano: 50 };

const FACTURA_DE_PRUEBA = {
  id: 1,
  numeroFactura: "F-001",
  fechaFactura: "2026-03-01",
  observacion: null,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  tienePdf: true,
  nombreArchivoPdf: "factura-F-001.pdf",
  propuestas: [
    {
      id: 1,
      periodoAnio: 2026,
      periodoMes: 2,
      fechaFacturacion: "2026-02-15",
      descripcion: "Soporte",
      netoClp: 100000,
      ivaClp: 19000,
      totalClp: 119000,
      estado: "FACTURADA",
    },
  ],
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <ListaFacturas />
    </ProveedorNotificaciones>,
  );
}

describe("ListaFacturas", () => {
  beforeEach(() => {
    mockObtener.mockReset();
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/clientes")) {
        return Promise.resolve(CLIENTES_VACIO);
      }
      return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 20 });
    });
  });

  it("envía los filtros de número, rango de fecha y cliente como query params", async () => {
    renderizar();

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/facturas"));
      expect(llamadas.length).toBeGreaterThan(0);
    });
  });

  it("envía fechaDesde y fechaHasta como query params al completar el rango", async () => {
    renderizar();

    await waitFor(() => expect(mockObtener).toHaveBeenCalled());
    mockObtener.mockClear();

    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-01-01" } });
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2026-03-31" } });

    await waitFor(() => {
      const ultimaLlamada = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/facturas"))
        .at(-1);
      expect(ultimaLlamada).toContain("fechaDesde=2026-01-01");
      expect(ultimaLlamada).toContain("fechaHasta=2026-03-31");
    });
  });

  it("muestra el total (suma de propuestas) y el indicador de PDF", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/facturas")) {
        return Promise.resolve({ contenido: [FACTURA_DE_PRUEBA], total: 1, pagina: 0, tamano: 20 });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByText("$119.000")).toBeInTheDocument();
    expect(screen.getByText("Sí")).toBeInTheDocument();
  });

  it("el estado vacío muestra el mensaje correspondiente", async () => {
    renderizar();

    expect(
      await screen.findByText("No hay facturas que coincidan con los filtros."),
    ).toBeInTheDocument();
  });
});
