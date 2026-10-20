# Despliegue paso a paso en AWS Academy Learner Lab

Sigue el orden de la guía 09 del curso: RDS → EC2 → API Gateway → Angular.
Región **us-east-1 (Norte de Virginia)** en todos los pasos; si la consola muestra otra
región, cámbiala en el selector de arriba a la derecha.

Ficha para ir anotando:

| Dato | Valor |
|---|---|
| Endpoint RDS | |
| IP pública EC2 | |
| Invoke URL del Gateway | |

La clave del RDS no se anota aquí ni se sube al repo: va solo en el user data de la EC2.

---

## Paso 0. Iniciar el laboratorio

1. Entra a `https://awsacademy.instructure.com` y abre el curso **AWS Academy Learner Lab**.
2. Módulos → **Iniciar el Laboratorio de aprendizaje de AWS Academy**.
3. Presiona **Start Lab** arriba a la derecha y espera a que el punto junto a "AWS" quede **verde**.
4. Haz clic en ese punto verde, o en **AWS**, para abrir la consola.
5. Descarga la llave SSH del lab: en la misma pantalla, **AWS Details** → **SSH key** →
   **Download PEM**. Guarda `labsuser.pem` en una carpeta fija, por ejemplo
   `C:\Users\diego\aws\labsuser.pem`. Esa llave corresponde al key pair `vockey`, que es el
   que seleccionarás al crear la instancia.

---

## Paso 1. Verificar el RDS y anotar el endpoint

La base ya la creaste. Solo hay que confirmar los datos y copiar el endpoint.

1. En el buscador de la consola escribe **RDS** y ábrelo.
2. Menú izquierdo → **Bases de datos** (*Databases*).
3. Haz clic en **dsy1107-pedidos-db**. El estado debe decir **Disponible** (*Available*).
4. Pestaña **Conectividad y seguridad** (*Connectivity & security*):
   - Copia el **Punto de enlace** (*Endpoint*). Se ve como
     `dsy1107-pedidos-db.xxxxxxxx.us-east-1.rds.amazonaws.com`. Anótalo, es tu `DB_HOST`.
   - Anota también el **Puerto**: 5432.
   - Bajo **VPC security groups** aparece el grupo asociado, por ejemplo `default` o
     `dsy1107-rds-sg`. Haz clic en él, se abre en EC2 → Security Groups. **Déjalo abierto
     en otra pestaña**, lo vas a editar en el paso 3.
5. Pestaña **Configuración** (*Configuration*): confirma que el **Nombre de la base de
   datos inicial** sea `pedidosdb`. Si está vacío, no importa: el user data de la EC2 crea
   las dos bases igual.

---

## Paso 2. Crear la instancia EC2

1. Buscador → **EC2** → menú izquierdo **Instancias** → botón naranjo
   **Lanzar instancias** (*Launch instances*).
2. **Nombre**: `dsy1107-pedidos-api`.
3. **Imágenes de aplicaciones y SO**: deja seleccionada **Amazon Linux**, y en la lista
   desplegable **Amazon Linux 2023 AMI** (64 bits, x86).
4. **Tipo de instancia**: abre el desplegable y elige **t3.small** (2 GB), o **t3.medium**
   (4 GB) si el laboratorio lo permite. No uses micro: son tres servicios Java y dos nodos
   RabbitMQ en la misma máquina. En t3.small funciona con cada Java limitado a 256 MB y
   una swap de 1 GB (lo deja configurado el paso 4.0).
5. **Par de claves**: en el desplegable elige **vockey**. Es la llave que descargaste en el
   paso 0.
