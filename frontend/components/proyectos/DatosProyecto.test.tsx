import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProveedorProyectoDetalle } from "./ContextoProyectoDetalle";
import { DatosProyecto } from "./DatosProyecto";

const PROYECTO_DE_PRUEBA = {
  id: 1,
  clienteId: 10,
  clienteRazonSocial: "Cliente de Prueba SpA",
  tipoServicioId: 5,
  tipoServicioNombre: "Soporte",
  codigo: "PRJ-001",
  nombre: "Soporte mensual",
  descripcion: "Descripción de prueba",
  precioBaseNeto: 12,
  monedaPrecio: "UF" as const,
  periodicidad: "MENSUAL" as const,
  diaFacturacion: 15,
  fechaInicio: "2026-01-01",
  fechaTermino: null,
  activo: true,
};

function renderizar() {
  return render(
    <ProveedorProyectoDetalle value={{ proyecto: PROYECTO_DE_PRUEBA, recargar: () => {} }}>
      <DatosProyecto />
    </ProveedorProyectoDetalle>,
  );
}

describe("DatosProyecto", () => {
  it("muestra los campos de solo lectura del proyecto compartido por contexto", () => {
    renderizar();

    expect(screen.getByText("Cliente de Prueba SpA")).toBeInTheDocument();
    expect(screen.getByText("Soporte")).toBeInTheDocument();
    expect(screen.getByText("PRJ-001")).toBeInTheDocument();
    expect(screen.getByText("Descripción de prueba")).toBeInTheDocument();
    expect(screen.getByText("Mensual")).toBeInTheDocument();
    expect(screen.getByText("15")).toBeInTheDocument();
  });

  it("muestra un guion para los campos opcionales ausentes", () => {
    render(
      <ProveedorProyectoDetalle
        value={{
          proyecto: { ...PROYECTO_DE_PRUEBA, tipoServicioNombre: null, codigo: null, descripcion: null, fechaTermino: null },
          recargar: () => {},
        }}
      >
        <DatosProyecto />
      </ProveedorProyectoDetalle>,
    );

    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(3);
  });
});
