# Astro Rush

Shooter espacial 2D para Android. Se compila en la nube con GitHub Actions y se sube desde Termux, sin Android Studio.

- Arrastra el dedo para mover la nave (dispara sola).
- Esquiva y destruye asteroides. Los grandes aguantan más golpes.
- Los orbes que sueltan dan una vida extra (rojo) o disparo triple (amarillo).
- Tiene menú, pausa, game over y récord guardado.

## Subirlo desde Termux

```bash
pkg install -y git gh unzip
termux-setup-storage
cd ~ && unzip ~/storage/downloads/astro-rush.zip && cd astro-rush

git config --global user.name "Tu Nombre"
git config --global user.email "tu@correo.com"
gh auth login

git init -b main
git add .
git commit -m "Astro Rush"
gh repo create astro-rush --public --source=. --push
```

Al terminar el `push`, GitHub Actions compila el APK (unos 3-5 minutos).
Mira el progreso con `gh run watch` o en la pestaña **Actions** del repositorio.

## Descargar el APK

- Desde el navegador del teléfono: `https://github.com/TU_USUARIO/astro-rush/releases`
  y toca el archivo `astro-rush.apk`.
- O desde Termux: `gh release download --pattern "*.apk" --dir ~/storage/downloads`

Para instalarlo, Android te pedirá permitir "instalar apps desconocidas" al navegador
o al gestor de archivos que uses.

## Cambiar el juego

Todo el juego está en `app/src/main/java/com/example/spacegame/GameView.java`
(arriba del archivo hay una lista de dónde tocar para cambiar dificultad, velocidad, textos y colores).

Después de editar:

```bash
git add .
git commit -m "Mis cambios"
git push
```

Cada `push` genera un APK nuevo en Releases. Se instala encima del anterior sin perder el récord.

## Cómo funciona

- `.github/workflows/build.yml`: el workflow (Java 17 + Gradle 8.7 + `assembleDebug`).
- `app/build.gradle`: configuración de la app (nombre de paquete, versión, firma).
- `app/debug.keystore`: llave de firma fija, para que las actualizaciones se instalen encima.
  Es una llave de depuración con contraseña conocida: sirve para uso personal.
  Si algún día publicas en Play Store, crea una llave propia y mantenla privada.
- No hay `gradlew`: el workflow instala Gradle por su cuenta.

## Si el build falla

Entra a la pestaña **Actions**, abre la ejecución en rojo y copia el error del paso
"Compilar el APK". Con ese texto se arregla rápido.
