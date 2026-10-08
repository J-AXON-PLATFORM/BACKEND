# Alertas

> **Clasificación:** CONDICIONAL  
> **Instancia J-AXON:** CKP-JAXON-005 | 2026-09-29.

## Propósito

Notificar condiciones accionables antes de depender de usuarios para detectar incidentes.

## Umbrales iniciales

- core/readiness fallando >5 min durante ventana operativa;
- filesystem crítico >85 % = WARNING, >95 % = CRITICAL;
- backup esperado falló o riesgo de superar RPO de 1 h;
- degradación colectiva de Agents: >=10 % de Agents ACTIVE y mínimo 10 pasan a OFFLINE en <=10 min;
- snapshot freshness: >5 % de Agents ACTIVE online con snapshot >8 h durante >=15 min;
- API 5xx >5 % durante 5 min con mínimo 20 requests;
- SFTP/ingesta: >20 % de operaciones fallidas durante 10 min con muestra suficiente.

Estos son defaults configurables sujetos a baseline/operación real.

## Integraciones degradables

IA o n8n caídos generan señal operativa de capacidad degradada, no necesariamente SEV-1 del sistema completo si Inventory/Collector/Support/Identity siguen funcionando.

## Evitar

- alertar cada HTTP 500 individual;
- alertar cada Agent offline individual;
- alertar cada retry;
- thresholds sin muestra/ventana;
- duplicados/no accionables.

## Runbooks

Alertas importantes deben apuntar a dashboard y runbook sin incluir secretos.