6. **Configuración de red** → botón **Editar**:
   - **VPC**: la misma donde está el RDS, normalmente la marcada como predeterminada.
   - **Asignar IP pública automáticamente**: **Habilitar**.
   - **Firewall (grupos de seguridad)**: marca **Crear grupo de seguridad**.
   - **Nombre**: `dsy1107-ec2-sg`. Descripción: la misma.
   - Deja la regla SSH que viene y agrega las demás con **Agregar regla de grupo de
     seguridad**, hasta tener estas siete:

   | Tipo | Puerto | Origen | Para qué |
   |---|---|---|---|
   | SSH | 22 | Mi IP | conectarte por scp desde tu PC |
   | SSH | 22 | Personalizado → escribe `com.amazonaws.us-east-1.ec2-instance-connect` | terminal del navegador |
   | TCP personalizado | 8080 | 0.0.0.0/0 | lo llama API Gateway |
   | TCP personalizado | 8081 | 0.0.0.0/0 | lo llama API Gateway |
   | TCP personalizado | 8082 | Mi IP | administrador RabbitMQ (`rabbit-admin-api`) desde Postman |
   | TCP personalizado | 15672 | Mi IP | Management UI de `rabbit1` |
   | TCP personalizado | 15673 | Mi IP | Management UI de `rabbit2` |

   Los puertos AMQP (5672 y 5673) **no** se abren: solo los usan los servicios de la misma
   instancia. Si otro integrante necesita la Management UI o el administrador, agrega su IP
   como otra regla. Si la instancia ya existe, estas reglas se agregan en EC2 → **Grupos de
   seguridad** → `dsy1107-ec2-sg` → **Editar reglas de entrada**.

7. **Configurar almacenamiento**: deja 8 GiB gp3.
8. Despliega **Detalles avanzados** (*Advanced details*), baja hasta el final y en el campo
   **Datos de usuario** (*User data*) pega el contenido de `infra/ec2-user-data.sh`,
   **reemplazando antes** en la sección CONFIGURACIÓN:
   - `DB_HOST` con el endpoint del paso 1
   - `DB_PASSWORD` con la clave del RDS
   - `CORS_ORIGINS` puedes dejarlo como está por ahora
9. Botón **Lanzar instancia**. Luego **Ver todas las instancias**.
10. Espera a que **Estado de la instancia** sea *En ejecución* y las **Comprobaciones de
    estado** digan *2/2*. Toma un par de minutos.
11. Selecciona la instancia y copia la **Dirección IPv4 pública**. Anótala.

---

## Paso 3. Dejar que la EC2 llegue al RDS

Vuelve a la pestaña del security group del RDS, la que abriste en el paso 1.

1. Pestaña **Reglas de entrada** (*Inbound rules*) → botón **Editar reglas de entrada**.
2. **Agregar regla**:
   - Tipo: **PostgreSQL** (se completa solo con TCP 5432)
   - Origen: **Personalizado**, y en el campo escribe `dsy1107-ec2-sg`. Aparecerá como
     sugerencia el grupo con su id `sg-xxxxxxxx`; selecciónalo.
3. **Guardar reglas**.

Nunca pongas 0.0.0.0/0 en el 5432.

---

## Paso 4. Subir los JAR y arrancar los servicios

El user data ya instaló Java 21 y creó las dos bases, pero los servicios están esperando
los archivos.

### 4.0 Preparar RabbitMQ (una sola vez por instancia)

Instala Docker, crea las credenciales del clúster y registra `rabbit-admin-api`. Sirve
también para una instancia que ya estaba funcionando con la EV2.

En **Git Bash**, desde la carpeta del repo:

```bash
scp -i /c/Users/diego/aws/labsuser.pem infra/ec2-rabbitmq-setup.sh ec2-user@<IP-PUBLICA>:/tmp/
ssh -i /c/Users/diego/aws/labsuser.pem ec2-user@<IP-PUBLICA>
```

Ya dentro de la instancia, elige una clave nueva para RabbitMQ (no `labpass`) y ejecuta:

```bash
read -rsp "Clave RabbitMQ: " RMQ && echo
sudo RABBITMQ_PASSWORD="$RMQ" bash /tmp/ec2-rabbitmq-setup.sh
# En t3.medium puedes dar más memoria a cada Java:
# sudo RABBITMQ_PASSWORD="$RMQ" JAVA_XMX=512m bash /tmp/ec2-rabbitmq-setup.sh
exit
```

