import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { NuevaFactura } from "./NuevaFactura";

const mockPush = vi.fn();
const mockObtener = vi.fn();
const mockCrear = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
    crear: (...args: unknown[]) => mockCrear(...args),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
    subirArchivo: vi.fn(),
    descargarArchivo: vi.fn(),
  },
}));

const CLIENTES_VACIO = { contenido: [], total: 0, pagina: 0, tamano: 50 };

const BASE = {
  proyectoId: 100,
  proyectoNombre: "Soporte",
  origen: "CICLO",
  periodoAnio: 2026,
  periodoMes: 2,
  fechaFacturacion: "2026-02-15",
  monedaOrigen: "CLP",
  precioBaseNeto: 100000,
  acuerdoTipo: null,
  acuerdoValor: null,
  acuerdoMoneda: null,
  valorUf: null,
  fechaValorUf: null,
  tasaIva: 0.19,
  numeroFactura: null,
  fechaFactura: null,
};

const PROPUESTA_A1 = {
  ...BASE,
  id: 1,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  descripcion: "Soporte mensual A1",
  netoClp: 100000,
  ivaClp: 19000,
  totalClp: 119000,
  estado: "PENDIENTE",
};
const PROPUESTA_A2 = {
  ...BASE,
  id: 2,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  descripcion: "Soporte mensual A2",
  netoClp: 42017,
  ivaClp: 7983,
  totalClp: 50000,
  estado: "PENDIENTE",
};
const PROPUESTA_A_FACTURADA = {
  ...BASE,
  id: 3,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  descripcion: "Servicio ya facturado",
  netoClp: 100000,
  ivaClp: 19000,
  totalClp: 119000,
  estado: "FACTURADA",
  numeroFactura: "F-001",
  fechaFactura: "2026-01-01",
};
const PROPUESTA_A_PENDIENTE_UF = {
  ...BASE,
  id: 4,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  descripcion: "Servicio pendiente UF",
  netoClp: 0,
  ivaClp: 0,
  totalClp: 0,
  estado: "PENDIENTE_UF",
};
const PROPUESTA_A_ANULADA = {
  ...BASE,
  id: 5,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  descripcion: "Servicio anulado",
  netoClp: 100000,
  ivaClp: 19000,
  totalClp: 119000,
  estado: "ANULADA",
};
const PROPUESTA_B = {
  ...BASE,
  id: 6,
  clienteId: 20,
  clienteRazonSocial: "Cliente B Ltda.",
  descripcion: "Servicio cliente B",
  netoClp: 75000,
  ivaClp: 14250,
  totalClp: 89250,
  estado: "PENDIENTE",
};

const TODAS_LAS_PROPUESTAS = [
  PROPUESTA_A1,
  PROPUESTA_A2,
  PROPUESTA_A_FACTURADA,
  PROPUESTA_A_PENDIENTE_UF,
  PROPUESTA_A_ANULADA,
  PROPUESTA_B,
];

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <NuevaFactura />
    </ProveedorNotificaciones>,
  );
}

describe("NuevaFactura", () => {
  beforeEach(() => {
    mockPush.mockReset();
    mockCrear.mockReset();
    mockObtener.mockReset();
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/clientes")) {
        return Promise.resolve(CLIENTES_VACIO);
      }
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({
          contenido: TODAS_LAS_PROPUESTAS,
          total: TODAS_LAS_PROPUESTAS.length,
          pagina: 0,
          tamano: 10,
        });
      }
      return Promise.reject(new Error("ruta no mockeada: " + ruta));
    });
  });

  it("solo las propuestas PENDIENTE son seleccionables", async () => {
    renderizar();

    expect(await screen.findByRole("checkbox", { name: /Soporte mensual A1/ })).toBeEnabled();
    expect(screen.getByRole("checkbox", { name: /Servicio ya facturado/ })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /Servicio pendiente UF/ })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: /Servicio anulado/ })).toBeDisabled();
  });

  it("al seleccionar una propuesta se restringe la selección a su mismo cliente", async () => {
    const usuario = userEvent.setup();
    renderizar();

    const casillaA1 = await screen.findByRole("checkbox", { name: /Soporte mensual A1/ });
    expect(screen.getByRole("checkbox", { name: /Servicio cliente B/ })).toBeEnabled();

    await usuario.click(casillaA1);

    expect(screen.getByRole("checkbox", { name: /Servicio cliente B/ })).toBeDisabled();
    expect(
      screen.getByText(/Selección restringida al cliente de la primera propuesta elegida/),
    ).toBeInTheDocument();
  });

  it("el subtotal suma correctamente las propuestas seleccionadas", async () => {
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(await screen.findByRole("checkbox", { name: /Soporte mensual A1/ }));
    expect(await screen.findByText("Subtotal: $119.000")).toBeInTheDocument();

    await usuario.click(screen.getByRole("checkbox", { name: /Soporte mensual A2/ }));
    expect(await screen.findByText("Subtotal: $169.000")).toBeInTheDocument();
  });

  it("un 409 FACTURA_DUPLICADA aterriza en el campo N° factura", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(409, {
        detail: "Ya existe una factura con el número F-100.",
        codigo: "FACTURA_DUPLICADA",
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(await screen.findByRole("checkbox", { name: /Soporte mensual A1/ }));
    await usuario.type(screen.getByLabelText(/N° factura/), "F-100");
    await usuario.type(screen.getByLabelText(/Fecha de factura/), "2026-03-01");
    await usuario.click(screen.getByRole("button", { name: "Crear factura" }));

    const mensajes = await screen.findAllByText("Ya existe una factura con el número F-100.");
    expect(mensajes.length).toBeGreaterThan(0);
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("un 409 PROPUESTA_NO_FACTURABLE se muestra con su detail y ofrece refrescar", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(409, {
        detail: "La propuesta 4 no se puede facturar: está en estado PENDIENTE_UF.",
        codigo: "PROPUESTA_NO_FACTURABLE",
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(await screen.findByRole("checkbox", { name: /Soporte mensual A1/ }));
    await usuario.type(screen.getByLabelText(/N° factura/), "F-200");
    await usuario.type(screen.getByLabelText(/Fecha de factura/), "2026-03-01");
    await usuario.click(screen.getByRole("button", { name: "Crear factura" }));

    const mensajes = await screen.findAllByText(
      "La propuesta 4 no se puede facturar: está en estado PENDIENTE_UF.",
    );
    expect(mensajes.length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Refrescar listado" })).toBeInTheDocument();
    expect(mockPush).not.toHaveBeenCalled();
  });

  it("al crear con éxito, navega al detalle de la factura recién creada", async () => {
    mockCrear.mockResolvedValueOnce({ id: 42, numeroFactura: "F-300" });
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(await screen.findByRole("checkbox", { name: /Soporte mensual A1/ }));
    await usuario.type(screen.getByLabelText(/N° factura/), "F-300");
    await usuario.type(screen.getByLabelText(/Fecha de factura/), "2026-03-01");
    await usuario.click(screen.getByRole("button", { name: "Crear factura" }));

    await waitFor(() =>
      expect(mockCrear).toHaveBeenCalledWith(
        "/facturas",
        expect.objectContaining({
          numeroFactura: "F-300",
          fechaFactura: "2026-03-01",
          propuestaIds: [1],
        }),
      ),
    );
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/facturacion/facturas/42"));
  });
});
