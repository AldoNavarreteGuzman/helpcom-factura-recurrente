import type { ReactNode } from "react";
import { LayoutDetalleProyecto } from "@/components/proyectos/LayoutDetalleProyecto";

interface PropiedadesLayoutProyecto {
  params: { id: string };
  children: ReactNode;
}

export default function LayoutProyecto({ params, children }: PropiedadesLayoutProyecto) {
  return <LayoutDetalleProyecto proyectoId={Number(params.id)}>{children}</LayoutDetalleProyecto>;
}
