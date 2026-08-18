import { ListaAcuerdos } from "@/components/proyectos/acuerdos/ListaAcuerdos";

interface PropiedadesPaginaAcuerdos {
  params: Promise<{ id: string }>;
}

export default async function PaginaAcuerdosProyecto(props: PropiedadesPaginaAcuerdos) {
  const params = await props.params;
  return <ListaAcuerdos proyectoId={Number(params.id)} />;
}
