import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { Dashboard } from "./Dashboard";
import type { Proyecto } from "@/types/proyecto";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

const mockObtener = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
  },
}));

function propuesta(datos: Partial<PropuestaFacturacion>): PropuestaFacturacion {
  return {
    id: 1,
    clienteId: 1,
    clienteRazonSocial: "Cliente de Prueba SpA",
    proyectoId: null,
    proyectoNombre: null,
    origen: "CICLO",
    periodoAnio: 2026,
    periodoMes: 6,
    fechaFacturacion: "2026-06-15",
    descripcion: "Servicio",
    monedaOrigen: "UF",
    precioBaseNeto: 12,
    acuerdoTipo: "DESCUENTO_PORCENTAJE",
    acuerdoValor: 10,
    acuerdoMoneda: null,
    valorUf: 40779.55,
    fechaValorUf: "2026-06-15",
    netoClp: 440419,
    tasaIva: 0.19,
    ivaClp: 83680,
    totalClp: 524099,
    estado: "PENDIENTE",
    numeroFactura: null,
    fechaFactura: null,
    ...datos,
  };
}

function proyecto(datos: Partial<Proyecto>): Proyecto {
  return {
    id: 1,
    clienteId: 1,
    clienteRazonSocial: "Cliente de Prueba SpA",
    tipoServicioId: 2,
    tipoServicioNombre: "Soporte y Mantención",
    codigo: null,
    nombre: "Soporte mensual",
    descripcion: null,
    precioBaseNeto: 12,
    monedaPrecio: "UF",
    periodicidad: "MENSUAL",
    diaFacturacion: 15,
    fechaInicio: "2026-01-01",
    fechaTermino: null,
    activo: true,
    ...datos,
  };
}

const PROPUESTAS: PropuestaFacturacion[] = [
  propuesta({
    id: 64,
    proyectoId: 1,
    proyectoNombre: "Soporte mensual",
    periodoMes: 5,
    netoClp: 435681,
    valorUf: 40340.86,
  }),
  propuesta({
    id: 65,
    proyectoId: 1,
    proyectoNombre: "Soporte mensual",
    periodoMes: 6,
    netoClp: 440419,
  }),
  propuesta({
    id: 67,
    proyectoId: 4,
    proyectoNombre: "Consultoría TI Mensual",
    periodoMes: 6,
    acuerdoTipo: "PRECIO_PACTADO",
    acuerdoValor: 5,
    acuerdoMoneda: "UF",
    precioBaseNeto: 6,
    valorUf: 40793.13,
    netoClp: 203966,
    clienteId: 2,
    clienteRazonSocial: "Helpcom Ltda",
  }),
  propuesta({
    id: 1,
    proyectoId: 1,
    proyectoNombre: "Soporte mensual",
    periodoMes: 8,
    estado: "PENDIENTE_UF",
    acuerdoTipo: "DESCUENTO_PORCENTAJE",
    acuerdoValor: 10,
    valorUf: undefined,
    netoClp: 0,
  }),
  propuesta({
    id: 66,
    proyectoId: 3,
    proyectoNombre: "Mantenimiento de Sistemas",
    periodoMes: 6,
    acuerdoTipo: "DESCUENTO_MONTO",
    acuerdoValor: 50000,
    acuerdoMoneda: "CLP",
    precioBaseNeto: 8,
    valorUf: 40765.97,
    netoClp: 276128,
  }),
];

const PROYECTOS: Proyecto[] = [
  proyecto({ id: 1, nombre: "Soporte mensual" }),
  proyecto({
    id: 4,
    nombre: "Consultoría TI Mensual",
    tipoServicioId: 1,
    tipoServicioNombre: "SaaS Crux ERP",
    clienteId: 2,
    clienteRazonSocial: "Helpcom Ltda",
  }),
];

function mockearRespuestaExitosa() {
  mockObtener.mockImplementation((ruta: string) => {
    if (ruta.startsWith("/propuestas")) {
      return Promise.resolve({
        contenido: PROPUESTAS,
        total: PROPUESTAS.length,
        pagina: 0,
        tamano: 500,
      });
    }
    if (ruta.startsWith("/proyectos")) {
      return Promise.resolve({
        contenido: PROYECTOS,
        total: PROYECTOS.length,
        pagina: 0,
        tamano: 500,
      });
    }
    return Promise.reject(new Error(`ruta no mockeada en el test: ${ruta}`));
  });
}

describe("Dashboard", () => {
  beforeEach(() => {
    mockObtener.mockReset();
  });

  it("muestra el spinner de carga en cada tarjeta mientras resuelve el fetch", () => {
    mockObtener.mockReturnValue(new Promise(() => {})); // nunca resuelve, para inspeccionar el estado de carga
    render(<Dashboard />);

    expect(screen.getAllByText("Cargando…").length).toBeGreaterThan(0);
  });

  it("con datos reales calcula y muestra el KPI de descuentos, PENDIENTE_UF aparte, y el callout ámbar", async () => {
    mockearRespuestaExitosa();
    render(<Dashboard />);

    await waitFor(() => expect(screen.queryAllByText("Cargando…").length).toBe(0));

    // KPI de descuentos: % = 484.090-435.681 + 489.355-440.419 = 97.345; monto (66) =
    // 326.128-276.128 = 50.000; total = 147.345; pactado (67) = 244.759-203.966 = 40.793, aparte.
    expect(screen.getByText("$97.345")).toBeInTheDocument();
    expect(screen.getByText("$50.000")).toBeInTheDocument();
    expect(screen.getByText("$147.345")).toBeInTheDocument();
    expect(screen.getByText("$40.793")).toBeInTheDocument();
    expect(screen.getByText(/Precio pactado — aparte/)).toBeInTheDocument();

    // El callout de PENDIENTE_UF cuenta la propuesta 1 aparte, con su enlace.
    expect(screen.getByText(/1 propuesta sin valor UF/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ver en Propuestas/ })).toHaveAttribute(
      "href",
      "/facturacion?estado=PENDIENTE_UF",
    );

    // Por cliente: los 2 clientes reales, cada uno con su neto.
    expect(screen.getByText("Cliente de Prueba SpA")).toBeInTheDocument();
    expect(screen.getByText("Helpcom Ltda")).toBeInTheDocument();
  });

  it("si el fetch falla, cada tarjeta muestra su propio error (la pantalla no se cae completa)", async () => {
    mockObtener.mockRejectedValue(new Error("Fallo simulado de red"));
    render(<Dashboard />);

    await waitFor(() => {
      expect(screen.getAllByText("Fallo simulado de red").length).toBeGreaterThan(0);
    });
    // 5 tarjetas comparten la fuente de datos (KPI, comparación, por tipo de servicio, por
    // proyecto, por cliente) — el callout de PENDIENTE_UF también, son 6 en total.
    expect(screen.getAllByText("Fallo simulado de red")).toHaveLength(6);
  });

  it("sin ninguna propuesta calculable muestra los mensajes vacíos, no un gráfico roto", async () => {
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/propuestas")) {
        return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 500 });
      }
      return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 500 });
    });
    render(<Dashboard />);

    await waitFor(() => expect(screen.queryAllByText("Cargando…").length).toBe(0));

    expect(screen.getAllByText(/Todavía no hay/).length).toBeGreaterThan(0);
  });
});
