package cl.duoc.dsy1107.catalogoapi.config;

import cl.duoc.dsy1107.catalogoapi.model.Producto;
import cl.duoc.dsy1107.catalogoapi.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    // Productos iniciales para la demo; solo se cargan si el catálogo está vacío.
    @Bean
    CommandLineRunner cargarCatalogo(ProductoRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.saveAll(List.of(
                        new Producto("Notebook corporativo", new BigDecimal("899900"), 50),
                        new Producto("Licencia de software", new BigDecimal("45000"), 200),
                        new Producto("Silla ergonómica", new BigDecimal("159990"), 30)));
            }
        };
    }
}
