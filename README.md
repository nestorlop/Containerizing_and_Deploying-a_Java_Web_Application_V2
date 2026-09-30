# HTTP Server Framework - Extension

## Estado inicial del framework

Este proyecto parte del servidor HTTP básico desarrollado durante el curso. El servidor fue construido utilizando `ServerSocket` de Java, sin utilizar frameworks como Spring Boot.

En su versión inicial, el framework contaba con las siguientes características:

* Servidor secuencial, es decir, atendía una solicitud a la vez.
* Registro de rutas mediante lambdas, por ejemplo:
  `framework.get("/path", (req, resp) -> ...)`
* Servía archivos estáticos desde la carpeta `webroot`.
* Permitía obtener parámetros de las solicitudes mediante `req.getValue("name")`.
* Utilizaba variables de entorno como `PORT`, `APP_ENV` y `GREETING_PREFIX`.
* Contaba con un endpoint `/shutdown`, disponible únicamente en el entorno de desarrollo.
* El proyecto se empaquetaba como un JAR ejecutable utilizando Maven Shade Plugin.

### Limitaciones encontradas

Aunque el servidor cumplía con su objetivo inicial, tenía algunas limitaciones:

* Solo podía procesar una solicitud a la vez.
* No tenía un mecanismo específico para manejar correctamente la terminación del proceso, por ejemplo, cuando Docker enviaba una señal de apagado.
* No contaba con un `Dockerfile` para facilitar su ejecución mediante contenedores.
* El proyecto estaba configurado para Java 17.

A partir de estas limitaciones se realizaron varias mejoras al framework.

---

## Cambios realizados en esta extensión

### 1. Manejo concurrente de solicitudes

Una de las principales mejoras fue cambiar la forma en que el servidor procesa las solicitudes.

Anteriormente, cada conexión se atendía directamente desde el hilo principal. Esto significaba que mientras una solicitud estaba siendo procesada, las demás tenían que esperar.

Para solucionar esto se agregó un `ExecutorService` con un thread pool fijo:

```java
ExecutorService executor = Executors.newFixedThreadPool(10);
```

El tamaño del pool también puede configurarse mediante la variable de entorno `THREAD_POOL_SIZE`.

Cada conexión recibida se envía al pool para que pueda ser procesada de manera independiente:

```java
executor.submit(() -> handleRequest(socket));
```

De esta forma, el servidor puede atender varias solicitudes al mismo tiempo sin que una solicitud bloquee completamente a las demás.

---

### 2. Graceful Shutdown

También se agregó un mecanismo de apagado controlado para evitar que el servidor termine abruptamente mientras todavía está procesando solicitudes.

Para esto se agregó un Shutdown Hook:

```java
Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
```

Cuando la aplicación recibe una señal de terminación, se ejecuta el método `stop()`.

El proceso de apagado funciona de la siguiente manera:

1. Se cierra el `ServerSocket` para evitar aceptar nuevas conexiones.
2. Se solicita al `executor` que termine las tareas que están actualmente en ejecución.
3. El servidor espera hasta 30 segundos para que estas solicitudes terminen normalmente.
4. Si después de ese tiempo todavía existen tareas activas, se utiliza `shutdownNow()` para intentar detenerlas.
5. Finalmente, se espera un máximo adicional de 10 segundos.
6. Si ocurre una interrupción durante este proceso, se maneja correctamente mediante `InterruptedException`.

La idea es que el servidor pueda cerrarse de forma controlada, especialmente cuando se ejecuta dentro de Docker.

---

## 3. Actualización a Java 21

El proyecto también fue actualizado de Java 17 a Java 21.

En el `pom.xml` se modificó la versión utilizada por el compilador:

```xml
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
```

Para mantener la misma versión de Java al ejecutar la aplicación dentro de Docker, se utiliza Amazon Corretto 21 como imagen base.

---

## 4. Dockerfile

Se agregó un `Dockerfile` para poder empaquetar el servidor dentro de un contenedor.

```dockerfile
FROM amazoncorretto:21

WORKDIR /app

COPY target/httpserver-1.0-SNAPSHOT.jar app.jar

ENV PORT=9000

EXPOSE 9000

ENTRYPOINT ["java", "-jar", "app.jar"]
```

En este caso, el contenedor utiliza el puerto `9000` por defecto.

---

# Cómo compilar el proyecto

Primero se debe generar el JAR ejecutable con Maven:

```bash
mvn clean package
```

Al finalizar, se genera el archivo:

```text
target/httpserver-1.0-SNAPSHOT.jar
```

---

# Ejecución local

El servidor puede ejecutarse directamente con Java.

### Puerto por defecto

```bash
java -jar target/httpserver-1.0-SNAPSHOT.jar
```

### Utilizando otro puerto

```bash
PORT=9000 java -jar target/httpserver-1.0-SNAPSHOT.jar
```

### Cambiando el tamaño del thread pool

```bash
THREAD_POOL_SIZE=20 java -jar target/httpserver-1.0-SNAPSHOT.jar
```

