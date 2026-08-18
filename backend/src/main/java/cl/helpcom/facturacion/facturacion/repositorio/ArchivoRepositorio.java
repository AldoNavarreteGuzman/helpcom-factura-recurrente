package cl.helpcom.facturacion.facturacion.repositorio;

import cl.helpcom.facturacion.facturacion.dominio.Archivo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchivoRepositorio extends JpaRepository<Archivo, Long> {
}
