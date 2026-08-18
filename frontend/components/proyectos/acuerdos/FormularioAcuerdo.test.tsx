import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { FormularioAcuerdo } from "./FormularioAcuerdo";

const mockCrear = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: vi.fn(),
    crear: (...args: unknown[]) => mockCrear(...args),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
  },
}));

function renderizar() {
  const onCerrar = vi.fn();
  const onExito = vi.fn();
  render(
    <ProveedorNotificaciones>
      <FormularioAcuerdo
        proyectoId={1}
        acuerdo={null}
        acuerdosExistentes={[]}
        onCerrar={onCerrar}
        onExito={onExito}
      />
    </ProveedorNotificaciones>,
  );
  return { onCerrar, onExito };
}

describe("FormularioAcuerdo — campos condicionales por tipo", () => {
  beforeEach(() => {
    mockCrear.mockReset();
  });

  it("DESCUENTO_PORCENTAJE no muestra el selector de moneda", () => {
    renderizar();
    // Es el tipo por defecto.
    expect(screen.queryByLabelText(/^Moneda/)).not.toBeInTheDocument();
  });

  it("DESCUENTO_MONTO muestra el selector de moneda, obligatorio", async () => {
    const usuario = userEvent.setup();
    renderizar();

    await usuario.selectOptions(screen.getByLabelText(/^Tipo de acuerdo/), "DESCUENTO_MONTO");

    expect(screen.getByLabelText(/^Moneda/)).toBeInTheDocument();
  });

  it("PRECIO_PACTADO muestra el selector de moneda y la aclaración de que reemplaza el precio base", async () => {
    const usuario = userEvent.setup();
    renderizar();

    await usuario.selectOptions(screen.getByLabelText(/^Tipo de acuerdo/), "PRECIO_PACTADO");

    expect(screen.getByLabelText(/^Moneda/)).toBeInTheDocument();
    expect(screen.getByText(/REEMPLAZA al precio base/)).toBeInTheDocument();
  });
});

describe("FormularioAcuerdo — modo de vigencia", () => {
  beforeEach(() => {
    mockCrear.mockReset();
  });

  it("alterna entre fecha de término y meses pactados, calculando el término desde los meses", async () => {
    const usuario = userEvent.setup();
    renderizar();

    expect(screen.getByLabelText(/^Fecha de término/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Meses pactados/)).not.toBeInTheDocument();

    await usuario.selectOptions(screen.getByLabelText(/^Vigencia/), "meses");

    expect(screen.queryByLabelText(/^Fecha de término/)).not.toBeInTheDocument();
    const campoMeses = screen.getByLabelText(/^Meses pactados/);
    expect(campoMeses).toBeInTheDocument();

    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-01-01");
    await usuario.type(campoMeses, "3");

    expect(await screen.findByText(/Término calculado: 31-03-2026/)).toBeInTheDocument();
  });

  it("envía mesesPactados (no fechaTermino) cuando el modo es 'meses'", async () => {
    mockCrear.mockResolvedValueOnce({ id: 1 });
    const usuario = userEvent.setup();
    const { onExito } = renderizar();

    await usuario.type(screen.getByLabelText(/^Porcentaje de descuento/), "10");
    await usuario.selectOptions(screen.getByLabelText(/^Vigencia/), "meses");
    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-01-01");
    await usuario.type(screen.getByLabelText(/^Meses pactados/), "3");

    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    await waitFor(() => expect(mockCrear).toHaveBeenCalledTimes(1));
    expect(mockCrear).toHaveBeenCalledWith(
      "/proyectos/1/acuerdos",
      expect.objectContaining({ mesesPactados: 3, fechaTermino: null }),
    );
    await waitFor(() => expect(onExito).toHaveBeenCalled());
  });
});

describe("FormularioAcuerdo — 409 ACUERDO_TRASLAPADO", () => {
  beforeEach(() => {
    mockCrear.mockReset();
  });

  it("muestra el detail del conflicto en vez de un error genérico", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(409, {
        detail: "La vigencia se traslapa con el acuerdo 5 (2026-01-01 a 2026-06-30).",
        codigo: "ACUERDO_TRASLAPADO",
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.type(screen.getByLabelText(/^Porcentaje de descuento/), "10");
    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-01-01");
    await usuario.type(screen.getByLabelText(/^Fecha de término/), "2026-12-31");

    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    const mensajes = await screen.findAllByText(
      "La vigencia se traslapa con el acuerdo 5 (2026-01-01 a 2026-06-30).",
    );
    expect(mensajes.length).toBeGreaterThan(0);
  });
});
