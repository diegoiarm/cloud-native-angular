package cl.duoc.dsy1107.pedidosapi.repository;

import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
