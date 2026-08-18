import { DetalleFactura } from "@/components/facturacion/facturas/DetalleFactura";

interface PropiedadesPaginaDetalleFactura {
  params: { id: string };
}

export default function PaginaDetalleFactura({ params }: PropiedadesPaginaDetalleFactura) {
  return <DetalleFactura facturaId={Number(params.id)} />;
}
