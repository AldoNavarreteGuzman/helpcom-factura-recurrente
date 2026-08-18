import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import type {
  ImportacionCsv,
  ImportacionPreview,
  ImportacionPreviewFila,
} from "@/types/importacionCsv";
import { ImportarCsv } from "./ImportarCsv";

const mockSubirArchivo = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: vi.fn(),
    crear: vi.fn(),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
    subirArchivo: (...args: unknown[]) => mockSubirArchivo(...args),
    descargarArchivo: vi.fn(),
  },
}));

const FILA_OK: ImportacionPreviewFila = {
  numeroFila: 2,
  estado: "OK",
  mensajes: [],
  rutCliente: "76543210-9",
  codigoProyecto: null,
  descripcion: "Servicio mantención enero",
  periodo: "2026-01",
  fechaFacturacion: "15-01-2026",
  moneda: "UF",
  montoNeto: "12.5",
  observacion: null,
  netoClp: 500000,
  ivaClp: 95000,
  totalClp: 595000,
};

const FILA_ADVERTENCIA_PERIODO: ImportacionPreviewFila = {
  numeroFila: 3,
  estado: "ADVERTENCIA",
  mensajes: [
    "La fecha de facturación (20-02-2026) no coincide con el período informado (2026-01).",
  ],
  rutCliente: "77111222-3",
  codigoProyecto: "PRJ-014",
  descripcion: "Soporte anual",
  periodo: "2026-01",
  fechaFacturacion: "20-02-2026",
  moneda: "CLP",
  montoNeto: "850000",
  observacion: null,
  netoClp: 850000,
  ivaClp: 161500,
  totalClp: 1011500,
};

const FILA_SIN_UF: ImportacionPreviewFila = {
  numeroFila: 4,
  estado: "ADVERTENCIA",
  mensajes: [
    "UF no disponible para la fecha 2026-01-15: la propuesta quedará en estado PENDIENTE_UF.",
  ],
  rutCliente: "76543210-9",
  codigoProyecto: null,
  descripcion: "Servicio sin UF",
  periodo: "2026-01",
  fechaFacturacion: "15-01-2026",
  moneda: "UF",
  montoNeto: "10",
  observacion: null,
  netoClp: 0,
  ivaClp: 0,
  totalClp: 0,
};

const FILA_ERROR: ImportacionPreviewFila = {
  numeroFila: 5,
  estado: "ERROR",
  mensajes: ["No existe un cliente activo con RUT 11111111-1."],
  rutCliente: "11111111-1",
  codigoProyecto: null,
  descripcion: "Servicio inexistente",
  periodo: "2026-01",
  fechaFacturacion: "10-01-2026",
  moneda: "CLP",
  montoNeto: "100000",
  observacion: null,
  netoClp: null,
  ivaClp: null,
  totalClp: null,
};

const PREVIEW_MEZCLA: ImportacionPreview = {
  resumen: { totalFilas: 4, filasOk: 1, filasAdvertencia: 2, filasError: 1 },
  filas: [FILA_OK, FILA_ADVERTENCIA_PERIODO, FILA_SIN_UF, FILA_ERROR],
};

// cantidadPendienteUf REAL (2) a propósito distinto del estimado que arroja la
// previsualización de PREVIEW_MEZCLA (1, por FILA_SIN_UF) — confirmar revalidó y encontró una
// segunda fila sin UF que la previsualización no había marcado así.
const RESULTADO_PARCIAL: ImportacionCsv = {
  id: 10,
  nombreArchivo: "importacion.csv",
  fechaImportacion: "2026-03-01T10:00:00-03:00",
  totalFilas: 4,
  filasOk: 3,
  cantidadPendienteUf: 2,
  filasError: 1,
  estado: "PARCIAL",
};

function renderizar() {
  return render(
    <ProveedorNotificaciones>
      <ImportarCsv />
    </ProveedorNotificaciones>,
  );
}

async function seleccionarArchivoValido(
  usuario: ReturnType<typeof userEvent.setup>,
  nombre = "importacion.csv",
): Promise<File> {
  const archivo = new File(["contenido"], nombre, { type: "text/csv" });
  await usuario.upload(screen.getByLabelText("Archivo CSV"), archivo);
  return archivo;
}

/**
 * "Error"/"OK" también aparecen como texto del badge de estado en la tabla, así que no basta
 * con `getByText`; se filtra por la estructura propia del widget de resumen (etiqueta seguida
 * de un `.text-lg` con el valor).
 */
function valorResumen(etiqueta: string): string | null {
  const candidatos = screen.getAllByText(etiqueta);
  for (const candidato of candidatos) {
    const valor = candidato.parentElement?.querySelector(".text-lg");
    if (valor) {
      return valor.textContent;
    }
  }
  return null;
}

