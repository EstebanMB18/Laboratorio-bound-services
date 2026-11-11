#!/bin/sh
# Ejecuta Gradle instalado en PATH. Android Studio también puede sincronizar el proyecto desde su interfaz.
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
printf '%s\n' 'Gradle no está instalado en PATH. Abre el proyecto en Android Studio y configura Gradle 8.11.1.' >&2
exit 1
