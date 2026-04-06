name: nCore
version: 2.0
main: pl.nelson.ncore.Main
api-version: '1.21'
author: Nelson

commands:
  setspawn:
    description: Define el punto de spawn global.
    permission: ncore.admin
  spawn:
    description: Te lleva al spawn del servidor.
    usage: /spawn
  rtp:
    description: Teletransportarse a un lugar aleatorio.
  regresar:
    description: Te devuelve a donde te desconectaste manualmente.

permissions:
  ncore.admin:
    description: Acceso a comandos de administración.
    default: op
