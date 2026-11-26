# Control360-DW en MySQL Workbench

## 1. Identifica dónde están tus registros

La configuración de Spring Boot se encuentra en [application.yml](../backend/src/main/resources/application.yml). La URL usa `MYSQL_HOST`, `MYSQL_PORT` y `MYSQL_DATABASE`. `MYSQL_USER` y `MYSQL_PASSWORD` se cargan desde el entorno antes de arrancar el backend. Workbench es un cliente para consultar el servidor MySQL.

1. Abre la terminal donde se ejecutó el backend con éxito. Busca la línea de Flyway que empieza con `Database: jdbc:mysql://`. Anota el host, el puerto y el esquema. Si existen variables avanzadas como `SPRING_DATASOURCE_URL` o `SPRING_FLYWAY_URL`, comprueba también su configuración efectiva sin publicar las credenciales.
2. Si tus datos están en Docker Desktop, abre CMD y ejecuta `docker port control360-demo-nueva-mysql-1 3306`. En la configuración anterior, Windows accedía a ese contenedor por `127.0.0.1:13308`.
3. En Workbench crea una conexión para cada servidor. Usa **Standard TCP/IP**, host `127.0.0.1`, el puerto de la conexión y el usuario correspondiente. En **Default Schema**, indica `control360_dw`.
4. Ejecuta el bloque A de [mysql-verificacion.sql](mysql-verificacion.sql) en ambas conexiones. Compara `servidor` y la lista de tablas. Un UUID diferente significa otra instancia de MySQL aunque el esquema tenga el mismo nombre. El puerto interno puede mostrar `3306` dentro de Docker aunque uses `13308` desde Windows.
5. Si aparece `flyway_schema_history`, ejecuta el bloque B. Tras verificar que existen las tablas de V5, ejecuta los bloques C y D. Compara los recuentos y confirma algún pedido o incidencia con un ID que reconozcas de la aplicación.

La captura inicial de Workbench muestra un esquema de Django seleccionado y la creación de otros dos esquemas. Para Control360-DW trabaja con `control360_dw`. Conserva el acceso y los datos de `control360demo5a3306c1` y `control360_django` por separado.

## 2. Conserva o traslada la base antes de cambiar la conexión

- Si `3307` apunta al mismo servidor y ya contiene los registros esperados de `control360_dw`, conserva una copia y sigue con la configuración.
- Si tus datos están en `13308` y `3307` pertenece a otro servidor, sigue usando la conexión que tiene los datos hasta completar la copia. Detén el backend antes de la exportación final para que los registros que se hayan guardado justo antes del traslado queden incluidos.
- En Workbench, conéctate al **origen** y abre **Server → Data Export**. Selecciona únicamente `control360_dw` y elige **Dump Structure and Data** y **Export to Self-Contained File**. Incluye `flyway_schema_history`. En las opciones avanzadas, desactiva `add-drop-table` para exportar sin sentencias de borrado. Guarda el archivo fuera del repositorio.
- En el **destino**, confirma con el bloque A que `control360_dw` está vacío. En **Server → Data Import**, selecciona el archivo, confirma el servidor y selecciona `control360_dw` como esquema de destino. Si el destino ya tiene tablas o registros, conserva ambos esquemas y compara los datos antes de importar encima.
- Repite las consultas de comprobación en el destino y contrasta los recuentos y algunos IDs con el origen. Conserva el respaldo y la base de origen hasta comprobar el nuevo acceso. Los hashes de las cuentas se conservan al exportar sus tablas.

Para crear un esquema vacío de instalación nueva, usa `CREATE DATABASE IF NOT EXISTS control360_dw CHARACTER SET utf8mb4;` en la conexión de `3307`. Usa una cuenta de aplicación dedicada con permisos limitados a `control360_dw.*`, suficientes para que Flyway aplique las migraciones. Puedes crearla desde **Users and Privileges** de Workbench y asignarle esos permisos en **Schema Privileges**. En Windows, si el servidor de Workbench ya escucha en `3307`, mantén el contenedor de origen en `13308` mientras verificas los datos.

## 3. Configura y arranca en Windows

Cuando hayas verificado el destino, edita el archivo `.env` de la raíz de `Control360-DW`. Si falta y vas a hacer una instalación nueva, copia `.env.example`.

```dotenv
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3307
MYSQL_DATABASE=control360_dw
MYSQL_USER=control360
MYSQL_PASSWORD=
DEMO_DATA=false
```

Completa localmente `MYSQL_PASSWORD` con la contraseña de la cuenta autorizada en el servidor de `3307`. Mantén los valores de administración que ya usas. Al trasladar una base, Flyway usa el historial restaurado y Hibernate valida el esquema. En una instalación vacía, Flyway crea las tablas versionadas. Para una base con tablas y sin historial Flyway, revisa el contenido antes de arrancar.

Desde **CMD** en la carpeta clonada, carga las variables y arranca el backend:

```cmd
cd /d "%USERPROFILE%\Control360-DW"
for /f "usebackq eol=# tokens=1,* delims==" %A in (".env") do @set "%A=%B"
cd backend
mvn spring-boot:run
```

Espera `Started Control360Application`. Verifica en el registro la URL `jdbc:mysql://127.0.0.1:3307/control360_dw`. Si Windows aún muestra el puerto anterior, revisa `.env` y las variables cargadas en esa terminal.

## 4. Comprueba que se guarda un registro

1. En Workbench, pulsa **Refresh** en **Schemas** y abre `control360_dw → Tables`. Revisa que Flyway tenga las versiones de la rama actual marcadas como exitosas.
2. En Control360, registra una incidencia de prueba como trabajador o un pedido como vendedor. Anota su identificador.
3. Ejecuta el bloque D en Workbench y localiza el registro. Vuelve a consultarlo después de reiniciar el backend para comprobar que permanece guardado.
