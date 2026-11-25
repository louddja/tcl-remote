# Télécommande TCL (Google TV)

Application Android qui pilote une TV TCL Google TV / Android TV par le Wi‑Fi.

## Obtenir l'APK avec GitHub (gratuit)

1. Créez un compte sur github.com, puis un nouveau dépôt (bouton « New »), par exemple `telecommande-tcl`.
2. Dézippez ce dossier sur votre ordinateur.
3. Dans le dépôt : « Add file » → « Upload files », puis glissez **tout le contenu** du dossier
   (y compris le dossier `.github`), et cliquez sur « Commit changes ».
4. Onglet **Actions** : la compilation « Compiler l'APK » démarre toute seule (environ 5 minutes).
5. Quand elle est verte, cliquez dessus et téléchargez l'artefact **telecommande-tcl** (un zip contenant `app-debug.apk`).
6. Sur le téléphone : ouvrez l'APK et autorisez l'installation depuis des sources inconnues.

Si le dossier `.github` n'a pas été envoyé (dossier caché), allez dans **Actions** →
« set up a workflow yourself », nommez le fichier `build.yml` et collez le contenu de
`.github/workflows/build.yml`.

## Utilisation

- Téléphone et TV sur le même Wi‑Fi, TV allumée.
- Ouvrez l'app : votre TV apparaît. Touchez-la, tapez le code affiché sur la TV.
- Les fois suivantes, l'app se reconnecte toute seule à la dernière TV.