describe("ImportarCsv", () => {
  beforeEach(() => {
    mockSubirArchivo.mockReset();
  });

  it("valida en el cliente que el archivo sea .csv, sin llamar al backend", async () => {
    renderizar();

    const input = screen.getByLabelText("Archivo CSV");
    // `userEvent.upload` respeta el `accept` del input y filtraría un .txt; se usa
    // `fireEvent` para simular el caso que sí debe bloquear la validación en cliente.
    fireEvent.change(input, {
      target: { files: [new File(["x"], "datos.txt", { type: "text/plain" })] },
    });

    expect(await screen.findByText("Selecciona un archivo .csv.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Previsualizar" })).toBeDisabled();
  });

  it("previsualiza y muestra el resumen + la tabla con estados mezclados", async () => {
    mockSubirArchivo.mockResolvedValueOnce(PREVIEW_MEZCLA);
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));

    await screen.findByText("Servicio mantención enero");

    expect(valorResumen("Total filas")).toBe("4");
    expect(valorResumen("OK")).toBe("1");
    expect(valorResumen("Advertencia")).toBe("2");
    expect(valorResumen("Error")).toBe("1");

    // Motivo de la fila ERROR.
    expect(screen.getByText("No existe un cliente activo con RUT 11111111-1.")).toBeInTheDocument();

    // La fila "sin UF" muestra "— (sin UF)" en Neto/IVA/Total, nunca el 0 real.
    expect(await screen.findAllByText("— (sin UF)")).toHaveLength(3);

    // El cálculo real aparece en las filas válidas (OK y ADVERTENCIA no-UF).
    expect(screen.getByText("$595.000")).toBeInTheDocument();
    expect(screen.getByText("$1.011.500")).toBeInTheDocument();
  });

  it("un archivo global inválido muestra el mensaje del backend, sin tabla", async () => {
    mockSubirArchivo.mockRejectedValueOnce(
      new ErrorApi(400, {
        detail: "Faltan columnas obligatorias en el CSV: moneda",
        codigo: "CSV_COLUMNAS_FALTANTES",
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario, "malo.csv");
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));

    const mensajes = await screen.findAllByText("Faltan columnas obligatorias en el CSV: moneda");
    expect(mensajes.length).toBeGreaterThan(0);
    expect(screen.queryByText("Vista previa")).not.toBeInTheDocument();
  });

  it("el botón Confirmar está deshabilitado si todas las filas tienen error", async () => {
    mockSubirArchivo.mockResolvedValueOnce({
      resumen: { totalFilas: 1, filasOk: 0, filasAdvertencia: 0, filasError: 1 },
      filas: [FILA_ERROR],
    });
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));

    expect(await screen.findByRole("button", { name: "Confirmar importación" })).toBeDisabled();
    expect(
      screen.getByText("Todas las filas tienen error; no hay nada para importar."),
    ).toBeInTheDocument();
  });

  it("el diálogo de confirmación enuncia los conteos correctos", async () => {
    mockSubirArchivo.mockResolvedValueOnce(PREVIEW_MEZCLA);
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));
    await usuario.click(await screen.findByRole("button", { name: "Confirmar importación" }));

    expect(
      screen.getByText(
        /Se importarán 3 filas \(1 OK \+ 2 con advertencia\); 1 fila con error NO se importará\./,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/de las cuales ~1 \(estimado según esta previsualización\)/),
    ).toBeInTheDocument();
  });

  it("confirmar reenvía el mismo File usado en la previsualización", async () => {
    mockSubirArchivo.mockResolvedValueOnce(PREVIEW_MEZCLA);
    mockSubirArchivo.mockResolvedValueOnce(RESULTADO_PARCIAL);
    const usuario = userEvent.setup();
    renderizar();

    const archivo = await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));
    await usuario.click(await screen.findByRole("button", { name: "Confirmar importación" }));
    await usuario.click(screen.getByRole("button", { name: "Confirmar" }));

    await waitFor(() => expect(mockSubirArchivo).toHaveBeenCalledTimes(2));
    expect(mockSubirArchivo.mock.calls[0]).toEqual([
      "/importaciones/previsualizar",
      "archivo",
      archivo,
    ]);
    expect(mockSubirArchivo.mock.calls[1]).toEqual([
      "/importaciones/confirmar",
      "archivo",
      archivo,
    ]);
  });

  it("un resultado PARCIAL no se pinta como éxito, destaca PENDIENTE_UF y enlaza a Propuestas", async () => {
    mockSubirArchivo.mockResolvedValueOnce(PREVIEW_MEZCLA);
    mockSubirArchivo.mockResolvedValueOnce(RESULTADO_PARCIAL);
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));
    await usuario.click(await screen.findByRole("button", { name: "Confirmar importación" }));
    await usuario.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(await screen.findByText("Parcial")).toBeInTheDocument();
    expect(screen.getByText(/Solo se importó una parte del archivo/)).toBeInTheDocument();
    expect(screen.queryByText(/procesada correctamente/)).not.toBeInTheDocument();

    const enlace = screen.getByRole("link", { name: /Ver propuestas importadas/ });
    expect(enlace).toHaveAttribute("href", "/facturacion?origen=CSV&periodoAnio=2026&periodoMes=1");
  });

  it("el resultado muestra el contador REAL de PENDIENTE_UF del backend, no el estimado de la previsualización", async () => {
    // PREVIEW_MEZCLA estima 1 (FILA_SIN_UF); RESULTADO_PARCIAL.cantidadPendienteUf es 2 — el
    // backend revalidó al confirmar y el número real terminó siendo distinto.
    mockSubirArchivo.mockResolvedValueOnce(PREVIEW_MEZCLA);
    mockSubirArchivo.mockResolvedValueOnce(RESULTADO_PARCIAL);
    const usuario = userEvent.setup();
    renderizar();

    await seleccionarArchivoValido(usuario);
    await usuario.click(screen.getByRole("button", { name: "Previsualizar" }));
    await usuario.click(await screen.findByRole("button", { name: "Confirmar importación" }));
    await usuario.click(screen.getByRole("button", { name: "Confirmar" }));

    expect(
      await screen.findByText(/2 de las importadas quedaron en estado Pendiente UF/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/1 de las importadas quedaron en estado Pendiente UF/),
    ).not.toBeInTheDocument();
  });
});
