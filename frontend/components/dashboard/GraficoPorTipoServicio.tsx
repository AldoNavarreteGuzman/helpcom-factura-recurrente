"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { TarjetaDashboard } from "./TarjetaDashboard";
import { colorParaCategoria, COLOR_LINEA, COLOR_SUTIL } from "@/lib/coloresGrafica";
import { calcularSeriePorTipoServicio, SIN_CLASIFICAR } from "@/lib/dashboardCalculos";
import { formatearClp, formatearClpCompacto } from "@/lib/formato";
import type { Proyecto } from "@/types/proyecto";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesGraficoPorTipoServicio {
  propuestas: PropuestaFacturacion[] | null;
  proyectos: Proyecto[] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * Tendencia mensual de neto calculable por tipo de servicio (docs/frontend.md R9): el rango de
 * meses va del primero al último con datos SIN saltarse huecos intermedios (un mes sin
 * propuestas calculables queda en 0, visible en el eje) — así la brecha real entre enero (toda
 * la importación CSV histórica) y el resto del año se lee tal cual es, no se disimula. "Sin
 * clasificar" (propuestas sin proyecto, o de un proyecto sin tipo de servicio asignado) es una
 * categoría más del gráfico, en un tono neutro deliberado — nunca se omite ni se esconde.
 */
export function GraficoPorTipoServicio({
  propuestas,
  proyectos,
  cargando,
  error,
}: PropiedadesGraficoPorTipoServicio) {
  const serie =
    propuestas && proyectos ? calcularSeriePorTipoServicio(propuestas, proyectos) : null;
  const sinDatos = serie != null && serie.meses.length === 0;

  const datos = serie
    ? serie.meses.map((mes, indice) => {
        const fila: Record<string, string | number> = { mes: mes.etiqueta };
        for (const categoria of serie.categorias) {
          fila[categoria.nombre] = categoria.valores[indice]!;
        }
        return fila;
      })
    : [];

  return (
    <TarjetaDashboard
      titulo="Por tipo de servicio, por mes"
      descripcion="Neto calculable (Pendiente + Facturada) agrupado por tipo de servicio del proyecto."
      cargando={cargando}
      error={error}
      alturaContenido="min-h-[300px]"
    >
      {sinDatos ? (
        <div className="flex flex-1 items-center justify-center text-sm text-sutil">
          Todavía no hay propuestas calculables para graficar.
        </div>
      ) : serie ? (
        <ResponsiveContainer width="100%" height={280}>
          <BarChart data={datos} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke={COLOR_LINEA} vertical={false} />
            <XAxis
              dataKey="mes"
              tick={{ fontSize: 12, fill: COLOR_SUTIL }}
              axisLine={{ stroke: COLOR_LINEA }}
            />
            <YAxis
              tickFormatter={(valor: number) => formatearClpCompacto(valor)}
              tick={{ fontSize: 12, fill: COLOR_SUTIL }}
              axisLine={{ stroke: COLOR_LINEA }}
              width={64}
            />
            <Tooltip
              formatter={(valor) => (typeof valor === "number" ? formatearClp(valor) : valor)}
              contentStyle={{ borderRadius: 8, borderColor: COLOR_LINEA, fontSize: 12 }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            {serie.categorias.map((categoria, indice) => (
              <Bar
                key={categoria.nombre}
                dataKey={categoria.nombre}
                stackId="tipo-servicio"
                fill={colorParaCategoria(categoria.nombre, indice, SIN_CLASIFICAR)}
                radius={indice === serie.categorias.length - 1 ? [4, 4, 0, 0] : undefined}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      ) : null}
    </TarjetaDashboard>
  );
}
