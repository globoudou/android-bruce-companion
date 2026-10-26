# Bruce Companion — récupération de wardriving pour QField

Application Android (Kotlin + Jetpack Compose) qui se connecte à un ESP32 sous
firmware [Bruce](https://github.com/BruceDevices/firmware), récupère les fichiers
de wardriving par la WebUI, et les met en forme en **GeoPackage** prêt à ouvrir
dans [QField](https://qfield.org).

## Ce que fait l'app

1. **Connexion** au Bruce :
   - via son **point d'accès** (l'app rejoint le Wi-Fi du Bruce et lie le trafic
     à ce réseau sans Internet — sinon Android routerait tout sur la 4G), ou
   - en **réseau courant** si le téléphone a déjà rejoint le Bruce.
   - Identifiants et adresse mémorisés d'une fois à l'autre.
2. **Fichiers** : liste le dossier `/BruceWardriving` de la carte SD, sélection
   des CSV à récupérer.
3. **Traitement** :
   - fusion **pondérée** des observations par MAC pour estimer la position de
     chaque émetteur (barycentre pondéré par la puissance du signal et la
     précision GPS), avec **détection des émetteurs mobiles** (exclus de
     l'estimation) et rayon d'incertitude ;
   - écriture dans un **nouveau** GeoPackage ou **complétion d'un existant**
     (la position s'affine au fil des sessions) ;
   - **suppression des fichiers sur la carte** après coup, sur confirmation.
4. **Partage** du GeoPackage vers QField via le sélecteur Android.

Le GeoPackage contient deux couches : `observations` (contacts bruts) et
`access_points` (émetteurs estimés), avec un style QGIS catégorisé par sécurité
embarqué (best-effort, à valider selon la version de QField).

## Notes techniques

- Le GeoPackage est écrit directement en SQLite (en-tête GeoPackageBinary + WKB),
  sans embarquer GDAL.
- Bruce n'enregistre chaque MAC **qu'une fois par session** (au premier contact,
  souvent en limite de portée) : la précision de position s'améliore surtout en
  **cumulant plusieurs sessions** dans le même GeoPackage.
- `minSdk 29` (la connexion au point d'accès par l'app utilise `WifiNetworkSpecifier`).

## Installer

Télécharge le dernier `bruce-companion-x.y.z.apk` dans les [Releases](../../releases)
et ouvre-le sur le téléphone (autoriser l'installation depuis cette source si
Android le demande).

## Compiler

GitHub Actions compile et teste à chaque push sur `main`, puis publie (ou met à
jour) la release `v` + contenu du fichier `VERSION`. Pour sortir une nouvelle
version, change `VERSION`.
En local : `./gradlew assembleRelease` (JDK 17 et SDK Android requis).

L'APK est signé avec la clé `app/bruce-release.jks` versionnée dans le dépôt, pour
que chaque build puisse mettre à jour l'app installée. Suffisant pour un usage
personnel ; pour une diffusion publique, remplace-la par une clé gardée secrète.

## Licence

Usage personnel. Le wardriving et son exploitation doivent respecter la
réglementation locale.
