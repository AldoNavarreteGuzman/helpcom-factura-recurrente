package cl.helpcom.facturacion.seguridad.repositorio;

import cl.helpcom.facturacion.seguridad.dominio.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
}
