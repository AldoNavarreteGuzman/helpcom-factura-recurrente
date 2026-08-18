import { describe, expect, it } from "vitest";
import {
  calcularKpiDescuentos,
  calcularMoM,
  calcularPorCliente,
  calcularPorProyecto,
  calcularSeriePorTipoServicio,
  calcularYoY,
  contarPendienteUf,
  SIN_CLASIFICAR,
  SIN_PROYECTO,
} from "./dashboardCalculos";
import type { Proyecto } from "@/types/proyecto";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

function propuesta(datos: Partial<PropuestaFacturacion>): PropuestaFacturacion {
  return {
    id: 1,
    clienteId: 1,
    clienteRazonSocial: "Cliente de Prueba SpA",
    proyectoId: null,
    proyectoNombre: null,
    origen: "CICLO",
    periodoAnio: 2026,
    periodoMes: 1,
    fechaFacturacion: "2026-01-15",
    descripcion: "Servicio",
    monedaOrigen: "CLP",
    precioBaseNeto: 100000,
    acuerdoTipo: null,
    acuerdoValor: null,
    acuerdoMoneda: null,
    valorUf: undefined,
    fechaValorUf: null,
    netoClp: 100000,
    tasaIva: 0.19,
    ivaClp: 19000,
    totalClp: 119000,
    estado: "PENDIENTE",
    numeroFactura: null,
    fechaFactura: null,
    ...datos,
  };
}

function proyecto(datos: Partial<Proyecto>): Proyecto {
  return {
    id: 1,
    clienteId: 1,
    clienteRazonSocial: "Cliente de Prueba SpA",
    tipoServicioId: null,
    tipoServicioNombre: null,
    codigo: null,
    nombre: "Proyecto",
    descripcion: null,
    precioBaseNeto: 12,
    monedaPrecio: "UF",
    periodicidad: "MENSUAL",
    diaFacturacion: 15,
    fechaInicio: "2026-01-01",
    fechaTermino: null,
    activo: true,
    ...datos,
  };
}

// Réplica del sembrado real de R9 (docs/frontend.md R9): 3 propuestas reprocesadas exitosamente
// con las cifras reales verificadas contra el backend (id 64/66/67).
const PROYECTO_1_SOPORTE = proyecto({
  id: 1,
  nombre: "Soporte mensual",
  tipoServicioId: 2,
  tipoServicioNombre: "Soporte y Mantención",
});
const PROYECTO_3_MANTENIMIENTO = proyecto({
  id: 3,
  nombre: "Mantenimiento de Sistemas",
  tipoServicioId: 2,
  tipoServicioNombre: "Soporte y Mantención",
});
const PROYECTO_4_CONSULTORIA = proyecto({
  id: 4,
  nombre: "Consultoría TI Mensual",
  tipoServicioId: 1,
  tipoServicioNombre: "SaaS Crux ERP",
});

const PROPUESTA_64_MAYO_DESCUENTO_PORCENTAJE = propuesta({
  id: 64,
  proyectoId: 1,
  proyectoNombre: "Soporte mensual",
  periodoMes: 5,
  monedaOrigen: "UF",
  precioBaseNeto: 12,
  acuerdoTipo: "DESCUENTO_PORCENTAJE",
  acuerdoValor: 10,
  valorUf: 40340.86,
  netoClp: 435681,
  estado: "PENDIENTE",
});
const PROPUESTA_65_JUNIO_DESCUENTO_PORCENTAJE = propuesta({
  id: 65,
  proyectoId: 1,
  proyectoNombre: "Soporte mensual",
  periodoMes: 6,
  monedaOrigen: "UF",
  precioBaseNeto: 12,
  acuerdoTipo: "DESCUENTO_PORCENTAJE",
  acuerdoValor: 10,
  valorUf: 40779.55,
  netoClp: 440419,
  estado: "PENDIENTE",
});
const PROPUESTA_5_JULIO_FACTURADA = propuesta({
  id: 5,
  proyectoId: 1,
  proyectoNombre: "Soporte mensual",
  periodoMes: 7,
  monedaOrigen: "UF",
  precioBaseNeto: 12,
  acuerdoTipo: "DESCUENTO_PORCENTAJE",
  acuerdoValor: 10,
  valorUf: 40844.79,
  netoClp: 441124,
  estado: "FACTURADA",
  numeroFactura: "12012",
});
const PROPUESTA_66_JUNIO_DESCUENTO_MONTO = propuesta({
  id: 66,
  proyectoId: 3,
  proyectoNombre: "Mantenimiento de Sistemas",
  periodoMes: 6,
  monedaOrigen: "UF",
  precioBaseNeto: 8,
  acuerdoTipo: "DESCUENTO_MONTO",
  acuerdoValor: 50000,
  acuerdoMoneda: "CLP",
  valorUf: 40765.97,
  netoClp: 276128,
  estado: "PENDIENTE",
});
const PROPUESTA_67_JUNIO_PRECIO_PACTADO = propuesta({
  id: 67,
  proyectoId: 4,
  proyectoNombre: "Consultoría TI Mensual",
  periodoMes: 6,
  monedaOrigen: "UF",
  precioBaseNeto: 6,
  acuerdoTipo: "PRECIO_PACTADO",
  acuerdoValor: 5,
  acuerdoMoneda: "UF",
  valorUf: 40793.13,
  netoClp: 203966,
  estado: "PENDIENTE",
});

