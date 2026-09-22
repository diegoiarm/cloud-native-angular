package cl.duoc.dsy1107.catalogoapi.repository;

import cl.duoc.dsy1107.catalogoapi.model.Producto;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findAllByOrderByNombreAsc();

    // Bloquea las filas mientras se descuenta stock, para que dos pedidos
    // aceptados al mismo tiempo no vendan el mismo stock dos veces.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Producto> findByIdIn(Collection<Long> ids);
}
