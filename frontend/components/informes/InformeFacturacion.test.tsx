import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import type {
  InformeFacturacionDetalleFila,
  InformeFacturacionResumen,
  InformeFacturacionRespuesta,
} from "@/types/informeFacturacion";
import { InformeFacturacion } from "./InformeFacturacion";

const mockObtener = vi.fn();
const mockDescargarArchivo = vi.fn();
const mockDescargarEnNavegador = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
    crear: vi.fn(),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
    subirArchivo: vi.fn(),
    descargarArchivo: (...args: unknown[]) => mockDescargarArchivo(...args),
  },
}));

vi.mock("@/lib/archivos", () => ({
  descargarArchivoEnNavegador: (...args: unknown[]) => mockDescargarEnNavegador(...args),
}));

const CLIENTES_VACIO = { contenido: [], total: 0, pagina: 0, tamano: 50 };

const RESUMEN_BASE: InformeFacturacionResumen = {
  cantidadTotal: 4,
  cantidadPorEstado: { PENDIENTE: 1, PENDIENTE_UF: 1, FACTURADA: 1, ANULADA: 1 },
  cantidadPendienteUf: 1,
  netoClp: 300000,
  ivaClp: 57000,
  totalClp: 357000,
  porCliente: [],
};

const FILA_BASE: InformeFacturacionDetalleFila = {
  id: 1,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  proyectoId: null,
  proyectoNombre: null,
  descripcion: "Servicio A",
  periodoAnio: 2026,
  periodoMes: 2,
  fechaFacturacion: "2026-02-15",
  monedaOrigen: "CLP",
  valorUf: null,
  netoClp: 100000,
  ivaClp: 19000,
  totalClp: 119000,
  estado: "PENDIENTE",
  origen: "CICLO",
  facturaId: null,
  numeroFactura: null,
  fechaFactura: null,
};

const FILA_PENDIENTE_UF: InformeFacturacionDetalleFila = {
  ...FILA_BASE,
  id: 2,
  descripcion: "Servicio sin UF",
  estado: "PENDIENTE_UF",
  netoClp: 0,
  ivaClp: 0,
  totalClp: 0,
};

const FILA_FACTURADA: InformeFacturacionDetalleFila = {
  ...FILA_BASE,
  id: 3,
  descripcion: "Servicio facturado",
  estado: "FACTURADA",
  netoClp: 200000,
  ivaClp: 38000,
  totalClp: 238000,
  facturaId: 99,
  numeroFactura: "F-001",
  fechaFactura: "2026-03-01",
};

const FILA_ANULADA: InformeFacturacionDetalleFila = {
  ...FILA_BASE,
  id: 4,
  descripcion: "Servicio anulado",
  estado: "ANULADA",
};

/**
 * Misma fila PENDIENTE_UF, pero SIN la clave `valorUf` (en vez de `valorUf: null`) — la forma
 * real de la respuesta: toda la API omite un campo `null` en vez de mandarlo como
 * `"valorUf":null` (`jackson.default-property-inclusion: non_null`, docs/deuda-tecnica.md
 * ítem 5), así que tras `JSON.parse` el campo vale `undefined`, no `null`.
 */
const FILA_PENDIENTE_UF_SIN_CLAVE_VALOR_UF: InformeFacturacionDetalleFila = {
  ...FILA_PENDIENTE_UF,
  id: 5,
  descripcion: "Servicio sin UF (clave valorUf omitida)",
  valorUf: undefined,
};

const RESPUESTA_INFORME: InformeFacturacionRespuesta = {
  resumen: RESUMEN_BASE,
  detalle: {
    contenido: [FILA_BASE, FILA_PENDIENTE_UF, FILA_FACTURADA, FILA_ANULADA],
    total: 4,
    pagina: 0,
    tamano: 20,
  },
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <InformeFacturacion />
    </ProveedorNotificaciones>,
  );
}

function mockearInforme(respuesta: InformeFacturacionRespuesta) {
  mockObtener.mockImplementation((ruta: string) => {
    if (ruta.startsWith("/informes/facturacion")) {
      return Promise.resolve(respuesta);
    }
    if (ruta.startsWith("/clientes")) {
      return Promise.resolve(CLIENTES_VACIO);
    }
    return Promise.reject(new Error("ruta no mockeada: " + ruta));
  });
}