const PROPUESTAS_CICLO_CALCULABLES = [
  PROPUESTA_64_MAYO_DESCUENTO_PORCENTAJE,
  PROPUESTA_65_JUNIO_DESCUENTO_PORCENTAJE,
  PROPUESTA_5_JULIO_FACTURADA,
  PROPUESTA_66_JUNIO_DESCUENTO_MONTO,
  PROPUESTA_67_JUNIO_PRECIO_PACTADO,
];
const PROYECTOS_CICLO = [PROYECTO_1_SOPORTE, PROYECTO_3_MANTENIMIENTO, PROYECTO_4_CONSULTORIA];

describe("calcularKpiDescuentos", () => {
  it("suma el descuento realizado por tipo con los montos reales del sembrado de R9", () => {
    const kpi = calcularKpiDescuentos(PROPUESTAS_CICLO_CALCULABLES);

    // 484.090 - 435.681 + 489.355 - 440.419 + 490.137 - 441.124 = 146.358 (redondeado HALF_UP
    // por mes antes de restar, igual que CalculadoraFacturacion en el backend).
    expect(kpi.porcentaje).toBe(146358);
    // 326.128 - 276.128 = 50.000
    expect(kpi.monto).toBe(50000);
    expect(kpi.totalPorcentajeYMonto).toBe(196358);
    // 244.759 - 203.966 = 40.793 — APARTE, no entra en totalPorcentajeYMonto.
    expect(kpi.pactado).toBe(40793);
  });

  it("no suma nada de una propuesta PENDIENTE_UF ni ANULADA aunque tenga acuerdo", () => {
    const conRotas = [
      ...PROPUESTAS_CICLO_CALCULABLES,
      propuesta({
        id: 999,
        estado: "PENDIENTE_UF",
        monedaOrigen: "UF",
        precioBaseNeto: 12,
        acuerdoTipo: "DESCUENTO_PORCENTAJE",
        acuerdoValor: 10,
        valorUf: undefined,
        netoClp: 0,
      }),
      propuesta({
        id: 998,
        estado: "ANULADA",
        monedaOrigen: "UF",
        precioBaseNeto: 12,
        acuerdoTipo: "DESCUENTO_PORCENTAJE",
        acuerdoValor: 10,
        valorUf: 40000,
        netoClp: 0,
      }),
    ];

    expect(calcularKpiDescuentos(conRotas)).toEqual(
      calcularKpiDescuentos(PROPUESTAS_CICLO_CALCULABLES),
    );
  });

  it("ignora propuestas sin acuerdo", () => {
    const sinAcuerdo = propuesta({
      id: 1,
      estado: "PENDIENTE",
      acuerdoTipo: null,
      netoClp: 850000,
    });
    const kpi = calcularKpiDescuentos([sinAcuerdo]);
    expect(kpi).toEqual({ porcentaje: 0, monto: 0, totalPorcentajeYMonto: 0, pactado: 0 });
  });

  it("retorna todo en 0 con una lista vacía", () => {
    expect(calcularKpiDescuentos([])).toEqual({
      porcentaje: 0,
      monto: 0,
      totalPorcentajeYMonto: 0,
      pactado: 0,
    });
  });
});

describe("contarPendienteUf", () => {
  it("cuenta solo las propuestas PENDIENTE_UF", () => {
    const propuestas = [
      propuesta({ id: 1, estado: "PENDIENTE" }),
      propuesta({ id: 2, estado: "PENDIENTE_UF" }),
      propuesta({ id: 3, estado: "PENDIENTE_UF" }),
      propuesta({ id: 4, estado: "FACTURADA" }),
      propuesta({ id: 5, estado: "ANULADA" }),
    ];
    expect(contarPendienteUf(propuestas)).toBe(2);
  });
});

