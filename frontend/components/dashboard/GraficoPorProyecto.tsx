"use client";

import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { TarjetaDashboard } from "./TarjetaDashboard";
import { colorParaCategoria, COLOR_LINEA, COLOR_SUTIL } from "@/lib/coloresGrafica";
import { calcularPorProyecto, SIN_PROYECTO } from "@/lib/dashboardCalculos";
import { formatearClp, formatearClpCompacto } from "@/lib/formato";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesGraficoPorProyecto {
  propuestas: PropuestaFacturacion[] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * Neto calculable por proyecto (docs/frontend.md R9), barras horizontales. "Sin proyecto" (toda
 * la importación CSV histórica, sin `codigo_proyecto`) es una barra más — en el mismo tono
 * neutro que "Sin clasificar" del gráfico por tipo de servicio — nunca se disimula ni se deja
 * fuera del gráfico aunque sea, con datos reales, la barra más grande.
 */
export function GraficoPorProyecto({ propuestas, cargando, error }: PropiedadesGraficoPorProyecto) {
  const filas = propuestas ? calcularPorProyecto(propuestas) : null;
  const sinDatos = filas != null && filas.length === 0;
  const alturaGrafico = filas ? Math.max(120, filas.length * 40) : 120;

  return (
    <TarjetaDashboard
      titulo="Por proyecto"
      descripcion="Neto calculable (Pendiente + Facturada) por proyecto."
      cargando={cargando}
      error={error}
      alturaContenido="min-h-[220px]"
    >
      {sinDatos ? (
        <div className="flex flex-1 items-center justify-center text-sm text-sutil">
          Todavía no hay propuestas calculables para graficar.
        </div>
      ) : filas ? (
        <ResponsiveContainer width="100%" height={alturaGrafico}>
          <BarChart
            data={filas}
            layout="vertical"
            margin={{ top: 4, right: 24, left: 8, bottom: 0 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke={COLOR_LINEA} horizontal={false} />
            <XAxis
              type="number"
              tickFormatter={(valor: number) => formatearClpCompacto(valor)}
              tick={{ fontSize: 12, fill: COLOR_SUTIL }}
              axisLine={{ stroke: COLOR_LINEA }}
            />
            <YAxis
              type="category"
              dataKey="nombre"
              tick={{ fontSize: 12, fill: COLOR_SUTIL }}
              width={140}
              axisLine={{ stroke: COLOR_LINEA }}
            />
            <Tooltip
              formatter={(valor) => (typeof valor === "number" ? formatearClp(valor) : valor)}
              contentStyle={{ borderRadius: 8, borderColor: COLOR_LINEA, fontSize: 12 }}
            />
            <Bar dataKey="netoClp" radius={[0, 4, 4, 0]}>
              {filas.map((fila) => (
                <Cell
                  key={fila.proyectoId ?? "sin-proyecto"}
                  fill={colorParaCategoria(fila.nombre, 0, SIN_PROYECTO)}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      ) : null}
    </TarjetaDashboard>
  );
}
