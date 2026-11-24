package cl.duoc.dsy1107.monitorsensores.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Envía en tiempo real (Server-Sent Events) cada lectura y alerta que procesa
 * el monitor. El navegador las recibe apenas llegan, sin consultar en bucle.
 */
@Component
public class EmisorEventos {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> suscriptores = new CopyOnWriteArrayList<>();

    public SseEmitter suscribir() {
        SseEmitter emisor = new SseEmitter(TIMEOUT_MS);
        suscriptores.add(emisor);
        emisor.onCompletion(() -> suscriptores.remove(emisor));
        emisor.onTimeout(() -> {
            suscriptores.remove(emisor);
            emisor.complete();
        });
        emisor.onError(error -> suscriptores.remove(emisor));
        try {
            // Sin un primer envío Spring no manda la respuesta y el navegador
            // sigue en "conectando" hasta que llegue la primera lectura.
            emisor.send(SseEmitter.event().name("conectado").data("ok"));
        } catch (IOException e) {
            suscriptores.remove(emisor);
        }
        return emisor;
    }

    /** tipo = "lectura" o "alerta"; es el nombre del evento SSE. */
    public void emitir(String tipo, Object dato) {
        for (SseEmitter emisor : suscriptores) {
            try {
                emisor.send(SseEmitter.event().name(tipo).data(dato));
            } catch (IOException | IllegalStateException e) {
                // Navegador cerrado: se descarta sin afectar el consumo de Kafka.
                suscriptores.remove(emisor);
            }
        }
    }

    public int suscriptores() {
        return suscriptores.size();
    }
}
