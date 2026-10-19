# Primer administrador parametrizable

El plan solo sembraba usuarios demo en el perfil `dev`, de modo que en el perfil `lan`
no había forma de crear el primer Administrador. Se decide sembrar un **Administrador
idempotente en el primer arranque**, únicamente si no existe ningún usuario, con la
contraseña tomada de una variable de entorno. Así `lan` arranca sin credenciales fijas
en el repositorio.
