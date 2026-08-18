import "@testing-library/jest-dom/vitest";

/**
 * jsdom implementa el elemento `<dialog>` pero no sus métodos imperativos
 * (`showModal`/`close`) — ver https://github.com/jsdom/jsdom/issues/3294. `components/ui/
 * Dialogo.tsx` (y todo lo que se construye sobre él: `FormularioDialogo`,
 * `DialogoConfirmacion`) los usa, así que se polyfillean acá para que los tests puedan
 * renderizarlos. Basta con reflejar el atributo `open`, que es lo único que lee el resto del
 * comportamiento del elemento en jsdom.
 */
if (typeof HTMLDialogElement !== "undefined" && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    this.removeAttribute("open");
  };
}
