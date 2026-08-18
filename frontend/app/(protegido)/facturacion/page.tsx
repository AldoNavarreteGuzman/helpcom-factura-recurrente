import { Suspense } from "react";
import { ListaPropuestas } from "@/components/facturacion/propuestas/ListaPropuestas";

export default function PaginaFacturacion() {
  return (
    <Suspense fallback={<div className="py-10 text-center text-sm text-sutil">Cargando…</div>}>
      <ListaPropuestas />
    </Suspense>
  );
}
