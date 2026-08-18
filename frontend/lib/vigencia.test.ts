import { describe, expect, it } from "vitest";
import { calcularEstadoVigencia, calcularTerminoDesdeMeses, seSuperponen } from "./vigencia";

describe("calcularEstadoVigencia", () => {
  it("es FUTURO cuando hoy es anterior al inicio", () => {
    expect(calcularEstadoVigencia("2026-05-01", "2026-06-30", "2026-04-01")).toBe("FUTURO");
  });

  it("es PASADO cuando hoy es posterior al término", () => {
    expect(calcularEstadoVigencia("2026-01-01", "2026-02-28", "2026-03-01")).toBe("PASADO");
  });

  it("es VIGENTE cuando hoy cae dentro del rango, bordes incluidos", () => {
    expect(calcularEstadoVigencia("2026-01-01", "2026-02-28", "2026-01-01")).toBe("VIGENTE");
    expect(calcularEstadoVigencia("2026-01-01", "2026-02-28", "2026-02-28")).toBe("VIGENTE");
    expect(calcularEstadoVigencia("2026-01-01", "2026-02-28", "2026-01-15")).toBe("VIGENTE");
  });
});

describe("seSuperponen", () => {
  it("detecta superposición parcial", () => {
    expect(seSuperponen("2026-01-01", "2026-03-31", "2026-03-01", "2026-06-30")).toBe(true);
  });

  it("detecta que compartir exactamente un día de borde es superposición", () => {
    expect(seSuperponen("2026-01-01", "2026-03-31", "2026-03-31", "2026-06-30")).toBe(true);
  });

  it("no detecta superposición cuando los rangos no se tocan", () => {
    expect(seSuperponen("2026-01-01", "2026-03-31", "2026-04-01", "2026-06-30")).toBe(false);
  });
});

describe("calcularTerminoDesdeMeses", () => {
  it("resta un día tras sumar los meses pactados", () => {
    expect(calcularTerminoDesdeMeses("2026-01-01", 3)).toBe("2026-03-31");
  });

  it("recorta al último día del mes cuando el día de inicio no existe en el mes resultante", () => {
    // 31 de enero + 1 mes -> 28 de febrero de 2026 (no bisiesto) -> -1 día -> 27 de febrero.
    expect(calcularTerminoDesdeMeses("2026-01-31", 1)).toBe("2026-02-27");
  });
});