describe("calcularSeriePorTipoServicio", () => {
  it("agrupa por tipo de servicio del proyecto, incluye los huecos reales entre meses", () => {
    const serie = calcularSeriePorTipoServicio(PROPUESTAS_CICLO_CALCULABLES, PROYECTOS_CICLO);

    expect(serie.meses.map((m) => `${m.anio}-${m.mes}`)).toEqual(["2026-5", "2026-6", "2026-7"]);

    const soporte = serie.categorias.find((c) => c.nombre === "Soporte y Mantención");
    const saas = serie.categorias.find((c) => c.nombre === "SaaS Crux ERP");
    expect(soporte?.valores).toEqual([435681, 440419 + 276128, 441124]);
    expect(saas?.valores).toEqual([0, 203966, 0]);
  });

  it('pone las propuestas sin proyecto (o de un proyecto sin tipo) en "Sin clasificar", visible, no omitida', () => {
    const sinProyecto = propuesta({
      id: 100,
      proyectoId: null,
      periodoMes: 1,
      estado: "PENDIENTE",
      netoClp: 850000,
    });

    const serie = calcularSeriePorTipoServicio([sinProyecto], []);

    expect(serie.categorias).toHaveLength(1);
    expect(serie.categorias[0]!.nombre).toBe(SIN_CLASIFICAR);
    expect(serie.categorias[0]!.valores).toEqual([850000]);
  });

  it("rellena con 0 un mes intermedio sin ninguna propuesta calculable (el hueco real, no se omite)", () => {
    const enero = propuesta({
      id: 1,
      periodoMes: 1,
      proyectoId: null,
      netoClp: 100000,
      estado: "PENDIENTE",
    });
    const marzo = propuesta({
      id: 2,
      periodoMes: 3,
      proyectoId: null,
      netoClp: 50000,
      estado: "PENDIENTE",
    });

    const serie = calcularSeriePorTipoServicio([enero, marzo], []);

    expect(serie.meses.map((m) => m.mes)).toEqual([1, 2, 3]);
    expect(serie.categorias[0]!.valores).toEqual([100000, 0, 50000]);
  });

  it("retorna vacío sin ninguna propuesta calculable", () => {
    const pendienteUf = propuesta({ id: 1, estado: "PENDIENTE_UF", netoClp: 0 });
    expect(calcularSeriePorTipoServicio([pendienteUf], [])).toEqual({ meses: [], categorias: [] });
  });
});

describe("calcularPorProyecto", () => {
  it('agrupa "sin proyecto" como una fila más, visible y sumada, no disimulada', () => {
    const conProyecto = propuesta({
      id: 1,
      proyectoId: 3,
      proyectoNombre: "Mantenimiento",
      netoClp: 200000,
      estado: "PENDIENTE",
    });
    const sinProyectoA = propuesta({
      id: 2,
      proyectoId: null,
      netoClp: 100000,
      estado: "PENDIENTE",
    });
    const sinProyectoB = propuesta({
      id: 3,
      proyectoId: null,
      netoClp: 50000,
      estado: "FACTURADA",
    });

    const filas = calcularPorProyecto([conProyecto, sinProyectoA, sinProyectoB]);

    expect(filas).toEqual(
      [
        { proyectoId: null, nombre: SIN_PROYECTO, netoClp: 150000, cantidad: 2 },
        { proyectoId: 3, nombre: "Mantenimiento", netoClp: 200000, cantidad: 1 },
      ].sort((a, b) => b.netoClp - a.netoClp),
    );
  });

  it("excluye PENDIENTE_UF y ANULADA del monto", () => {
    const calculable = propuesta({
      id: 1,
      proyectoId: 1,
      proyectoNombre: "Proyecto",
      netoClp: 100000,
      estado: "PENDIENTE",
    });
    const pendienteUf = propuesta({ id: 2, proyectoId: 1, netoClp: 0, estado: "PENDIENTE_UF" });
    const anulada = propuesta({ id: 3, proyectoId: 1, netoClp: 0, estado: "ANULADA" });

    const filas = calcularPorProyecto([calculable, pendienteUf, anulada]);
    expect(filas).toEqual([{ proyectoId: 1, nombre: "Proyecto", netoClp: 100000, cantidad: 1 }]);
  });
});