El framework cuenta con los siguientes endpoints principales:

* `GET /hello?name=X` → devuelve un saludo, por ejemplo `Hello X`.
* `GET /pi` → devuelve el valor de π.
* `GET /shutdown` → permite apagar el servidor cuando está ejecutándose en el entorno de desarrollo.

---

# Ejecución con Docker

## Construir la imagen

Después de generar el JAR, se puede construir la imagen:

```bash
docker build -t <dockerhub-user>/httpserver-framework:1.0 .
```

## Ejecutar el contenedor

```bash
docker run -d \
  --name httpserver-test \
  -e PORT=9000 \
  -p 9000:9000 \
  <dockerhub-user>/httpserver-framework:1.0
```

En este caso, el puerto `9000` del equipo se conecta con el puerto `9000` del contenedor.

## Probar el servidor

Se pueden realizar algunas solicitudes para comprobar que la aplicación está funcionando:

```bash
curl http://localhost:9000/hello?name=Test

curl http://localhost:9000/pi

curl http://localhost:9000/shutdown
```

## Revisar los logs

Para revisar lo que está ocurriendo dentro del contenedor:

```bash
docker logs httpserver-test
```

---

# Deployment en Amazon EC2

Una vez comprobado el funcionamiento del contenedor localmente, la imagen puede publicarse en Docker Hub y posteriormente utilizarse desde una instancia EC2.

## 1. Publicar la imagen en Docker Hub

Primero se pueden crear los tags:

```bash
docker tag <user>/httpserver-framework:1.0 <user>/httpserver-framework:latest
```

Luego se publican:

```bash
docker push <user>/httpserver-framework:1.0

docker push <user>/httpserver-framework:latest
```

Esto permite disponer de las versiones `1.0` y `latest` en Docker Hub.

---

## 2. Ejecutar la aplicación en EC2

La aplicación se desplegó en una instancia Amazon Linux 2023.

Primero se instala Docker:

```bash
sudo yum update -y
sudo yum install -y docker
```

Después se inicia el servicio:

```bash
sudo service docker start
```

Para poder utilizar Docker con el usuario `ec2-user`:

```bash
sudo usermod -a -G docker ec2-user
```

Después de esto se debe cerrar la sesión SSH y volver a conectarse.

Finalmente, se descarga la imagen:

```bash
docker pull <user>/httpserver-framework:1.0
```

Y se ejecuta el contenedor:

```bash
docker run -d \
  --name httpserver-framework \
  --restart unless-stopped \
  -e PORT=9000 \
  -p 8081:9000 \
  <user>/httpserver-framework:1.0
```

Aquí el puerto `8081` de la instancia EC2 se conecta con el puerto `9000` del contenedor.

---

## 3. Configuración del Security Group

Para poder acceder al servidor desde Internet fue necesario permitir tráfico TCP en el puerto `8081` dentro del Security Group de la instancia EC2.

---

## 4. Verificar el deployment

Primero se puede comprobar que el contenedor esté ejecutándose:

```bash
docker ps
```

También se pueden revisar sus logs:

```bash
docker logs httpserver-framework
```

Finalmente, el servidor puede probarse utilizando el DNS público de la instancia:

```bash
curl http://<EC2-PUBLIC-DNS>:8081/hello?name=Cloud

curl http://<EC2-PUBLIC-DNS>:8081/pi
```

Si las solicitudes devuelven correctamente las respuestas esperadas, significa que el servidor está funcionando dentro del contenedor y puede ser accedido desde la instancia EC2.

---

# Evidencias

Durante el desarrollo se generaron diferentes evidencias para comprobar cada parte de la implementación:

| Archivo                | Descripción                                                             |
| ---------------------- | ----------------------------------------------------------------------- |
| `docker-build.png`     | Construcción de la imagen y resultado de `docker images`.               |
| `docker-run.png`       | Contenedor ejecutándose y resultado de `docker ps`.                     |
| `concurrency-test.png` | Prueba con 20 solicitudes realizadas en paralelo.                       |
| `shutdown-test.png`    | Prueba del apagado controlado y logs generados durante el proceso.      |
| `ec2-deploy.png`       | Contenedor ejecutándose en EC2 y prueba mediante una solicitud pública. |
| `dockerhub-tags.png`   | Imágenes publicadas en Docker Hub con los tags `1.0` y `latest`.        |

---

# Commit significativo

El commit principal de esta extensión corresponde a la implementación de la concurrencia y el apagado controlado.

**Mensaje del commit:**

```text
Implement concurrent request handling and graceful shutdown
```

Los principales cambios incluidos fueron:

* `pom.xml`: actualización de Java 17 a Java 21.
* `WebFramework.java`: incorporación del thread pool y graceful shutdown.
* `Dockerfile`: configuración para ejecutar el servidor dentro de Docker.

El hash del commit puede obtenerse con:

```bash
git log -1 --oneline
```

---

# Autor

**Nestor David Lopez Castaneda**
