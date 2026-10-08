# Infraestructura

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Describir topología y recursos necesarios sin imponer proveedor ni adelantar nube.

## Topología v1 aprobada

- modelo base **on-premise/LAN** y no público por defecto;
- usuarios web acceden por **un único origen HTTPS** con nombre DNS estable;
- un reverse proxy termina TLS y enruta frontend, `/api/...` y SSE hacia componentes internos;
- Production usa certificado confiable de la PKI/CA de la organización;
- TLS 1.3 preferido, TLS 1.2 permitido por compatibilidad, TLS 1.0/1.1 deshabilitados;
- 443/TCP es el puerto normal web; 80, si existe, solo redirige a HTTPS;
- PostgreSQL, backend directo, paneles administrativos y otros servicios internos no se exponen normalmente a usuarios finales;
- SFTPGo conserva endpoint separado para Agents y Discovery Executors autorizados;
- n8n/observabilidad se consideran infraestructura interna con acceso restringido.

Representación conceptual:

```text
LAN
 |
 DNS estable
 |
 HTTPS 443
 |
Reverse Proxy
 +-- Frontend
 +-- Backend /api + SSE

Agents/Discovery Executors --SFTP--> SFTPGo
Backend --> PostgreSQL
Backend --> DocumentStorage filesystem persistente
```

## Storage

- `DocumentStorage` abstrae almacenamiento documental;
- v1 usa filesystem persistente, nunca filesystem efímero del contenedor;
- medio físico concreto (disco/volumen/NAS) `POR_DECIDIR` según instalación;
- object storage/cloud no se requiere en v1 y queda como posible evolución;
- mailbox SFTP está separado del storage permanente.

## Secretos

- Local puede usar `.env` ignorado por Git;
- QA/Production no usan `.env` plano como almacén permanente;
- v1 usa archivos protegidos/runtime secrets fuera del repositorio y montados solo en componentes que los necesitan;
- secretos separados por entorno y propósito;
- secret manager empresarial concreto solo si escala/infra lo justifica.

## Observabilidad

Production/QA usan inicialmente stack on-premise Grafana + Prometheus + Loki + Tempo sin HA obligatoria. Su fallo no debe tumbar el core.

## Capacidad

Perfil de benchmark inicial: 8 vCPU, 32 GiB RAM, SSD/NVMe y 1 Gbps. No es hardware mínimo Productivo. Hardware Production se dimensiona mediante pruebas representativas y puede escalar verticalmente antes de introducir complejidad distribuida.

## Backups/DR

- al menos una copia fuera del dominio de fallo primario;
- Production backups cifrados;
- RPO <=1 h y RTO <=8 h iniciales, sujetos a prueba;
- ver `COPIAS_SEGURIDAD_RECUPERACION.md` y `RECUPERACION_DESASTRES.md`.

## Seguridad

Aplicar mínimo privilegio, segmentación, hardening, patching, cifrado y auditoría según componente. No convertir la LAN en trust implícito.

## Reproducibilidad

Compose, configuración no secreta, scripts de deployment/backup/restore y health checks deben quedar versionados. IaC formal se evalúa cuando se conozca infraestructura real.

## Pendiente

- reverse proxy concreto;
- nombres DNS concretos;
- PKI concreta de cada instalación;
- hardware Productivo definitivo;
- medio físico de `DocumentStorage`;
- secret manager empresarial;
- IaC real;
- topología física final/virtualización/VLAN/VPN/HA/cloud.
