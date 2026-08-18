package cl.helpcom.facturacion.facturacion.almacenamiento;

/**
 * Lo que {@link AlmacenArchivos#guardar} retorna: la clave con la que el objeto quedó
 * guardado, junto a los metadatos que la fila {@code archivo} necesita persistir.
 */
public record ReferenciaArchivo(String claveObjeto, String nombreOriginal, String tipoContenido, long tamanoBytes) {
}
