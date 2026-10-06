# Build Veil 4.5.0 + Mystrix patch

Windows PowerShell from the project root:

```powershell
.\gradlew :fabric:clean :fabric:build
```

The Fabric runtime jar is produced under:

```text
fabric\build\libs\
```

Use the remapped runtime jar, not a `sources` or `dev` jar.

At runtime remove the original Veil 4.5.0 jar and keep only this patched build.
The patched semantic version is `4.5.0+mystrix.1`, which remains in the 4.5.x compatibility line.
