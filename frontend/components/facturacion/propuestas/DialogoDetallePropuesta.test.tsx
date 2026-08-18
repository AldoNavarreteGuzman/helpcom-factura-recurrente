import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { DialogoDetallePropuesta } from "./DialogoDetallePropuesta";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

const PROPUESTA_PENDIENTE_UF: PropuestaFacturacion = {
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
 * Misma propuesta PENDIENTE_UF, pero SIN la clave `valorUf` (en vez de `valorUf: null`) — la
 * forma real de la respuesta: toda la API omite un campo `null` en vez de mandarlo como
 * `"valorUf":null` (`jackson.default-property-inclusion: non_null`, docs/deuda-tecnica.md
 * ítem 5), así que tras `JSON.parse` el campo vale `undefined`, no `null`. Nota: en este sitio
 * puntual, `fechaValorUf` (también omitido en este caso real — `ArmadorPropuesta` los pone en
 * null SIEMPRE juntos, nunca uno sin el otro) ya protegía este caso vía el `&&` del componente
 * — a diferencia de `ListaPropuestas`/`InformeFacturacion`, este render YA daba "— (sin UF)"
 * incluso antes del fix. El segundo test de abajo sí aísla la comparación de `valorUf` sola.
 */
const PROPUESTA_SIN_CLAVE_VALOR_UF: PropuestaFacturacion = {
  ...PROPUESTA_PENDIENTE_UF,
  valorUf: undefined,
};

describe("DialogoDetallePropuesta", () => {
  it("Valor UF muestra '— (sin UF)', nunca NaN, cuando el backend omite la clave valorUf (caso real)", () => {
    render(<DialogoDetallePropuesta propuesta={PROPUESTA_SIN_CLAVE_VALOR_UF} onCerrar={vi.fn()} />);

    const fila = screen.getByText("Valor UF").closest("div");
    if (!fila) {
      throw new Error("No se encontró la fila 'Valor UF'");
    }
    expect(within(fila).queryByText(/NaN/)).not.toBeInTheDocument();
    expect(within(fila).getByText("— (sin UF)")).toBeInTheDocument();
  });

  it("con valorUf omitido pero fechaValorUf presente, igual muestra '— (sin UF)' (aísla el `!= null` de esta línea)", () => {
    // Combinación que `ArmadorPropuesta` no produce hoy (siempre pone ambos en null juntos),
    // pero que el tipo permite — prueba la comparación de `valorUf` en esta línea de forma
    // aislada, sin depender de que `fechaValorUf` la proteja también.
    const propuesta = { ...PROPUESTA_SIN_CLAVE_VALOR_UF, fechaValorUf: "2026-02-15" };
    render(<DialogoDetallePropuesta propuesta={propuesta} onCerrar={vi.fn()} />);

    const fila = screen.getByText("Valor UF").closest("div");
    if (!fila) {
      throw new Error("No se encontró la fila 'Valor UF'");
    }
    expect(within(fila).queryByText(/NaN/)).not.toBeInTheDocument();
    expect(within(fila).getByText("— (sin UF)")).toBeInTheDocument();
  });
});