describe("calcularPorCliente", () => {
  it("suma el neto calculable pero cuenta TODOS los estados por cliente", () => {
    const filas = calcularPorCliente([
      propuesta({
        id: 1,
        clienteId: 1,
        clienteRazonSocial: "A",
        netoClp: 100000,
        estado: "PENDIENTE",
      }),
      propuesta({
        id: 2,
        clienteId: 1,
        clienteRazonSocial: "A",
        netoClp: 0,
        estado: "PENDIENTE_UF",
      }),
      propuesta({
        id: 3,
        clienteId: 2,
        clienteRazonSocial: "B",
        netoClp: 200000,
        estado: "FACTURADA",
      }),
    ]);

    const clienteA = filas.find((f) => f.clienteId === 1)!;
    expect(clienteA.netoClp).toBe(100000);
    expect(clienteA.cantidad).toBe(2);
    expect(clienteA.cantidadPorEstado).toEqual({
      PENDIENTE: 1,
      PENDIENTE_UF: 1,
      FACTURADA: 0,
      ANULADA: 0,
    });

    const clienteB = filas.find((f) => f.clienteId === 2)!;
    expect(clienteB.netoClp).toBe(200000);
  });
});

describe("calcularMoM", () => {
  it("compara el último mes con datos contra el mes calendario inmediatamente anterior", () => {
    const junio = propuesta({
      id: 1,
      periodoAnio: 2026,
      periodoMes: 6,
      netoClp: 100000,
      estado: "PENDIENTE",
    });
    const julio = propuesta({
      id: 2,
      periodoAnio: 2026,
      periodoMes: 7,
      netoClp: 150000,
      estado: "PENDIENTE",
    });

    const resultado = calcularMoM([junio, julio]);

    expect(resultado.disponible).toBe(true);
    if (resultado.disponible) {
      expect(resultado.mesActual).toBe(7);
      expect(resultado.mesAnterior).toBe(6);
      expect(resultado.variacionPorcentaje).toBeCloseTo(50, 5);
    }
  });

  it('no calcula nada ("disponible: false") si el mes inmediatamente anterior no tiene datos — el caso real: enero (CSV) a mayo (ciclo)', () => {
    const enero = propuesta({
      id: 1,
      periodoAnio: 2026,
      periodoMes: 1,
      netoClp: 5000000,
      estado: "PENDIENTE",
    });
    const mayo = propuesta({
      id: 2,
      periodoAnio: 2026,
      periodoMes: 5,
      netoClp: 435681,
      estado: "PENDIENTE",
    });

    const resultado = calcularMoM([enero, mayo]);

    expect(resultado.disponible).toBe(false);
  });

  it("cruza el año (enero contra diciembre del año anterior)", () => {
    const diciembre = propuesta({
      id: 1,
      periodoAnio: 2025,
      periodoMes: 12,
      netoClp: 100000,
      estado: "PENDIENTE",
    });
    const enero = propuesta({
      id: 2,
      periodoAnio: 2026,
      periodoMes: 1,
      netoClp: 120000,
      estado: "PENDIENTE",
    });

    const resultado = calcularMoM([diciembre, enero]);

    expect(resultado.disponible).toBe(true);
    if (resultado.disponible) {
      expect(resultado.anioAnterior).toBe(2025);
      expect(resultado.mesAnterior).toBe(12);
    }
  });

  it("no explota sin ninguna propuesta calculable", () => {
    expect(calcularMoM([])).toEqual({ disponible: false });
  });
});

describe("calcularYoY", () => {
  it('nace "sin período anterior" con datos reales (nada de 2025) — nunca un -100% inventado', () => {
    const resultado = calcularYoY(PROPUESTAS_CICLO_CALCULABLES);

    expect(resultado.disponible).toBe(false);
    if (!resultado.disponible) {
      expect(resultado.anioActual).toBe(2026);
    }
  });

  it("calcula la variación cuando SÍ hay el mismo mes del año anterior", () => {
    const julio2025 = propuesta({
      id: 1,
      periodoAnio: 2025,
      periodoMes: 7,
      netoClp: 300000,
      estado: "FACTURADA",
    });
    const julio2026 = propuesta({
      id: 2,
      periodoAnio: 2026,
      periodoMes: 7,
      netoClp: 450000,
      estado: "FACTURADA",
    });

    const resultado = calcularYoY([julio2025, julio2026]);

    expect(resultado.disponible).toBe(true);
    if (resultado.disponible) {
      expect(resultado.variacionPorcentaje).toBeCloseTo(50, 5);
    }
  });
});
