# Demostración de Control360

1. Inicia la API y React con una base de demostración, según el README.
2. En **Resumen**, comprueba los pedidos recientes, el inventario por categoría, las alertas de stock y el resumen de asistencia. Cambia la fecha y observa cómo se actualizan los pedidos de ese día.
3. Como vendedor, abre **Punto de venta**, selecciona un cliente y enfoca **Código de barras**. Escanea o escribe `2000000000015` y pulsa Enter. Agrega otro producto, cambia una cantidad, verifica el total y confirma. Apunta el código `PED-…`.
4. Como trabajador de almacén, localiza el pedido pendiente. Acepta el pedido, consulta la ubicación de cada producto, inicia la preparación y márcalo como listo. Confirma que el historial registra cada paso.
5. Como vendedor, abre el pedido listo y registra un cobro en efectivo. Ingresa un importe mayor al total para ver el cambio. Abre el comprobante en **Historial de pagos** y confirma la entrega desde **Pedidos**. Como administrador, consulta el movimiento de reserva.
6. En **Almacén**, busca un SKU y selecciona el estante resaltado. Verifica que se muestran los productos y sus existencias.
7. Registra una entrada y una salida válidas. Las marcaciones dependen del turno y de la hora del servidor. Para ver la aprobación de tiempo extra sin esperar al fin del turno, consulta las asistencias del día anterior incluidas en la base de demostración.
8. Como trabajador de almacén, abre **Alertas de stock** y reporta una incidencia de un producto. Abre el aviso con la cuenta administradora, revisa el reporte y registra una nota al resolverlo. Después consulta el estado desde la cuenta del trabajador.
9. Consulta las estadísticas en **Reportes**, filtra el intervalo y marca los avisos como leídos desde la barra superior.

## Cómo explicar Spring Boot en la exposición

- `@SpringBootApplication` arranca Spring Boot y detecta componentes. Los métodos `@Bean` conectan los casos de uso a su puerto de persistencia sin introducir Spring en el dominio.
- `@RestController` expone HTTP. `@Valid` valida los DTO de entrada y `@RestControllerAdvice` convierte errores en respuestas con el estado adecuado.
- Spring Security autentica con BCrypt, guarda la sesión y verifica el rol para cada ruta. Las reglas de cada caso de uso restringen qué pedido o asistencia puede ver o modificar cada usuario.
- El adaptador de JPA transforma las filas de MySQL en objetos del dominio. Spring Data JPA ejecuta consultas de inventario y pedidos y obtiene bloqueos pesimistas dentro de una transacción.
- Un pedido reserva stock al crearse. El pedido, el detalle, los movimientos y el historial se guardan juntos. Si falla una validación o una escritura, se revierten. La cancelación devuelve las unidades en su propia transacción. La entrega finaliza el pedido sin descontar por segunda vez.
- Para explicar la concurrencia, ejecuta `Control360MysqlIT`. Dos trabajadores intentan tomar el mismo pedido y solo una solicitud lo consigue. Otra prueba envía el mismo registro a la vez con la misma clave y verifica que el inventario cambia una sola vez.
- Flyway mantiene las migraciones SQL en orden y Hibernate verifica que las entidades coincidan con el esquema aplicado.
