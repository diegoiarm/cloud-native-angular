package cl.duoc.dsy1107.monitorsensores.service;

import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Memoria temporal de lecturas (documento 01 S13: máximo 50 SOLO en memoria).
 *
 * No hay persistencia: al reiniciar el monitor la lista se pierde. El offset
 * confirmado en el consumer group es lo que permite retomar desde Kafka.
 */
@Component
public class MemoriaLecturas {

    private final int maximo;
    private final Deque<LecturaRecibida> lecturas = new ArrayDeque<>();

    public MemoriaLecturas(@Value("${sensores.maximo-memoria:50}") int maximo) {
        this.maximo = maximo;
    }

    public synchronized void agregar(LecturaRecibida lectura) {
        lecturas.addFirst(lectura);
        while (lecturas.size() > maximo) {
            lecturas.removeLast();
        }
    }

    public synchronized List<LecturaRecibida> listar() {
        return List.copyOf(lecturas);
    }

    public synchronized int total() {
        return lecturas.size();
    }
}
