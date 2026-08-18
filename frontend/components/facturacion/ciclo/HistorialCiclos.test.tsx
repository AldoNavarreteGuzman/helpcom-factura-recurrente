import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { HistorialCiclos } from "./HistorialCiclos";

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

describe("HistorialCiclos", () => {
  it("renderiza el historial con datos mockeados", async () => {
    mockObtener.mockResolvedValueOnce({
      contenido: [
        {
          id: 1,
          periodoAnio: 2026,
          periodoMes: 2,
          ejecutadoEn: "2026-02-01T00:05:00-03:00",
          disparo: "AUTOMATICO",
          cantidadGeneradas: 12,
          cantidadPendientesUf: 1,
          estado: "CON_ADVERTENCIAS",
          observacion: "Propuestas generadas: 12. Pendientes de UF: 1. Proyectos con error: 0.",
          ejecutadoPor: "sistema",
        },
      ],
      total: 1,
      pagina: 0,
      tamano: 20,
    });

    render(<HistorialCiclos />);

    expect(await screen.findByText("02-2026")).toBeInTheDocument();
    expect(screen.getByText("Automático")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("Con advertencias")).toBeInTheDocument();
  });

  it("muestra el mensaje de vacío cuando no hay ejecuciones", async () => {
    mockObtener.mockResolvedValueOnce({ contenido: [], total: 0, pagina: 0, tamano: 20 });

    render(<HistorialCiclos />);

    expect(await screen.findByText("Todavía no se ha ejecutado el ciclo.")).toBeInTheDocument();
  });
});
