-- Control360-DW. Consultas de solo lectura.
-- Bloque A. Ejecutar en cada conexión de Workbench, incluso si el esquema está vacío.
SELECT @@server_uuid AS servidor,
       @@hostname AS equipo_mysql,
       @@port AS puerto_interno_mysql,
       VERSION() AS version_mysql,
       DATABASE() AS esquema_seleccionado;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'control360_dw' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Bloque B. Ejecutar si aparece flyway_schema_history.
SELECT version, description, success
FROM control360_dw.flyway_schema_history
ORDER BY installed_rank;

-- Bloque C. Ejecutar cuando el esquema contiene las tablas de Control360 hasta V5.
SELECT 'Usuarios' AS modulo, COUNT(*) AS filas FROM control360_dw.app_users
UNION ALL SELECT 'Trabajadores', COUNT(*) FROM control360_dw.workers
UNION ALL SELECT 'Turnos', COUNT(*) FROM control360_dw.shifts
UNION ALL SELECT 'Clientes', COUNT(*) FROM control360_dw.customers
UNION ALL SELECT 'Pedidos', COUNT(*) FROM control360_dw.orders
UNION ALL SELECT 'Líneas de pedido', COUNT(*) FROM control360_dw.order_lines
UNION ALL SELECT 'Historial de pedidos', COUNT(*) FROM control360_dw.order_history
UNION ALL SELECT 'Productos', COUNT(*) FROM control360_dw.products
UNION ALL SELECT 'Categorías', COUNT(*) FROM control360_dw.categories
UNION ALL SELECT 'Ubicaciones', COUNT(*) FROM control360_dw.locations
UNION ALL SELECT 'Inventario', COUNT(*) FROM control360_dw.inventory
UNION ALL SELECT 'Movimientos de stock', COUNT(*) FROM control360_dw.stock_movements
UNION ALL SELECT 'Asistencias', COUNT(*) FROM control360_dw.attendance
UNION ALL SELECT 'Auditoría de asistencia', COUNT(*) FROM control360_dw.attendance_audit
UNION ALL SELECT 'Pagos', COUNT(*) FROM control360_dw.order_payments
UNION ALL SELECT 'Incidencias', COUNT(*) FROM control360_dw.inventory_incidents
UNION ALL SELECT 'Notificaciones', COUNT(*) FROM control360_dw.notifications;

SELECT role AS rol, COUNT(*) AS cuentas
FROM control360_dw.app_users
GROUP BY role
ORDER BY role;

-- Bloque D. Relacionar los registros de la aplicación con su producto y ubicación.
SELECT i.id AS incidencia, p.sku, p.name AS producto, i.type AS tipo, i.status AS estado, i.created_at AS registrado
FROM control360_dw.inventory_incidents AS i
JOIN control360_dw.products AS p ON p.id = i.product_id
ORDER BY i.id DESC
LIMIT 5;

SELECT o.id AS pedido, o.status AS estado, o.total, p.method AS metodo_pago,
       CASE WHEN o.status = 'CANCELADO' THEN 'ANULADO'
            ELSE COALESCE(p.status, 'SIN REGISTRO') END AS estado_pago
FROM control360_dw.orders AS o
LEFT JOIN control360_dw.order_payments AS p ON p.order_id = o.id
ORDER BY o.id DESC
LIMIT 5;
