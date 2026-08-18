package cl.helpcom.facturacion.empresa.repositorio;

import cl.helpcom.facturacion.empresa.dominio.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepositorio extends JpaRepository<Empresa, Long> {
}