`read -s` evita que la clave quede en el historial. Queda guardada solo en la instancia:
en `/opt/pedidos360/rabbitmq/.env` (permisos 600) y en los servicios. El usuario de
RabbitMQ es `pedidos360`.

### 4.1 Compilar y subir

En **Git Bash**, desde la carpeta del repo:

```bash
cd /c/Users/diego/Documents/cloud-nativa-angular-ev1/cloud-native-angular
chmod 400 /c/Users/diego/aws/labsuser.pem
./infra/deploy-ec2.sh <IP-PUBLICA> /c/Users/diego/aws/labsuser.pem
```

Eso compila los tres servicios y sube los JAR y el compose del clúster
(`infra/rabbitmq-cloud/`). Después levanta los dos nodos RabbitMQ, que forman el clúster
solos, aplica la política de retención, reinicia los servicios y muestra los códigos de
prueba. Al final debe imprimir:

```
pedidos360-rabbit1: healthy
pedidos360-rabbit2: healthy
running_nodes: ['rabbit@rabbit1', 'rabbit@rabbit2']
active
active
active
publico=200
orders sin token=401
catalog sin token=401
admin rabbit sin token=401
```

y la lista de las 9 colas `pedidos360.*`, con 1 consumer en cada `.queue`.

### 4.2 Revisar desde la instancia

1. EC2 → **Instancias** → selecciona la tuya → botón **Conectar** (*Connect*).
2. Pestaña **EC2 Instance Connect** → botón **Conectar**. Se abre una terminal en el navegador.
3. Ejecuta:

```bash
systemctl is-active catalogo-api pedidos-api rabbit-admin-api   # active tres veces
curl http://localhost:8080/api/publico            # 200 con un JSON
curl -i http://localhost:8080/api/orders          # 401
curl -i http://localhost:8081/api/catalog/products # 401
sudo docker ps                                    # pedidos360-rabbit1 y rabbit2 (healthy)
sudo docker exec pedidos360-rabbit1 rabbitmqctl cluster_status   # Running Nodes: los dos
sudo docker exec pedidos360-rabbit1 rabbitmqctl list_policies    # pedidos360-principales
```

Si algo no arranca: `sudo journalctl -u pedidos-api -n 50 --no-pager`. Para RabbitMQ:
`sudo docker logs --tail 50 pedidos360-rabbit1`.

### 4.3 Comprobar desde tu PC

- `http://<IP-PUBLICA>:8080/api/publico` debe responder el JSON del endpoint público. Si
  no carga, revisa las reglas 8080 y 8081 del paso 2.
- `http://<IP-PUBLICA>:15672` abre la Management UI de `rabbit1` (usuario `pedidos360` y la
  clave del paso 4.0). En **Overview → Nodes** deben aparecer `rabbit@rabbit1` y
  `rabbit@rabbit2`. Para `rabbit2`, el puerto 15673.
- El monitor de colas también funciona contra la nube:

```bash
RABBITMQ_MANAGEMENT_URL=http://<IP-PUBLICA>:15672 RABBITMQ_USER=pedidos360 \
RABBITMQ_PASSWORD='<clave>' python monitor/monitor_queues.py --una-vez
```

### 4.4 Administrador y fallo controlado (EP4-08 y EP4-09)

Los dos exigen un token de Entra con rol **Admin** (se obtiene desde la app con la cuenta
`admin.p360`). No pasan por el API Gateway: se llaman directo a la instancia.

| Qué | Request |
|---|---|
| Estado de una DLQ | `GET http://<IP-PUBLICA>:8082/api/admin/rabbit/queues/pedidos360.cocina.dlq` |
| Crear cola | `POST http://<IP-PUBLICA>:8082/api/admin/rabbit/queues` con `{"nombre":"lab.auditoria.queue","ttlMs":60000}` |
| Fallo controlado en cocina | `POST http://<IP-PUBLICA>:8080/api/lab/pedidos/aceptar` con `{"pedidoId":900,"clienteEmail":"demo@example.com","falloCocina":true}` |

