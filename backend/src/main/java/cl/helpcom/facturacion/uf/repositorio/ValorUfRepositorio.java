package cl.helpcom.facturacion.uf.repositorio;

import cl.helpcom.facturacion.uf.dominio.ValorUf;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValorUfRepositorio extends JpaRepository<ValorUf, LocalDate> {
}
