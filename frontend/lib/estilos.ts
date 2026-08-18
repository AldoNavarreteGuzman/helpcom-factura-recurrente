/** Combina clases de Tailwind condicionalmente, descartando valores falsy. */
export function combinarClases(...clases: Array<string | false | null | undefined>): string {
  return clases.filter(Boolean).join(" ");
}
