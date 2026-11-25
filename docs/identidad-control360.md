# Identidad visual de Control360

## Propuestas de logotipo

| Propuesta | Idea | Recurso |
| --- | --- | --- |
| **Confluencia** · elegida | Tres trazos convergen en un eje de salida. Representa pedidos, inventario y personas dentro de una misma operación. Los trazos gruesos se distinguen en un favicon. | [Isotipo SVG](../frontend/public/brand/isotipo-dark.svg) |
| **Trama** | Dos piezas que encajan representan la coordinación entre procesos. | [Propuesta SVG](../frontend/public/brand/concepto-trama.svg) |
| **Bastidor** | Cuatro esquinas enmarcan un punto de control para representar orden y precisión. | [Propuesta SVG](../frontend/public/brand/concepto-bastidor.svg) |

El logotipo completo tiene versiones para [fondo claro](../frontend/public/brand/control360-light.svg) y [fondo oscuro](../frontend/public/brand/control360-dark.svg). El [isotipo para fondo claro](../frontend/public/brand/isotipo-light.svg), el isotipo para fondo oscuro y el [favicon](../frontend/public/brand/favicon.svg) están disponibles en el mismo directorio.

## Color, tipo y espaciado

| Uso | Tema claro | Tema oscuro |
| --- | --- | --- |
| Fondo | `#F2F5EF` | `#13201A` |
| Superficie | `#FFFFFF` | `#1B2B23` |
| Texto principal | `#172720` | `#EDF3E9` |
| Acciones y foco | Verde petróleo | Verde claro |
| Acento de identidad | Lima `#D8FB91` | Lima `#D8FB91` |

La interfaz usa Inter. Las tablas mantienen cifras alineadas y estados con texto y color. La separación normal entre paneles es de 14 px y las tarjetas usan un radio de 11 px. Los valores están centralizados en [styles.css](../frontend/src/assets/styles.css).

## Componentes

- `PageTitle` y `Section` ordenan títulos, acciones y paneles.
- `DataTable`, `Status` y `Tag` presentan listas operativas y estados.
- `Field`, `Button` y `Dialog` comparten estilos y etiquetas accesibles.
- `Loading`, `Empty` y `ErrorNotice` muestran carga, listas vacías y errores.
- `OrderProgress` muestra la etapa del pedido según su estado actual.
- El minimapa mantiene los accesos a ubicaciones y las rutas desde Recepción.
