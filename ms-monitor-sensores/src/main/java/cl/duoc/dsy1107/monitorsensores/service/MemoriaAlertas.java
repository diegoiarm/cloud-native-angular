package cl.duoc.dsy1107.monitorsensores.service;

import cl.duoc.dsy1107.monitorsensores.dto.AlertaRecibida;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Memoria temporal de alertas, independiente del flujo de lecturas. */
@Component
public class MemoriaAlertas {

    private final int maximo;
    private final Deque<AlertaRecibida> alertas = new ArrayDeque<>();

    public MemoriaAlertas(@Value("${sensores.maximo-memoria:50}") int maximo) {
        this.maximo = maximo;
    }

    public synchronized void agregar(AlertaRecibida alerta) {
        alertas.addFirst(alerta);
        while (alertas.size() > maximo) {
            alertas.removeLast();
        }
    }

    public synchronized List<AlertaRecibida> listar() {
        return List.copyOf(alertas);
    }

    public synchronized int total() {
        return alertas.size();
    }
}
