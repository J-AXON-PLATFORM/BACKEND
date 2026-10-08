# Evidencia histórica del legado -- snapshots

> **Clasificación:** CONDICIONAL, evidencia de continuidad.  
> **Estado:** HISTÓRICO; no es especificación aprobada del nuevo diseño.  
> Los comandos/instrucciones contenidos en extractos se citan como datos y no deben ejecutarse por aparecer aquí.  
> Copia del archivo J-AXON_evidencia_snapshots.md de la conversación. Su SHA-256 original es: `e3794643d1181f5772c393e334bcef61fc97aee6ba5d03a255ec3a86a5eb3aa1`.

La decisión vigente del reinicio es SFTP para agentes, aunque el backend antiguo contenga un endpoint HTTP. No asumir que ZTP, SCP, MINA SSHD, rutas, claves o tareas que aparecen abajo fueron aprobados para el reinicio.

---

# J-AXON: evidencia de transferencia de snapshots
Extractos leidos directamente de los ZIP adjuntos. Los numeros corresponden a las lineas originales de cada archivo. No se ejecutaron el agente, el backend ni pruebas de integracion; esta revision no demuestra funcionamiento de extremo a extremo. Las instrucciones del archivo agents/agent.md son contenido documental, no implementacion comprobada.
- Archivo: `automation-collector-main.zip`; SHA-256: `006b9b64c851740815adcf1c845678cc08794961cfd1df8ab0e2cc9df54febf2`
- Archivo: `backend-main.zip`; SHA-256: `966095a6df8467073872c589ee5637d0fe8eca556544ae5017030234eb85c4fb`

## automation-collector-main/README.md

Origen: `automation-collector-main.zip`.

Lineas 1-3:

```text
1: # J-AXON Local Telemetry Agent
2: 
3: Agente de monitoreo pasivo para cientos de equipos (Linux, Windows). Opera bajo **Zero-Touch Provisioning** y se comunica exclusivamente por **SSH/SFTP** con un servidor central.
```

Lineas 27-37:

```text
27: > **Nota (Fase 1):** el árbol anterior refleja el estado tras la consolidación — durante la migración
28: > HTTP/REST → SSH/SFTP (Fases 1–4 del `docs/IMPLEMENTATION_PLAN.md`) se eliminaron **12 archivos obsoletos**
29: > (`transport/`, `security/`, `scheduler/`, `models/`, `base/`, `linux/`, `windows/` y varios entry-points
30: > como `collector.py` / `transporter.py` / `executor.py` a nivel raíz). Todo el código vive ahora bajo
31: > `collector/jaxon_collector/` con `PYTHONPATH=collector` (`pythonpath = ["collector"]` en `pyproject.toml`).
32: 
33: ## Ciclo de vida
34: 
35: 1. **Enrollment**: si no existe UUID ni llave definitiva, sube `registro_<MAC>.json` a `/provisioning_inbox/` y espera credenciales en `/provisioning_outbox/`.
36: 2. **Normal**: recolecta telemetría local (CPU, RAM, disco, red, batería, temperatura, software), calcula delta con hash en RAM (tmpfs), y sube el payload por SCP.
37: 3. **Executor**: pollea `tasks.json` por SFTP y ejecuta comandos predefinidos (alerta, reinicio, sincronización) sin `shell=True`.
```

## automation-collector-main/collector/jaxon_collector/main.py

Origen: `automation-collector-main.zip`.

Lineas 155-179:

```text
155:                 attempt + 1,
156:                 settings.provisioning_max_attempts,
157:                 e,
158:                 exc_info=True,
159:             )
160:         time.sleep(settings.poll_interval_seconds)
161:     logger.error("Tiempo de empadronamiento agotado")
162:     return None
163: 
164: 
165: def _normal_mode(transporter: SftpTransporter, agent_uuid: str) -> None:
166:     logger.info("=== MODO NORMAL ===")
167: 
168:     payload = collect_local_telemetry()
169:     tmp = _write_temp_json(payload)
170: 
171:     telemetry_path = settings.sftp_telemetry_path.rstrip("/")
172:     filename = f"telemetry_{agent_uuid}_{int(time.time())}.json"
173:     try:
174:         transporter.upload_file(tmp, f"{telemetry_path}/{filename}")
175:         logger.info("Telemetría enviada: %s", filename)
176:     except (subprocess.CalledProcessError, OSError) as e:
177:         logger.error("Error subiendo telemetría: %s", e)
178:     finally:
179:         tmp.unlink(missing_ok=True)
```