describe("InformeFacturacion", () => {
  beforeEach(() => {
    mockObtener.mockReset();
    mockDescargarArchivo.mockReset();
    mockDescargarEnNavegador.mockReset();
  });

  it("muestra los totales con la nota de exclusión y el desglose por estado", async () => {
    mockearInforme(RESPUESTA_INFORME);
    renderizar();

    expect(await screen.findByText("$357.000")).toBeInTheDocument();
    expect(screen.getByText("$300.000")).toBeInTheDocument();
    expect(screen.getByText("$57.000")).toBeInTheDocument();
    expect(
      screen.getByText("Solo Pendiente + Facturada — excluye Pendiente UF y Anulada"),
    ).toBeInTheDocument();

    expect(screen.getByText("Cantidad de propuestas por estado (4 en total)")).toBeInTheDocument();
  });

  it("destaca la cantidad de PENDIENTE_UF como advertencia y enlaza a Propuestas", async () => {
    mockearInforme(RESPUESTA_INFORME);
    renderizar();

    expect(
      await screen.findByText(/1 propuesta sin valor UF, no incluida en los totales/),
    ).toBeInTheDocument();
    const enlace = screen.getByRole("link", { name: /Ver en Propuestas/ });
    expect(enlace).toHaveAttribute("href", "/facturacion?estado=PENDIENTE_UF");
  });

  it("no muestra la advertencia de PENDIENTE_UF cuando la cantidad es 0", async () => {
    mockearInforme({
      ...RESPUESTA_INFORME,
      resumen: { ...RESUMEN_BASE, cantidadPendienteUf: 0 },
    });
    renderizar();

    await screen.findByText("$357.000");
    expect(screen.queryByText(/sin valor UF/)).not.toBeInTheDocument();
  });

  it("el detalle muestra '— (sin UF)' en la fila PENDIENTE_UF, no el 0 real", async () => {
    mockearInforme(RESPUESTA_INFORME);
    renderizar();

    await screen.findByText("Servicio sin UF");
    const filas = await screen.findAllByRole("row");
    const filaSinUf = filas.find((fila) => within(fila).queryByText("Servicio sin UF") !== null);
    expect(filaSinUf).toBeDefined();
    // Neto, IVA y Total de esa fila.
    expect(within(filaSinUf!).getAllByText("— (sin UF)")).toHaveLength(3);
    expect(within(filaSinUf!).queryByText("$0")).not.toBeInTheDocument();
  });

  it("la columna Valor UF muestra '—', nunca NaN, cuando el backend omite la clave valorUf", async () => {
    mockearInforme({
      resumen: RESUMEN_BASE,
      detalle: {
        contenido: [FILA_PENDIENTE_UF_SIN_CLAVE_VALOR_UF],
        total: 1,
        pagina: 0,
        tamano: 20,
      },
    });
    renderizar();

    const fila = (await screen.findAllByRole("row")).find(
      (f) => within(f).queryByText("Servicio sin UF (clave valorUf omitida)") !== null,
    );
    if (!fila) {
      throw new Error("No se encontró la fila de prueba");
    }
    expect(within(fila).queryByText(/NaN/)).not.toBeInTheDocument();
    expect(within(fila).getAllByText("—").length).toBeGreaterThanOrEqual(1);
  });

  it("el N° factura aparece en la fila FACTURADA y no en las demás", async () => {
    mockearInforme(RESPUESTA_INFORME);
    renderizar();

    expect(await screen.findByText("F-001")).toBeInTheDocument();
    const filas = await screen.findAllByRole("row");
    const filaPendiente = filas.find((fila) => within(fila).queryByText("Servicio A") !== null);
    expect(within(filaPendiente!).queryByText("F-001")).not.toBeInTheDocument();
  });

  it("los filtros acotan: cambiar el año recarga resumen y detalle con el nuevo filtro", async () => {
    mockearInforme(RESPUESTA_INFORME);
    const usuario = userEvent.setup();
    renderizar();

    await screen.findByText("Servicio A");
    mockObtener.mockClear();

    await usuario.type(screen.getByLabelText("Año"), "2026");

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/informes/facturacion"));
      expect(llamadas.some((ruta) => ruta.includes("periodoAnio=2026"))).toBe(true);
    });
  });

  it("la paginación pide la página siguiente al backend", async () => {
    mockearInforme({
      resumen: RESUMEN_BASE,
      detalle: { contenido: [FILA_BASE], total: 40, pagina: 0, tamano: 20 },
    });
    const usuario = userEvent.setup();
    renderizar();

    await screen.findByText("Servicio A");
    mockObtener.mockClear();

    await usuario.click(screen.getByRole("button", { name: "Siguiente" }));

    await waitFor(() => {
      const llamadas = mockObtener.mock.calls
        .map((llamada) => llamada[0] as string)
        .filter((ruta) => ruta.startsWith("/informes/facturacion"));
      expect(llamadas.some((ruta) => ruta.includes("page=1"))).toBe(true);
    });
  });

  it("exportar usa el cliente API autenticado (no un enlace plano) y respeta los filtros activos", async () => {
    mockearInforme(RESPUESTA_INFORME);
    mockDescargarArchivo.mockResolvedValueOnce({
      blob: new Blob(["x"]),
      nombreArchivo: "informe-facturacion.csv",
    });
    const usuario = userEvent.setup();
    renderizar();

    await screen.findByText("Servicio A");
    await usuario.type(screen.getByLabelText("Año"), "2026");
    await usuario.selectOptions(screen.getByLabelText("Mes"), "2");

    // Ningún <a> debe apuntar directo al endpoint de exportación: no llevaría el token.
    expect(document.querySelector('a[href*="/export"]')).toBeNull();

    await usuario.click(screen.getByRole("button", { name: "Exportar CSV" }));

    await waitFor(() => expect(mockDescargarArchivo).toHaveBeenCalledTimes(1));
    const [ruta] = mockDescargarArchivo.mock.calls[0];
    expect(ruta).toContain("/informes/facturacion/export");
    expect(ruta).toContain("periodoAnio=2026");
    expect(ruta).toContain("periodoMes=2");

    await waitFor(() => expect(mockDescargarEnNavegador).toHaveBeenCalledTimes(1));
    const [{ nombreArchivo }] = mockDescargarEnNavegador.mock.calls[0];
    expect(nombreArchivo).toBe("informe-facturacion.csv");
  });

  it("usa el nombre descriptivo que entrega el backend en el Content-Disposition, sin recalcularlo en el cliente", async () => {
    mockearInforme(RESPUESTA_INFORME);
    mockDescargarArchivo.mockResolvedValueOnce({
      blob: new Blob(["x"]),
      nombreArchivo: "informe-facturacion-2026-01_2026-03.csv",
    });
    const usuario = userEvent.setup();
    renderizar();

    await screen.findByText("Servicio A");
    await usuario.click(screen.getByRole("button", { name: "Exportar CSV" }));

    await waitFor(() => expect(mockDescargarEnNavegador).toHaveBeenCalledTimes(1));
    const [{ nombreArchivo }] = mockDescargarEnNavegador.mock.calls[0];
    expect(nombreArchivo).toBe("informe-facturacion-2026-01_2026-03.csv");
  });
});
