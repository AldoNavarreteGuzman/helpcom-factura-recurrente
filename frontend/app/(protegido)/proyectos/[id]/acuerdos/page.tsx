import { ListaAcuerdos } from "@/components/proyectos/acuerdos/ListaAcuerdos";

interface PropiedadesPaginaAcuerdos {
  params: { id: string };
}

export default function PaginaAcuerdosProyecto({ params }: PropiedadesPaginaAcuerdos) {
  return <ListaAcuerdos proyectoId={Number(params.id)} />;
}
