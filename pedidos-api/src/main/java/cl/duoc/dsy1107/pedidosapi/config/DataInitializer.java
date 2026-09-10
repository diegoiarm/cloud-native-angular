package cl.duoc.dsy1107.pedidosapi.config;

import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import cl.duoc.dsy1107.pedidosapi.repository.PedidoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner cargarDatos(PedidoRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.save(new Pedido("Notebook", "CREADO", new BigDecimal("699990")));
                repository.save(new Pedido("Mouse", "CREADO", new BigDecimal("19990")));
                repository.save(new Pedido("Teclado", "CREADO", new BigDecimal("29990")));
            }
        };
    }
}
