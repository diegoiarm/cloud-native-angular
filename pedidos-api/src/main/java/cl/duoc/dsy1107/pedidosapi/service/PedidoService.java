package cl.duoc.dsy1107.pedidosapi.service;

import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import cl.duoc.dsy1107.pedidosapi.repository.PedidoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository repository;

    public PedidoService(PedidoRepository repository) {
        this.repository = repository;
    }

    public List<Pedido> listar() {
        return repository.findAll();
    }

    public Pedido guardar(Pedido pedido) {
        return repository.save(pedido);
    }
}