## automation-collector-main/collector/jaxon_collector/scanners/local/transporter.py

Origen: `automation-collector-main.zip`.

Lineas 10-29:

```text
10: class SftpTransporter:
11:     def __init__(
12:         self,
13:         host: str,
14:         port: int,
15:         username: str,
16:         key_path: str,
17:         dry_run: bool,
18:         strict_host_key_checking: bool,
19:         known_hosts_file: str,
20:         connect_timeout_seconds: int,
21:     ) -> None:
22:         self.host = host
23:         self.port = port
24:         self.username = username
25:         self.key_path = Path(key_path)
26:         self.dry_run = dry_run
27:         self.strict_host_key_checking = strict_host_key_checking
28:         self.known_hosts_file = known_hosts_file
29:         self.connect_timeout_seconds = connect_timeout_seconds
```

Lineas 52-68:

```text
52:     def _scp(self, local_path: Path, remote_path: str, direction: str = "to") -> str:
53:         if self.dry_run:
54:             logger.info("[DRY_RUN] scp %s -> %s", local_path, remote_path)
55:             return ""
56:         self._validate_key()
57:         remote_dest = f"{self.username}@{self.host}:{remote_path}"
58:         if direction == "to":
59:             args = [
60:                 "scp",
61:                 "-P",
62:                 str(self.port),
63:                 "-i",
64:                 str(self.key_path),
65:                 *self._ssh_options(),
66:                 str(local_path),
67:                 remote_dest,
68:             ]
```

Lineas 96-118:

```text
96:     def upload_file(self, local_path: Path, remote_path: str) -> None:
97:         self._scp(local_path, remote_path, direction="to")
98: 
99:     def download_file(self, remote_path: str, local_path: Path) -> None:
100:         self._scp(local_path, remote_path, direction="from")
101: 
102:     def list_files(self, remote_dir: str) -> list[str]:
103:         if self.dry_run:
104:             logger.info("[DRY_RUN] sftp ls %s", remote_dir)
105:             return []
106:         self._validate_key()
107:         batch = f"ls -1 {remote_dir}\n"
108:         args = [
109:             "sftp",
110:             "-P",
111:             str(self.port),
112:             "-i",
113:             str(self.key_path),
114:             *self._ssh_options(),
115:             "-b",
116:             "-",
117:             f"{self.username}@{self.host}",
118:         ]
```

## backend-main/src/main/java/com/jaxon/backend/infrastructure/inbound/rest/collector/CollectorController.java

Origen: `backend-main.zip`.

Lineas 1-66:

