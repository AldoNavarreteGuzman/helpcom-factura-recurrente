"use client";

import { CalloutPendienteUf } from "./CalloutPendienteUf";
import { GraficoPorProyecto } from "./GraficoPorProyecto";
import { GraficoPorTipoServicio } from "./GraficoPorTipoServicio";
import { TarjetaComparacionPeriodos } from "./TarjetaComparacionPeriodos";
import { TarjetaKpiDescuentos } from "./TarjetaKpiDescuentos";
import { TarjetaPorCliente } from "./TarjetaPorCliente";
import { useDashboardDatos } from "@/lib/useDashboardDatos";

/**
 * Dashboard (docs/plan-rediseno.md R9, docs/frontend.md R9): todas las tarjetas se calculan
 * client-side (`lib/dashboardCalculos.ts`) a partir de las propuestas y proyectos completos —
 * no hay endpoint de agregación para KPI de descuentos / por tipo de servicio / por proyecto
 * hoy (verificado antes de construir, ver R9); "por cliente" sí lo expone
 * `GET /informes/facturacion`, pero se deriva de la misma fuente que el resto para no duplicar
 * llamadas ni mantener dos caminos de datos distintos en una sola pantalla.
 */
export function Dashboard() {
  const { propuestas, proyectos, cargando, error } = useDashboardDatos();

  return (
    <div className="space-y-4">
      <CalloutPendienteUf propuestas={propuestas} cargando={cargando} error={error} />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <TarjetaKpiDescuentos propuestas={propuestas} cargando={cargando} error={error} />
        <TarjetaComparacionPeriodos propuestas={propuestas} cargando={cargando} error={error} />
      </div>

      <GraficoPorTipoServicio
        propuestas={propuestas}
        proyectos={proyectos}
        cargando={cargando}
        error={error}
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <GraficoPorProyecto propuestas={propuestas} cargando={cargando} error={error} />
        <TarjetaPorCliente propuestas={propuestas} cargando={cargando} error={error} />
      </div>
    </div>
  );
}
