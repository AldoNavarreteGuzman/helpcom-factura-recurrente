package cl.helpcom.facturacion.clientes.repositorio;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByIdAndEmpresaId(Long id, Long empresaId);

    /**
     * Usado por la importación CSV para resolver el {@code rut_cliente} de cada fila
     * (modelo-de-datos.md §6) al cliente registrado, ya con el RUT normalizado al formato
     * canónico {@code NNNNNNNN-D} (ver {@code RutChileno}).
     */
    Optional<Cliente> findByEmpresaIdAndRut(Long empresaId, String rut);

    boolean existsByEmpresaIdAndRut(Long empresaId, String rut);

    boolean existsByEmpresaIdAndRutAndIdNot(Long empresaId, String rut, Long id);
}
