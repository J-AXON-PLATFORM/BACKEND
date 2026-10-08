# Contexto del módulo — Pedidos

- ID del módulo: `MOD-ORDERS`
- Responsabilidad: gestionar el ciclo de vida de pedidos.
- Datos propios: `Order`, `OrderItem`.
- Dependencias: Inventario, Pagos.
- Requisito principal: `REQ-ORD-001`.
- Flujos principales: `FLOW-ORD-001`, `FLOW-ORD-002`.
- Máquina de estados: `CREATED -> CONFIRMED -> PAID -> SHIPPED -> DELIVERED`.
- Contratos relevantes: API de pedidos, reserva de inventario, pago.
- Pruebas principales: unitarias del dominio, integración de persistencia/contratos y E2E de compra.
