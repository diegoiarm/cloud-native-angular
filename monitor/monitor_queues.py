#!/usr/bin/env python3
"""Monitor de colas de Pedidos360 para la Management API de RabbitMQ.

Documento 04 (Laboratorio 1), Actividad D, y documento 03 (alertas didacticas).
Solo usa la biblioteca estandar de Python 3.

  python monitor/monitor_queues.py --una-vez
  python monitor/monitor_queues.py --intervalo 15 --umbral-ready 10

Campos que entrega como evidencia: messages_ready, messages_unacknowledged y
consumers de cada cola, mas las alertas:

  - Una cola *.dlq con Ready > 0          -> hay mensajes fallidos que revisar.
  - Ready >= umbral-ready                 -> se acumulan pendientes.
  - Consumers == 0 con Ready > 0          -> consumidor detenido o desconectado.
  - Unacked >= umbral-unacked             -> revisar carga, ACK y tiempos.

Los umbrales son de demostracion y deben ajustarse a la operacion real.
"""

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

URL_DEFECTO = os.environ.get("RABBITMQ_MANAGEMENT_URL", "http://localhost:15672")
USUARIO_DEFECTO = os.environ.get("RABBITMQ_USER", "labuser")
PASSWORD_DEFECTO = os.environ.get("RABBITMQ_PASSWORD", "labpass")


def obtener(url, usuario, password):
    credenciales = base64.b64encode(f"{usuario}:{password}".encode()).decode()
    peticion = urllib.request.Request(
        url,
        headers={"Authorization": f"Basic {credenciales}",
                 "Accept": "application/json"},
    )
    with urllib.request.urlopen(peticion, timeout=10) as respuesta:
        return json.load(respuesta)


def evaluar(colas, umbral_ready, umbral_unacked):
    """Devuelve la lista de alertas explicativas para la consola."""
    alertas = []
    for cola in colas:
        nombre = cola.get("name", "")
        ready = cola.get("messages_ready", 0)
        unacked = cola.get("messages_unacknowledged", 0)
        consumidores = cola.get("consumers", 0)

        if nombre.endswith(".dlq"):
            if ready > 0:
                alertas.append(
                    f"DLQ {nombre}: Ready={ready} "
                    "- hay mensajes fallidos que requieren revision.")
            continue

        if nombre.endswith(".retry"):
            # Cola de espera de 5 s: su contenido es transitorio, no es alerta.
            continue

        if ready >= umbral_ready:
            alertas.append(
                f"{nombre}: Ready={ready} >= umbral {umbral_ready} "
                "- se estan acumulando pendientes; investigar ritmo productor/consumidor.")
        if consumidores == 0 and ready > 0:
            alertas.append(
                f"{nombre}: Consumers=0 con Ready={ready} "
                "- servicio consumidor detenido o desconectado.")
        if unacked >= umbral_unacked:
            alertas.append(
                f"{nombre}: Unacked={unacked} >= umbral {umbral_unacked} "
                "- revisar carga, ACK manual y tiempos de proceso.")
    return alertas


def imprimir(colas, alertas):
    print(f"{'cola':45} {'ready':>7} {'unack':>7} {'consumers':>10}")
    for cola in sorted(colas, key=lambda c: c.get("name", "")):
        print(f"{cola.get('name', ''):45} "
              f"{cola.get('messages_ready', 0):>7} "
              f"{cola.get('messages_unacknowledged', 0):>7} "
              f"{cola.get('consumers', 0):>10}")
    if alertas:
        for alerta in alertas:
            print(f"ALERTA: {alerta}")
    else:
        print("Sin alertas en esta lectura.")


def leer(url, usuario, password, umbral_ready, umbral_unacked):
    try:
        colas = obtener(f"{url}/api/queues", usuario, password)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            print(f"ERROR: credenciales rechazadas por {url} "
                  f"(revise --usuario/--password, por defecto labuser/labpass).")
            sys.exit(2)
        print(f"ERROR: la Management API respondio {e.code}.")
        sys.exit(2)
    except (urllib.error.URLError, OSError) as e:
        print(f"ERROR: no se pudo consultar {url}: {e}")
        sys.exit(2)

    alertas = evaluar(colas, umbral_ready, umbral_unacked)
    imprimir(colas, alertas)
    return alertas


def main():
    parser = argparse.ArgumentParser(
        description="Monitor de colas de Pedidos360 (Management API de RabbitMQ).")
    parser.add_argument("--una-vez", action="store_true",
                        help="una sola lectura; sale con codigo 1 si hay alertas")
    parser.add_argument("--intervalo", type=int, default=15,
                        help="segundos entre lecturas en modo continuo (por defecto 15)")
    parser.add_argument("--umbral-ready", type=int, default=10,
                        help="Ready acumulado que dispara alerta (por defecto 10)")
    parser.add_argument("--umbral-unacked", type=int, default=10,
                        help="Unacked sostenido que dispara alerta (por defecto 10)")
    parser.add_argument("--url", default=URL_DEFECTO,
                        help=f"URL de la Management API (por defecto {URL_DEFECTO})")
    parser.add_argument("--usuario", default=USUARIO_DEFECTO,
                        help=f"usuario de management (por defecto {USUARIO_DEFECTO})")
    parser.add_argument("--password", default=PASSWORD_DEFECTO,
                        help="password de management (por defecto el de laboratorio)")
    args = parser.parse_args()

    if args.una_vez:
        alertas = leer(args.url, args.usuario, args.password,
                       args.umbral_ready, args.umbral_unacked)
        sys.exit(1 if alertas else 0)

    print(f"Monitor cada {args.intervalo} s contra {args.url}. Ctrl+C para detener.")
    try:
        while True:
            leer(args.url, args.usuario, args.password,
                 args.umbral_ready, args.umbral_unacked)
            print("-" * 72)
            time.sleep(args.intervalo)
    except KeyboardInterrupt:
        print("\nMonitor detenido.")


if __name__ == "__main__":
    main()