Sin token responden 401; con otro rol, 403. El resto de los endpoints está en el README.

---

## Paso 5. API Gateway

### 5.1 Preparar el archivo

Abre `infra/api-gateway-openapi.yaml` y reemplaza, con Ctrl+H:

- `TU-EC2-DNS` → la IP pública de la EC2
- `TU-EC2-CATALOGO-DNS` → la misma IP (cambia solo el puerto, 8081)
- El dominio de Vercel ya viene puesto en el CORS; cámbialo solo si usas otro

Guarda el archivo. **No lo subas al repo con la IP dentro**, es un valor de tu sesión.

### 5.2 Importar

1. Buscador → **API Gateway**.
2. Botón **Crear API** (*Create API*).
3. En la tarjeta **HTTP API**, en vez de *Compilar*, usa el enlace **Importar**
   (*Import*). Si no aparece, presiona **Compilar** y luego busca **Import** en el menú
   lateral de la API creada.
4. Arrastra o pega el contenido de `api-gateway-openapi.yaml` y presiona **Importar**.
5. Se crea la API con sus rutas, integraciones, CORS y el authorizer.

### 5.3 Verificar el authorizer

1. En la API creada, menú izquierdo → **Autorización** (*Authorization*).
2. Debe existir el authorizer JWT con:
   - Issuer: `https://login.microsoftonline.com/6bf42f50-ccc9-46e4-ae46-2b6534675957/v2.0`
   - Audience: `371e0368-c37d-4768-9266-0212824e67ba`
   - Scope: `Pedidos.Read`
3. Menú izquierdo → **Rutas** (*Routes*): confirma que `/api/publico` esté **sin**
   authorizer y el resto **con** authorizer.

### 5.4 Invoke URL

1. Menú izquierdo → **Etapas** (*Stages*).
2. Si existe la etapa **$default** con implementación automática activada, copia su
   **Invoke URL**: queda como `https://abc123.execute-api.us-east-1.amazonaws.com`, **sin**
   sufijo.
3. Si en cambio creaste una etapa `prod`, la URL termina en `/prod`. Usa la que
   corresponda; lo importante es que sea la que muestra la consola.
4. Prueba desde tu PC:

```bash
curl -i <INVOKE-URL>/api/publico     # 200
curl -i <INVOKE-URL>/api/orders      # 401
```

Si `/api/publico` da 500 o 503, el Gateway no está alcanzando la EC2: revisa la IP en las
integraciones y que los puertos 8080 y 8081 estén abiertos.

---

## Paso 6. Frontend contra el Gateway

1. Abre `cloud-native-angular/src/environments/environment.ts` y reemplaza:

```ts
apiBaseUrl: "<INVOKE-URL>",
catalogoBaseUrl: "<INVOKE-URL>",
```

2. En Git Bash o PowerShell:

```bash
cd cloud-native-angular
npm start
```

3. Abre `http://localhost:4200`, inicia sesión y entra a Pedidos.
4. F12 → pestaña **Red** (*Network*): las llamadas deben ir al dominio `execute-api` y
   llevar el encabezado `Authorization: Bearer ...`.

Cuando termines la demo, deja `environment.ts` de vuelta en `http://localhost:8080` y
`http://localhost:8081` para seguir desarrollando, o usa `environment.prod.ts` para la
versión publicada.

---

## Paso 7. Pruebas de la entrega

| Código | Prueba | Cómo |
|---|---|---|
| 200 | `/api/publico` sin token | `curl -i <INVOKE-URL>/api/publico` |
| 401 | `/api/orders` sin token | `curl -i <INVOKE-URL>/api/orders` |
| 401 | token inválido | `curl -i -H "Authorization: Bearer abc" <INVOKE-URL>/api/orders` |
| 200 | listado con token válido | desde la app, en la pestaña Red |
| 403 | operación sin el rol | Cliente intentando cambiar un estado |