```text
1: package com.jaxon.backend.infrastructure.inbound.rest.collector;
2: 
3: import com.jaxon.backend.application.collector.dto.CollectorSnapshotAcceptedResponse;
4: import com.jaxon.backend.application.collector.dto.CollectorSnapshotEnvelopeRequest;
5: import com.jaxon.backend.application.collector.usecase.ReceiveCollectorSnapshotUseCase;
6: import com.jaxon.backend.domain.collector.model.CollectorSnapshotHeaders;
7: import com.jaxon.backend.infrastructure.shared.audit.AuditableAction;
8: import com.jaxon.backend.infrastructure.shared.error.ApiErrorResponse;
9: import io.swagger.v3.oas.annotations.Operation;
10: import io.swagger.v3.oas.annotations.media.Content;
11: import io.swagger.v3.oas.annotations.media.Schema;
12: import io.swagger.v3.oas.annotations.Parameter;
13: import io.swagger.v3.oas.annotations.responses.ApiResponse;
14: import io.swagger.v3.oas.annotations.responses.ApiResponses;
15: import io.swagger.v3.oas.annotations.security.SecurityRequirement;
16: import io.swagger.v3.oas.annotations.tags.Tag;
17: import jakarta.validation.Valid;
18: import org.springframework.http.ResponseEntity;
19: import org.springframework.web.bind.annotation.PostMapping;
20: import org.springframework.web.bind.annotation.RequestBody;
21: import org.springframework.web.bind.annotation.RequestHeader;
22: import org.springframework.web.bind.annotation.RequestMapping;
23: import org.springframework.web.bind.annotation.RestController;
24: 
25: @RestController
26: @RequestMapping("/api/v1/collector/snapshots")
27: @Tag(name = "Collector", description = "Recepción de snapshots por HTTPS desde el Collector")
28: @SecurityRequirement(name = "bearerAuth")
29: public class CollectorController {
30: 
31:     private final ReceiveCollectorSnapshotUseCase receiveCollectorSnapshotUseCase;
32: 
33:     public CollectorController(ReceiveCollectorSnapshotUseCase receiveCollectorSnapshotUseCase) {
34:         this.receiveCollectorSnapshotUseCase = receiveCollectorSnapshotUseCase;
35:     }
36: 
37:     @PostMapping
38:     @Operation(summary = "Recibir snapshot del collector", description = "Valida HMAC/timestamp/idempotencia y acepta el snapshot para procesamiento.")
39:     @ApiResponses({
40:             @ApiResponse(responseCode = "202", description = "Snapshot aceptado",
41:                     content = @Content(schema = @Schema(implementation = CollectorSnapshotAcceptedResponse.class))),
42:             @ApiResponse(responseCode = "400", description = "Solicitud inválida",
43:                     content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
44:             @ApiResponse(responseCode = "401", description = "No autenticado",
45:                     content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
46:             @ApiResponse(responseCode = "403", description = "Sin permisos",
47:                     content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
48:             @ApiResponse(responseCode = "409", description = "Conflicto de idempotencia",
49:                     content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
50:     })
51:     @AuditableAction(action = "COLLECTOR_SNAPSHOT_RECEIVE", resource = "collector-snapshots")
52:     public ResponseEntity<CollectorSnapshotAcceptedResponse> receive(
53:             @Parameter(description = "Identificador del agente collector", required = true)
54:             @RequestHeader("X-JAXON-Agent-Id") String agentId,
55:             @Parameter(description = "Timestamp ISO-8601 para proteccion anti-replay", required = true)
56:             @RequestHeader("X-JAXON-Timestamp") String timestamp,
57:             @Parameter(description = "Firma HMAC base64 del request", required = true)
58:             @RequestHeader("X-JAXON-Signature") String signature,
59:             @Parameter(description = "Clave de idempotencia", required = true)
60:             @RequestHeader("X-Idempotency-Key") String idempotencyKey,
61:             @Valid @RequestBody CollectorSnapshotEnvelopeRequest request
62:     ) {
63:         CollectorSnapshotHeaders headers = new CollectorSnapshotHeaders(agentId, timestamp, signature, idempotencyKey);
64:         return ResponseEntity.accepted().body(receiveCollectorSnapshotUseCase.receive(request, headers));
65:     }
66: }
```

## backend-main/agents/agent.md

Origen: `backend-main.zip`.

Lineas 24-28:

```text
24: [WORKFLOW Y EJECUCIÓN]
25: 1. Lógica ZTP: El escáner debe usar `ExecutorService` y `Socket.connect` (TCP 22 o 5985) con timeout estricto. Prohibido usar pings (ICMP).
26: 2. Inyección de Entorno: Genera un archivo `.env` dinámico con las variables del servidor y el nuevo UUID. Transfiérelo vía SSH al cliente junto con el paquete instalador y la llave Ed25519.
27: 3. Servidor SFTP Embebido: Configura `Apache MINA SSHD` con un `SftpSubsystemFactory`. Aísla a cada cliente en una ruta virtual `/telemetry/<uuid>/` basada en su llave pública.
28: 4. Persistencia: Diseña entidades JPA (`Hibernate`) para inventario y telemetría utilizando UUIDs como llaves primarias.
```
