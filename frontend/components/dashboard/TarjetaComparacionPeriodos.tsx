import { TarjetaDashboard } from "./TarjetaDashboard";
import { calcularMoM, calcularYoY } from "@/lib/dashboardCalculos";
import { NOMBRES_MES } from "@/lib/etiquetas";
import { combinarClases } from "@/lib/estilos";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesTarjetaComparacionPeriodos {
  propuestas: PropuestaFacturacion[] | null;
  cargando: boolean;
  error: unknown;
}

function nombreMes(mes: number): string {
  return NOMBRES_MES[mes - 1]!;
}

function formatearVariacion(porcentaje: number): { texto: string; clase: string } {
  const positivo = porcentaje >= 0;
  return {
    texto: `${positivo ? "+" : ""}${porcentaje.toFixed(1)}%`,
    clase: positivo ? "text-estado-facturada" : "text-estado-error",
  };
}

/**
 * MoM y YoY (docs/frontend.md R9), agrupadas en una sola tarjeta porque comparten la misma
 * fuente (totales mensuales de neto calculable) y el mismo propósito: comparar contra un
 * período anterior REAL, nunca inventado.
 *
 * MoM solo compara el último mes con datos contra el mes calendario INMEDIATAMENTE anterior, y
 * solo si ese mes también tiene datos — con datos reales (enero concentra 58 propuestas CSV y
 * no hay nada de nuevo hasta mayo), comparar contra "el período anterior en la lista" mostraría
 * un falso -100% que se leería como un bug. `lib/dashboardCalculos.ts::calcularMoM` documenta
 * la regla completa.
 *
 * YoY nace "sin período anterior" a propósito: no hay datos de 2025 (primer año del sistema) —
 * es el comportamiento correcto en producción durante el primer año, no un caso sin manejar, y
 * nunca se muestra como una caída de -100%.
 */
export function TarjetaComparacionPeriodos({
  propuestas,
  cargando,
  error,
}: PropiedadesTarjetaComparacionPeriodos) {
  const mom = propuestas ? calcularMoM(propuestas) : null;
  const yoy = propuestas ? calcularYoY(propuestas) : null;

  return (
    <TarjetaDashboard
      titulo="Comparación de períodos"
      descripcion="Sobre el neto calculable (Pendiente + Facturada), solo entre meses/años con datos reales de ambos lados."
      cargando={cargando}
      error={error}
      alturaContenido="min-h-[160px]"
    >
      {mom && yoy ? (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-md border border-linea p-3">
            <div className="text-xs font-medium text-sutil">Mes contra mes</div>
            {mom.disponible ? (
              <>
                <div
                  className={combinarClases(
                    "text-2xl font-semibold tabular-nums",
                    formatearVariacion(mom.variacionPorcentaje).clase,
                  )}
                >
                  {formatearVariacion(mom.variacionPorcentaje).texto}
                </div>
                <p className="text-xs text-sutil">
                  {nombreMes(mom.mesActual)} {mom.anioActual} vs {nombreMes(mom.mesAnterior)}{" "}
                  {mom.anioAnterior}
                </p>
              </>
            ) : (
              <p className="mt-1 text-sm text-sutil">
                Sin dos meses consecutivos con datos todavía para comparar.
              </p>
            )}
          </div>
          <div className="rounded-md border border-linea p-3">
            <div className="text-xs font-medium text-sutil">Año contra año</div>
            {yoy.disponible ? (
              <>
                <div
                  className={combinarClases(
                    "text-2xl font-semibold tabular-nums",
                    formatearVariacion(yoy.variacionPorcentaje).clase,
                  )}
                >
                  {formatearVariacion(yoy.variacionPorcentaje).texto}
                </div>
                <p className="text-xs text-sutil">
                  {nombreMes(yoy.mes)} {yoy.anioActual} vs {nombreMes(yoy.mes)} {yoy.anioActual - 1}
                </p>
              </>
            ) : (
              <p className="mt-1 text-sm text-sutil">
                Sin datos de {(yoy.anioActual || new Date().getFullYear()) - 1} — primer año del
                sistema.
              </p>
            )}
          </div>
        </div>
      ) : null}
    </TarjetaDashboard>
  );
}
