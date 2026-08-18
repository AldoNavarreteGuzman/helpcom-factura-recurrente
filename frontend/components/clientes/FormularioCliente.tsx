"use client";

import { useState, type FocusEvent, type FormEvent } from "react";
import { FormularioDialogo } from "@/components/formularios/FormularioDialogo";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Entrada } from "@/components/ui/Entrada";
import { Interruptor } from "@/components/ui/Interruptor";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerErrorDeCampo } from "@/lib/errores";
import { normalizarRut } from "@/lib/rut";
import { useFormularioApi } from "@/lib/useFormularioApi";
import type { Cliente, ClienteSolicitud } from "@/types/cliente";

export interface PropiedadesFormularioCliente {
  /** `null` = creando uno nuevo. */
  cliente: Cliente | null;
  onCerrar: () => void;
  onExito: () => void;
}

const EXPRESION_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function vacioANulo(valor: string): string | null {
  const recortado = valor.trim();
  return recortado === "" ? null : recortado;
}

export function FormularioCliente({ cliente, onCerrar, onExito }: PropiedadesFormularioCliente) {
  const { enviando, error, manejarEnvio } = useFormularioApi();

  const [rut, setRut] = useState(cliente?.rut ?? "");
  const [razonSocial, setRazonSocial] = useState(cliente?.razonSocial ?? "");
  const [nombreFantasia, setNombreFantasia] = useState(cliente?.nombreFantasia ?? "");
  const [giro, setGiro] = useState(cliente?.giro ?? "");
  const [email, setEmail] = useState(cliente?.email ?? "");
  const [telefono, setTelefono] = useState(cliente?.telefono ?? "");
  const [direccion, setDireccion] = useState(cliente?.direccion ?? "");
  const [activo, setActivo] = useState(cliente?.activo ?? true);

  const [erroresLocales, setErroresLocales] = useState<Record<string, string>>({});

  function errorDeCampo(campo: string): string | undefined {
    return erroresLocales[campo] ?? obtenerErrorDeCampo(error, campo);
  }

  function establecerErrorLocal(campo: string, mensaje: string | undefined) {
    setErroresLocales((actuales) => {
      if (!mensaje) {
        if (!(campo in actuales)) {
          return actuales;
        }
        const copia = { ...actuales };
        delete copia[campo];
        return copia;
      }
      return { ...actuales, [campo]: mensaje };
    });
  }

  function validarRutAlSalir(evento: FocusEvent<HTMLInputElement>) {
    const valor = evento.target.value;
    const valido = valor.trim() === "" || normalizarRut(valor) !== null;
    establecerErrorLocal("rut", valido ? undefined : "El RUT no es válido.");
  }

  function validarEmailAlSalir(evento: FocusEvent<HTMLInputElement>) {
    const valor = evento.target.value.trim();
    const valido = valor === "" || EXPRESION_EMAIL.test(valor);
    establecerErrorLocal("email", valido ? undefined : "El correo no tiene un formato válido.");
  }

  function validarAntesDeEnviar(): string | null {
    const errores: Record<string, string> = {};
    const rutNormalizado = normalizarRut(rut);
    if (!rutNormalizado) {
      errores.rut = "El RUT no es válido.";
    }
    if (razonSocial.trim() === "") {
      errores.razonSocial = "La razón social es obligatoria.";
    }
    const emailRecortado = email.trim();
    if (emailRecortado !== "" && !EXPRESION_EMAIL.test(emailRecortado)) {
      errores.email = "El correo no tiene un formato válido.";
    }
    setErroresLocales(errores);
    return Object.keys(errores).length === 0 ? rutNormalizado : null;
  }

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();

    const rutNormalizado = validarAntesDeEnviar();
    if (!rutNormalizado) {
      return;
    }

    const solicitud: ClienteSolicitud = {
      rut: rutNormalizado,
      razonSocial: razonSocial.trim(),
      nombreFantasia: vacioANulo(nombreFantasia),
      giro: vacioANulo(giro),
      email: vacioANulo(email),
      telefono: vacioANulo(telefono),
      direccion: vacioANulo(direccion),
      activo,
    };

    const exito = await manejarEnvio(async () => {
      if (cliente) {
        await clienteApiCliente.actualizar(`/clientes/${cliente.id}`, solicitud);
      } else {
        await clienteApiCliente.crear("/clientes", solicitud);
      }
    });

    if (exito) {
      onExito();
    }
  }

  return (
    <FormularioDialogo
      abierto
      titulo={cliente ? "Editar cliente" : "Nuevo cliente"}
      enviando={enviando}
      error={error}
      onCerrar={onCerrar}
      onEnviar={alEnviar}
    >
      <CampoFormulario
        id="rut"
        etiqueta="RUT"
        requerido
        error={errorDeCampo("rut")}
        descripcion="Formato 12345678-9, con o sin puntos."
      >
        <Entrada
          id="rut"
          value={rut}
          onChange={(evento) => setRut(evento.target.value)}
          onBlur={validarRutAlSalir}
          invalida={Boolean(errorDeCampo("rut"))}
          required
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario
        id="razonSocial"
        etiqueta="Razón social"
        requerido
        error={errorDeCampo("razonSocial")}
      >
        <Entrada
          id="razonSocial"
          value={razonSocial}
          onChange={(evento) => setRazonSocial(evento.target.value)}
          invalida={Boolean(errorDeCampo("razonSocial"))}
          maxLength={200}
          required
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario
        id="nombreFantasia"
        etiqueta="Nombre de fantasía"
        error={errorDeCampo("nombreFantasia")}
      >
        <Entrada
          id="nombreFantasia"
          value={nombreFantasia}
          onChange={(evento) => setNombreFantasia(evento.target.value)}
          maxLength={200}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="giro" etiqueta="Giro" error={errorDeCampo("giro")}>
        <Entrada
          id="giro"
          value={giro}
          onChange={(evento) => setGiro(evento.target.value)}
          maxLength={150}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="email" etiqueta="Email" error={errorDeCampo("email")}>
        <Entrada
          id="email"
          type="email"
          value={email}
          onChange={(evento) => setEmail(evento.target.value)}
          onBlur={validarEmailAlSalir}
          invalida={Boolean(errorDeCampo("email"))}
          maxLength={150}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="telefono" etiqueta="Teléfono" error={errorDeCampo("telefono")}>
        <Entrada
          id="telefono"
          value={telefono}
          onChange={(evento) => setTelefono(evento.target.value)}
          maxLength={30}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="direccion" etiqueta="Dirección" error={errorDeCampo("direccion")}>
        <Entrada
          id="direccion"
          value={direccion}
          onChange={(evento) => setDireccion(evento.target.value)}
          maxLength={250}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="activo" etiqueta="Estado">
        <Interruptor
          id="activo"
          etiqueta="Activo"
          checked={activo}
          onChange={(evento) => setActivo(evento.target.checked)}
        />
      </CampoFormulario>
    </FormularioDialogo>
  );
}
