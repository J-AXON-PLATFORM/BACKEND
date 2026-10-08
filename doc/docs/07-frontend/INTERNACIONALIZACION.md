# Internacionalización y localización

> **Clasificación:** CONDICIONAL

## Activar si

La aplicación soporta múltiples idiomas, regiones o formatos culturales.

## Definir

- idiomas;
- fallback;
- traducciones;
- fechas;
- horas;
- zonas horarias;
- números;
- monedas;
- pluralización;
- dirección RTL;
- ordenamiento;
- contenido legal.

## Arquitectura

No concatenar textos traducibles de forma que rompa gramática.

Separar claves de traducción de contenido dinámico.

## Pruebas

- expansión de texto;
- caracteres no ASCII;
- RTL si aplica;
- formatos regionales;
- cambio de idioma;
- fallback.
