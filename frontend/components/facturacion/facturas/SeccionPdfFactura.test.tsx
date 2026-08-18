import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import type { Factura } from "@/types/factura";
import { SeccionPdfFactura } from "./SeccionPdfFactura";

const mockSubirArchivo = vi.fn();
const mockDescargarArchivo = vi.fn();
const mockDescargarEnNavegador = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: vi.fn(),
    crear: vi.fn(),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
    subirArchivo: (...args: unknown[]) => mockSubirArchivo(...args),
    descargarArchivo: (...args: unknown[]) => mockDescargarArchivo(...args),
  },
}));

vi.mock("@/lib/archivos", () => ({
  descargarArchivoEnNavegador: (...args: unknown[]) => mockDescargarEnNavegador(...args),
}));

const FACTURA_SIN_PDF: Factura = {
  id: 1,
  numeroFactura: "F-001",
  fechaFactura: "2026-03-01",
  observacion: null,
  clienteId: 10,
  clienteRazonSocial: "Cliente A SpA",
  tienePdf: false,
  nombreArchivoPdf: null,
  propuestas: [],
};

const FACTURA_CON_PDF: Factura = {
  ...FACTURA_SIN_PDF,
  tienePdf: true,
  nombreArchivoPdf: "factura-F-001.pdf",
};

function renderizar(factura: Factura) {
  const onActualizada = vi.fn();
  render(
    <ProveedorNotificaciones>
      <SeccionPdfFactura factura={factura} onActualizada={onActualizada} />
    </ProveedorNotificaciones>,
  );
  return { onActualizada };
}

function archivoDePrueba(nombre: string, tipo: string): File {
  return new File(["contenido"], nombre, { type: tipo });
}

describe("SeccionPdfFactura", () => {
  beforeEach(() => {
    mockSubirArchivo.mockReset();
    mockDescargarArchivo.mockReset();
    mockDescargarEnNavegador.mockReset();
  });

  it("sin PDF: muestra el control de subida, no el de descarga", () => {
    renderizar(FACTURA_SIN_PDF);

    expect(screen.getByRole("button", { name: "Subir PDF" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Descargar" })).not.toBeInTheDocument();
  });

  it("valida en el cliente que el archivo sea application/pdf, sin llamar al backend", async () => {
    const usuario = userEvent.setup();
    renderizar(FACTURA_SIN_PDF);

    const input = screen.getByLabelText("Archivo PDF");
    // `userEvent.upload` respeta el atributo `accept` del input (como haría un selector de
    // archivos real) y filtraría este .txt sin disparar el evento; se usa `fireEvent` para
    // simular el caso que SÍ debe bloquear la validación en cliente: un archivo que llegó
    // igual (p. ej. arrastrado) sin pasar por el selector nativo.
    fireEvent.change(input, {
      target: { files: [archivoDePrueba("documento.txt", "text/plain")] },
    });

    expect(
      await screen.findByText("Solo se aceptan archivos PDF (application/pdf)."),
    ).toBeInTheDocument();

    await usuario.click(screen.getByRole("button", { name: "Subir PDF" }));
    expect(mockSubirArchivo).not.toHaveBeenCalled();
  });

  it("sube un PDF válido y propaga la factura actualizada", async () => {
    const actualizada = { ...FACTURA_SIN_PDF, tienePdf: true, nombreArchivoPdf: "nuevo.pdf" };
    mockSubirArchivo.mockResolvedValueOnce(actualizada);
    const usuario = userEvent.setup();
    const { onActualizada } = renderizar(FACTURA_SIN_PDF);

    const input = screen.getByLabelText("Archivo PDF");
    await usuario.upload(input, archivoDePrueba("nuevo.pdf", "application/pdf"));
    await usuario.click(screen.getByRole("button", { name: "Subir PDF" }));

    await waitFor(() =>
      expect(mockSubirArchivo).toHaveBeenCalledWith("/facturas/1/pdf", "archivo", expect.any(File)),
    );
    await waitFor(() => expect(onActualizada).toHaveBeenCalledWith(actualizada));
  });

  it("con PDF: la descarga usa el cliente API autenticado, nunca un enlace directo al backend", async () => {
    mockDescargarArchivo.mockResolvedValueOnce({
      blob: new Blob(["x"]),
      nombreArchivo: "factura-F-001.pdf",
    });
    const usuario = userEvent.setup();
    renderizar(FACTURA_CON_PDF);

    // Ningún <a> debe apuntar directo al endpoint del PDF: no llevaría el header Authorization.
    expect(document.querySelector('a[href*="/pdf"]')).toBeNull();

    await usuario.click(screen.getByRole("button", { name: "Descargar" }));

    await waitFor(() =>
      expect(mockDescargarArchivo).toHaveBeenCalledWith("/facturas/1/pdf", "factura-F-001.pdf"),
    );
    await waitFor(() => expect(mockDescargarEnNavegador).toHaveBeenCalled());
  });

  it("reemplazar muestra el control de subida y sube el PDF nuevo", async () => {
    const actualizada = { ...FACTURA_CON_PDF, nombreArchivoPdf: "reemplazo.pdf" };
    mockSubirArchivo.mockResolvedValueOnce(actualizada);
    const usuario = userEvent.setup();
    const { onActualizada } = renderizar(FACTURA_CON_PDF);

    await usuario.click(screen.getByRole("button", { name: "Reemplazar" }));
    const input = await screen.findByLabelText("Archivo PDF");
    await usuario.upload(input, archivoDePrueba("reemplazo.pdf", "application/pdf"));
    await usuario.click(screen.getByRole("button", { name: "Reemplazar PDF" }));

    await waitFor(() =>
      expect(mockSubirArchivo).toHaveBeenCalledWith("/facturas/1/pdf", "archivo", expect.any(File)),
    );
    await waitFor(() => expect(onActualizada).toHaveBeenCalledWith(actualizada));
  });
});
