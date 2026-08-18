import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import { EjecutarCiclo } from "./EjecutarCiclo";

const mockUseSession = vi.fn();
const mockCrear = vi.fn();

vi.mock("next-auth/react", () => ({
  useSession: () => mockUseSession(),
}));

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
  return render(
    <ProveedorNotificaciones>
      <EjecutarCiclo />
    </ProveedorNotificaciones>,
  );
}

describe("EjecutarCiclo", () => {
  beforeEach(() => {
    mockCrear.mockReset();
  });

  it("el botón de ejecutar está deshabilitado para OPERADOR", () => {
    mockUseSession.mockReturnValue({ data: { roles: ["OPERADOR"] }, status: "authenticated" });

    renderizar();

    expect(screen.getByRole("button", { name: "Ejecutar ciclo" })).toBeDisabled();
    expect(screen.getByText("Esta acción requiere el rol ADMINISTRADOR.")).toBeInTheDocument();
  });

  it("muestra las PENDIENTE_UF como advertencia, no como éxito silencioso", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    mockCrear.mockResolvedValueOnce({
      ejecucionId: 1,
      anio: 2026,
      mes: 2,
      disparo: "MANUAL",
      estado: "CON_ADVERTENCIAS",
      cantidadGeneradas: 5,
      cantidadPendientesUf: 2,
      observacion: "Propuestas generadas: 5. Pendientes de UF: 2. Proyectos con error: 0.",
    });
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(screen.getByRole("button", { name: "Ejecutar ciclo" }));
    await usuario.click(screen.getByRole("button", { name: "Ejecutar" }));

    expect(await screen.findByText("Con advertencias")).toBeInTheDocument();
    expect(
      screen.getByText(/2 propuestas quedaron sin valor UF disponible para su fecha/),
    ).toBeInTheDocument();
    // El enlace filtra por PENDIENTE_UF, ya que las hubo.
    expect(screen.getByRole("link", { name: /Ver propuestas de este período/ })).toHaveAttribute(
      "href",
      "/facturacion?periodoAnio=2026&periodoMes=2&estado=PENDIENTE_UF",
    );
  });

  it("un resultado EXITOSA sin pendientes no muestra la advertencia", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    mockCrear.mockResolvedValueOnce({
      ejecucionId: 2,
      anio: 2026,
      mes: 3,
      disparo: "MANUAL",
      estado: "EXITOSA",
      cantidadGeneradas: 3,
      cantidadPendientesUf: 0,
      observacion: null,
    });
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(screen.getByRole("button", { name: "Ejecutar ciclo" }));
    await usuario.click(screen.getByRole("button", { name: "Ejecutar" }));

    expect(await screen.findByText("Exitosa")).toBeInTheDocument();
    expect(screen.queryByText(/sin valor UF disponible/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Ver propuestas de este período/ })).toHaveAttribute(
      "href",
      "/facturacion?periodoAnio=2026&periodoMes=3",
    );
  });

  it("con ejecucionId omitido (lock ya tomado, forma real de la respuesta) muestra el aviso correcto, no el enlace", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    // SIN la clave `ejecucionId` — la forma real de la respuesta cuando el backend omite la
    // corrida porque ya había otra en curso: toda la API omite un campo `null` en vez de
    // mandarlo como `"ejecucionId":null` (`jackson.default-property-inclusion: non_null`,
    // docs/deuda-tecnica.md ítem 5), así que tras `JSON.parse` vale `undefined`, no `null`.
    // Antes del fix, esto hacía tomar la rama equivocada: mostraba el enlace "Ver propuestas"
    // como si sí se hubiera generado algo nuevo.
    mockCrear.mockResolvedValueOnce({
      anio: 2026,
      mes: 5,
      disparo: "MANUAL",
      estado: "EXITOSA",
      cantidadGeneradas: 0,
      cantidadPendientesUf: 0,
      observacion: "Ya había una ejecución en curso para este período; no se realizó ningún cambio.",
    });
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(screen.getByRole("button", { name: "Ejecutar ciclo" }));
    await usuario.click(screen.getByRole("button", { name: "Ejecutar" }));

    // El mensaje puede aparecer dos veces (la `observacion` real del backend coincide texto a
    // texto con el aviso fijo de esta rama) — lo que importa para este bug es que NINGUNA sea
    // el enlace "Ver propuestas", que asumiría que sí se generó algo nuevo.
    expect(
      (await screen.findAllByText(
        "Ya había una ejecución en curso para este período; no se realizó ningún cambio.",
      )).length,
    ).toBeGreaterThanOrEqual(1);
    expect(
      screen.queryByRole("link", { name: /Ver propuestas de este período/ }),
    ).not.toBeInTheDocument();
  });

  it("muestra un estado de carga mientras la ejecución está en curso", async () => {
    mockUseSession.mockReturnValue({ data: { roles: ["ADMINISTRADOR"] }, status: "authenticated" });
    let resolver: (valor: unknown) => void = () => {};
    mockCrear.mockReturnValueOnce(
      new Promise((resolve) => {
        resolver = resolve;
      }),
    );
    const usuario = userEvent.setup();
    renderizar();

    await usuario.click(screen.getByRole("button", { name: "Ejecutar ciclo" }));
    await usuario.click(screen.getByRole("button", { name: "Ejecutar" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Ejecutar ciclo" })).toBeDisabled(),
    );

    resolver({
      ejecucionId: 3,
      anio: 2026,
      mes: 4,
      disparo: "MANUAL",
      estado: "EXITOSA",
      cantidadGeneradas: 1,
      cantidadPendientesUf: 0,
      observacion: null,
    });

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Ejecutar ciclo" })).toBeEnabled(),
    );
  });
});
