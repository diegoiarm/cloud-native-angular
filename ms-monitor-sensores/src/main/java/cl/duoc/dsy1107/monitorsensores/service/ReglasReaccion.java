package cl.duoc.dsy1107.monitorsensores.service;

import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reglas que el monitor aplica a cada lectura consumida (procesamiento reactivo, EP6).
 *
 *   UMBRAL       temperatura >= umbral            -> severidad ALTA
 *   ALZA_BRUSCA  subida >= alza respecto de la    -> severidad MEDIA
 *                lectura anterior del mismo sensor
 *
 * ALZA_BRUSCA necesita estado por sensor. Funciona porque la clave del evento es
 * sensorId: todas las lecturas de un sensor caen en la misma partición y llegan
 * en orden al consumer.
 *
 * evaluar() no modifica el estado; registrar() se llama solo cuando la lectura
 * se procesó completa, para que un reintento evalúe con el mismo estado.
 */
@Component
public class ReglasReaccion {

    private final double umbralC;
    private final double alzaBruscaC;
    private final Map<String, Double> ultimaTemperatura = new ConcurrentHashMap<>();

    public ReglasReaccion(
            @Value("${sensores.umbral-alerta-c:30.0}") double umbralC,
            @Value("${sensores.alza-brusca-c:5.0}") double alzaBruscaC) {
        this.umbralC = umbralC;
        this.alzaBruscaC = alzaBruscaC;
    }

    public Optional<AlertaSensor> evaluar(LecturaSensor lectura) {
        double temperatura = lectura.temperatura();
        Double anterior = ultimaTemperatura.get(lectura.sensorId());

        List<String> motivos = new ArrayList<>();
        boolean superaUmbral = temperatura >= umbralC;
        if (superaUmbral) {
            motivos.add("UMBRAL: " + temperatura + " " + lectura.unidad()
                    + " supera " + umbralC + " " + lectura.unidad());
        }
        if (anterior != null && temperatura - anterior >= alzaBruscaC) {
            motivos.add("ALZA_BRUSCA: subió " + redondear(temperatura - anterior) + " "
                    + lectura.unidad() + " desde " + anterior + " " + lectura.unidad());
        }
        if (motivos.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new AlertaSensor(
                lectura.sensorId(),
                temperatura,
                lectura.unidad(),
                lectura.secuencia(),
                String.join("; ", motivos),
                superaUmbral ? "ALTA" : "MEDIA",
                Instant.now()));
    }

    public void registrar(LecturaSensor lectura) {
        ultimaTemperatura.put(lectura.sensorId(), lectura.temperatura());
    }

    public double umbralC() {
        return umbralC;
    }

    public double alzaBruscaC() {
        return alzaBruscaC;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