Persistencia en RDS: crea un pedido, acéptalo y confirma en la app que el stock bajó.
Para verlo en la base, desde la terminal de la instancia:

```bash
psql -h <ENDPOINT-RDS> -U postgres -d pedidosdb  -c "SELECT id, estado, total FROM pedidos;"
psql -h <ENDPOINT-RDS> -U postgres -d catalogodb -c "SELECT nombre, stock FROM productos;"
```

---

## Paso 8. Publicar el frontend en Vercel (opcional)

1. Completa `environment.prod.ts` con el Invoke URL y el dominio que tendrá en Vercel.
2. En `vercel.com`: **Add New → Project**, importa el repo y en **Root Directory** elige
   `cloud-native-angular`. El `vercel.json` ya trae build, carpeta de salida y ruteo.
3. **Deploy** y copia el dominio.
4. Portal de Entra → **App registrations** → app del frontend → **Authentication** →
   **Add a platform** → **Single-page application** → pega el dominio.
5. En la terminal de la EC2, agrega ese dominio a CORS:

```bash
sudo systemctl edit --full pedidos-api     # edita Environment=CORS_ORIGINS=...
sudo systemctl edit --full catalogo-api
sudo systemctl daemon-reload && sudo systemctl restart pedidos-api catalogo-api
```

---

## Cada vez que retomes el laboratorio

1. **Start Lab** y esperar el punto verde.
2. EC2 → Instancias → selecciona la tuya → **Estado de la instancia** → **Iniciar instancia**.
3. Copia la **nueva IP pública**, porque cambia.
4. API Gateway → tu API → **Integraciones**: edita cada una y reemplaza la IP antigua.
5. En la terminal de la instancia: `systemctl is-active pedidos-api catalogo-api rabbit-admin-api`
   y `sudo docker ps`. Docker y los dos nodos RabbitMQ arrancan solos con la instancia y
   vuelven a formar el clúster; tardan cerca de un minuto en quedar *healthy*.

Para evitar el paso 3 y 4: EC2 → menú izquierdo **IP elásticas** → **Asignar dirección IP
elástica** → **Asignar** → selecciónala → **Acciones** → **Asociar dirección IP elástica**
→ elige la instancia. Si el laboratorio no te deja asignarla, sigue con la IP dinámica.

---

## Problemas frecuentes

| Síntoma | Qué revisar |
|---|---|
| `deploy-ec2.sh` no conecta | Regla SSH 22 desde Mi IP; que la IP del comando sea la actual; `chmod 400` en el .pem |
| Los servicios no arrancan | `sudo journalctl -u pedidos-api -n 50 --no-pager`; casi siempre es `DB_HOST` o `DB_PASSWORD` mal escritos en el user data |
| Error de conexión a la base | Regla PostgreSQL 5432 del SG del RDS con origen `dsy1107-ec2-sg` |
| Gateway responde 503 | IP vieja en las integraciones, o los servicios detenidos |
| La app sigue llamando a localhost | `environment.ts` sin el Invoke URL, o falta reiniciar `npm start` |
| 401 con sesión iniciada | Issuer o audience del authorizer distintos a los del token; revisa los claims en el dashboard |
| 403 con token válido | Al usuario le falta el App Role en Entra ID |
| Crear o aceptar un pedido responde 500 | RabbitMQ caído: `sudo docker ps`; si falta un nodo, `cd /opt/pedidos360/rabbitmq && sudo docker compose up -d` |
| Un nodo queda *unhealthy* tras reiniciar | Los dos nodos deben arrancar juntos: `sudo docker compose restart` en `/opt/pedidos360/rabbitmq`, nunca uno solo |
| Management UI no carga | Reglas 15672/15673 con tu IP actual (cambia si cambias de red) |
| La instancia se pone lenta | Memoria: `free -m`. En t3.small, baja `JAVA_XMX` o cambia a t3.medium y vuelve a correr el paso 4.0 con `JAVA_XMX=512m` |
