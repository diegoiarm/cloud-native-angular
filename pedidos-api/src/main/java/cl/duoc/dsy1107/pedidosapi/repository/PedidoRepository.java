package cl.duoc.dsy1107.pedidosapi.repository;

import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "items")
    List<Pedido> findAllByOrderByCreadoEnDesc();

    @EntityGraph(attributePaths = "items")
    List<Pedido> findByEstadoOrderByCreadoEnDesc(EstadoPedido estado);

    @EntityGraph(attributePaths = "items")
    List<Pedido> findByClienteIdOrderByCreadoEnDesc(String clienteId);

    @EntityGraph(attributePaths = "items")
    List<Pedido> findByClienteIdAndEstadoOrderByCreadoEnDesc(String clienteId, EstadoPedido estado);
}
