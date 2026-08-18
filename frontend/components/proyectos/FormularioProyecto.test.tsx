import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { ErrorApi } from "@/lib/clienteApi";
import { FormularioProyecto } from "./FormularioProyecto";

const mockCrear = vi.fn();
const mockObtener = vi.fn();

vi.mock("@/lib/clienteApiCliente", () => ({
  clienteApiCliente: {
    obtener: (...args: unknown[]) => mockObtener(...args),
    crear: (...args: unknown[]) => mockCrear(...args),
    actualizar: vi.fn(),
    actualizarParcial: vi.fn(),
    eliminar: vi.fn(),
  },
}));

const CLIENTE_DE_PRUEBA = {
  id: 10,
  rut: "11111111-1",
  razonSocial: "Cliente de Prueba SpA",
  activo: true,
};

function renderizar() {
  const onCerrar = vi.fn();
  const onExito = vi.fn();
  render(
    <ProveedorNotificaciones>
      <FormularioProyecto proyecto={null} onCerrar={onCerrar} onExito={onExito} />
    </ProveedorNotificaciones>,
  );
  return { onCerrar, onExito };
}

async function completarCamposObligatorios(usuario: ReturnType<typeof userEvent.setup>) {
  // Espera a que el selector de clientes termine de cargar la opción antes de elegirla.
  await screen.findByRole("option", { name: /Cliente de Prueba SpA/ });
  await usuario.selectOptions(screen.getByLabelText(/^Cliente/), "10");
  await usuario.type(screen.getByLabelText(/^Nombre/), "Soporte mensual");
  await usuario.type(screen.getByLabelText(/^Precio base neto/), "12");
}

describe("FormularioProyecto", () => {
  beforeEach(() => {
    mockCrear.mockReset();
    mockObtener.mockReset();
    mockObtener.mockImplementation((ruta: string) => {
      if (ruta.startsWith("/clientes")) {
        return Promise.resolve({ contenido: [CLIENTE_DE_PRUEBA], total: 1, pagina: 0, tamano: 50 });
      }
      if (ruta.startsWith("/tipos-servicio")) {
        return Promise.resolve({ contenido: [], total: 0, pagina: 0, tamano: 200 });
      }
      return Promise.reject(new Error("ruta no mockeada: " + ruta));
    });
  });

  it("marca la fecha de término anterior a la de inicio, sin llamar al cliente API", async () => {
    const usuario = userEvent.setup();
    renderizar();
    await completarCamposObligatorios(usuario);

    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-06-01");
    await usuario.type(screen.getByLabelText(/^Fecha de término/), "2026-05-01");
    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    expect(
      await screen.findByText(
        "La fecha de término debe ser igual o posterior a la fecha de inicio.",
      ),
    ).toBeInTheDocument();
    expect(mockCrear).not.toHaveBeenCalled();
  });

  it("envía la solicitud cuando las fechas son coherentes", async () => {
    mockCrear.mockResolvedValueOnce({ id: 1 });
    const usuario = userEvent.setup();
    const { onExito } = renderizar();
    await completarCamposObligatorios(usuario);

    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-01-01");

    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    await waitFor(() => expect(mockCrear).toHaveBeenCalledTimes(1));
    expect(mockCrear).toHaveBeenCalledWith(
      "/proyectos",
      expect.objectContaining({
        clienteId: 10,
        nombre: "Soporte mensual",
        fechaInicio: "2026-01-01",
      }),
    );
    await waitFor(() => expect(onExito).toHaveBeenCalled());
  });

  it("muestra el error de un campo específico cuando el backend responde 400 con ese campo en 'errores'", async () => {
    mockCrear.mockRejectedValueOnce(
      new ErrorApi(400, {
        detail: "Uno o más campos no cumplen las validaciones requeridas.",
        codigo: "VALIDACION_CAMPOS",
        errores: ["diaFacturacion"],
      }),
    );
    const usuario = userEvent.setup();
    renderizar();
    await completarCamposObligatorios(usuario);
    await usuario.type(screen.getByLabelText(/^Fecha de inicio/), "2026-01-01");

    await usuario.click(screen.getByRole("button", { name: "Guardar" }));

    expect(
      await screen.findAllByText("Uno o más campos no cumplen las validaciones requeridas."),
    ).not.toHaveLength(0);
  });
});
