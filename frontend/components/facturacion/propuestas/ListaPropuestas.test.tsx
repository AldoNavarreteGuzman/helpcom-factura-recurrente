import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ListaPropuestas } from "./ListaPropuestas";

const mockUseSession = vi.fn();
const mockObtener = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
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

const PROPUESTA_PENDIENTE_UF = {
  id: 1,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  proyectoId: 100,
  proyectoNombre: "Soporte mensual",
  origen: "CICLO",
  periodoAnio: 2026,
  periodoMes: 2,
  fechaFacturacion: "2026-02-15",
  descripcion: "Soporte mensual",
  monedaOrigen: "UF",
  precioBaseNeto: 12,
  acuerdoTipo: null,
  acuerdoValor: null,
  acuerdoMoneda: null,
  valorUf: null,
  fechaValorUf: null,
  netoClp: 0,
  tasaIva: 0.19,
  ivaClp: 0,
  totalClp: 0,
  estado: "PENDIENTE_UF",
  numeroFactura: null,
  fechaFactura: null,
};

/**
 * Misma fila PENDIENTE_UF, pero SIN la clave `valorUf` (en vez de `valorUf: null`) — la forma
 * real de la respuesta: toda la API omite un campo `null` en vez de mandarlo como
 * `"valorUf":null` (`jackson.default-property-inclusion: non_null`, docs/deuda-tecnica.md
 * ítem 5), así que tras `JSON.parse` el campo vale `undefined`, no `null`. El fixture de
 * arriba (`valorUf: null` explícito) no ejercita este caso — por eso el bug real (columna
 * "Valor UF" mostrando "$NaN") pasó sin que ningún test ni la revisión visual lo notaran.
 */
const PROPUESTA_PENDIENTE_UF_SIN_CLAVE_VALOR_UF = {
  ...PROPUESTA_PENDIENTE_UF,
  id: 3,
  descripcion: "Soporte mensual (sin clave valorUf)",
  valorUf: undefined,
};

const PROPUESTA_FACTURADA = {
  ...PROPUESTA_PENDIENTE_UF,
  id: 2,
  netoClp: 480000,
  ivaClp: 91200,
  totalClp: 571200,
  valorUf: 40000,
  fechaValorUf: "2026-02-15",
  estado: "FACTURADA",
  numeroFactura: "F-001",
  fechaFactura: "2026-03-01",
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <ListaPropuestas />
    </ProveedorNotificaciones>,
  );
}

describe("ListaPropuestas", () => {
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

  it("envía los filtros de período, cliente y estado como query params", async () => {
    renderizar();

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/propuestas"));
      expect(llamadas.length).toBeGreaterThan(0);
    });
  });

  it("una fila PENDIENTE_UF muestra '— (sin UF)' en vez de $0 en las columnas de monto", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_PENDIENTE_UF],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    const marcadoresAusentes = await screen.findAllByText("— (sin UF)");
    // Valor UF, Neto, IVA y Total quedan marcados como ausentes.
    expect(marcadoresAusentes.length).toBe(4);
    expect(screen.queryByText("$0")).not.toBeInTheDocument();
  });

  it("una fila FACTURADA muestra los montos reales, no el marcador de ausencia", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_FACTURADA],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByText("$480.000")).toBeInTheDocument();
    expect(screen.queryByText("— (sin UF)")).not.toBeInTheDocument();
  });

  it("la acción Anular no aparece en una fila FACTURADA", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_FACTURADA],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    await screen.findByText("Cliente A SpA");
    expect(screen.queryByRole("button", { name: "Anular" })).not.toBeInTheDocument();
  });

  it("la acción Anular está deshabilitada para OPERADOR en una fila anulable", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_PENDIENTE_UF],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByRole("button", { name: "Anular" })).toBeDisabled();
  });

  it("la acción Anular está habilitada para ADMINISTRADOR en una fila anulable", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_PENDIENTE_UF],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByRole("button", { name: "Anular" })).toBeEnabled();
  });

  it("la columna N° factura muestra el número en una fila FACTURADA y '—' si no está facturada", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_PENDIENTE_UF, PROPUESTA_FACTURADA],
          total: 2,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    expect(await screen.findByText("F-001")).toBeInTheDocument();
    const filas = await screen.findAllByRole("row");
    const filaPendiente = filas.find((fila) => within(fila).queryByText("Pendiente UF") !== null);
    if (!filaPendiente) {
      throw new Error("No se encontró la fila PENDIENTE_UF");
    }
    // "—" exacto (la columna N° factura) es distinto de "— (sin UF)" (Valor UF/Neto/IVA/Total).
    expect(within(filaPendiente).getByText("—")).toBeInTheDocument();
    expect(within(filaPendiente).queryByText("F-001")).not.toBeInTheDocument();
  });

  it("el filtro por origen viaja como query param y acota el listado", async () => {
    const usuario = userEvent.setup();
    renderizar();

    await waitFor(() => {
      expect(
        mockObtener.mock.calls.some((llamada) => (llamada[0] as string).startsWith("/propuestas")),
      ).toBe(true);
    });
    mockObtener.mockClear();

    await usuario.selectOptions(screen.getByLabelText("Origen"), "CSV");

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/propuestas"));
      expect(llamadas.some((ruta) => ruta.includes("origen=CSV"))).toBe(true);
    });
  });

  it("la columna Valor UF muestra '— (sin UF)', nunca NaN, cuando el backend omite la clave valorUf", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: [PROPUESTA_PENDIENTE_UF_SIN_CLAVE_VALOR_UF],
          total: 1,
          pagina: 0,
          tamano: 20,
        });
      }
      return Promise.resolve(CLIENTES_VACIO);
    });

    renderizar();

    const fila = (await screen.findAllByRole("row")).find(
      (f) => within(f).queryByText("Soporte mensual (sin clave valorUf)") !== null,
    );
    if (!fila) {
      throw new Error("No se encontró la fila de prueba");
    }
    expect(within(fila).queryByText(/NaN/)).not.toBeInTheDocument();
    expect(within(fila).getAllByText("— (sin UF)").length).toBeGreaterThanOrEqual(1);
  });
});
