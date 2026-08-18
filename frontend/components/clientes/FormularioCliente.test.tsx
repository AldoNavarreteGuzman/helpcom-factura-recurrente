import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { FormularioCliente } from "./FormularioCliente";

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
      <FormularioCliente cliente={null} onCerrar={onCerrar} onExito={onExito} />
    </ProveedorNotificaciones>,
  );
  return { onCerrar, onExito };
}

describe("FormularioCliente", () => {
  beforeEach(() => {
    mockCrear.mockReset();
  });

  it("marca el RUT inválido antes de enviar, sin llamar al cliente API", async () => {
    const usuario = userEvent.setup();
    renderizar();

    // Dígito verificador incorrecto a propósito (el correcto para "11111111" es "1").
    await usuario.type(screen.getByLabelText(/^RUT/), "11111111-2");
    await usuario.type(screen.getByLabelText(/^Razón social/), "Cliente de Prueba SpA");
    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    expect(await screen.findByText("El RUT no es válido.")).toBeInTheDocument();
    expect(mockCrear).not.toHaveBeenCalled();
  });

  it("permite enviar con un RUT válido y normaliza el formato antes de enviar", async () => {
    mockCrear.mockResolvedValueOnce({ id: 1 });
    const usuario = userEvent.setup();
    const { onExito } = renderizar();

    // 11.111.111-1 es válido: cuerpo "11111111", dígito verificador calculado en módulo 11.
    await usuario.type(screen.getByLabelText(/^RUT/), "11.111.111-1");
    await usuario.type(screen.getByLabelText(/^Razón social/), "Cliente de Prueba SpA");
    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    await waitFor(() => expect(mockCrear).toHaveBeenCalledTimes(1));
    expect(mockCrear).toHaveBeenCalledWith(
      "/clientes",
      expect.objectContaining({ rut: "11111111-1", razonSocial: "Cliente de Prueba SpA" }),
    );
    await waitFor(() => expect(onExito).toHaveBeenCalled());
  });

  it("muestra el error de un campo específico cuando el backend responde 400 con ese campo en 'errores'", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(400, {
        detail: "Uno o más campos no cumplen las validaciones requeridas.",
        codigo: "VALIDACION_CAMPOS",
        errores: ["razonSocial"],
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.type(screen.getByLabelText(/^RUT/), "11.111.111-1");
    await usuario.type(screen.getByLabelText(/^Razón social/), "Cliente de Prueba SpA");
    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    expect(
      await screen.findAllByText("Uno o más campos no cumplen las validaciones requeridas."),
    ).not.toHaveLength(0);
  });

  it("muestra el mensaje de duplicado (409) aunque no mapee a un campo específico", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(409, {
        detail: "Ya existe un cliente con el RUT 11111111-1.",
        codigo: "CLIENTE_DUPLICADO",
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.type(screen.getByLabelText(/^RUT/), "11.111.111-1");
    await usuario.type(screen.getByLabelText(/^Razón social/), "Cliente de Prueba SpA");
    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    expect(
      await screen.findAllByText("Ya existe un cliente con el RUT 11111111-1."),
    ).not.toHaveLength(0);
  });
});
