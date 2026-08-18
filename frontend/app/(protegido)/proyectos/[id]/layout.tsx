import type { ReactNode } from "react";
import { LayoutDetalleProyecto } from "@/components/proyectos/LayoutDetalleProyecto";

interface PropiedadesLayoutProyecto {
  params: Promise<{ id: string }>;
  children: ReactNode;
}

export default async function LayoutProyecto({ params, children }: PropiedadesLayoutProyecto) {
  const { id } = await params;
  return <LayoutDetalleProyecto proyectoId={Number(id)}>{children}</LayoutDetalleProyecto>;
}
