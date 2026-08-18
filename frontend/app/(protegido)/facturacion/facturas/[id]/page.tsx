import { DetalleFactura } from "@/components/facturacion/facturas/DetalleFactura";

interface PropiedadesPaginaDetalleFactura {
  params: Promise<{ id: string }>;
}

export default async function PaginaDetalleFactura(props: PropiedadesPaginaDetalleFactura) {
  const params = await props.params;
  return <DetalleFactura facturaId={Number(params.id)} />;
}
