# 📺 Télécommande TCL

Transformez votre téléphone Android en télécommande pour votre TV TCL, par le Wi‑Fi.
Pratique quand la télécommande est perdue, cassée ou sans piles.

> Application gratuite, open source et non officielle. Elle n'est pas affiliée à TCL ni à Google.

---

## ✅ Compatibilité

- **TV** : TCL (ou autre marque) sous **Google TV** ou **Android TV**.
  Pour vérifier : sur la TV, *Paramètres → Système → À propos*.
  Les TV sous Roku TV ou un autre système ne sont pas compatibles.
- **Téléphone** : Android 9 ou plus récent. Pas de version iPhone.
- **Réseau** : le téléphone et la TV doivent être connectés **au même Wi‑Fi**.

---

## 📥 Installation

1. Ouvrez l'onglet **Actions** de ce dépôt (connexion à un compte GitHub gratuit nécessaire).
2. Cliquez sur la dernière compilation marquée d'une coche verte ✅.
3. En bas de la page, dans **Artifacts**, téléchargez **telecommande-tcl**.
4. Dézippez le fichier : vous obtenez `app-debug.apk`.
5. Envoyez-le sur votre téléphone et ouvrez-le.
   Android vous demandera d'autoriser l'installation depuis cette source : acceptez.

> Android peut afficher un avertissement car l'application ne vient pas du Play Store.
> C'est normal pour une application installée à la main.

---

## 🚀 Première utilisation

1. Allumez la TV et vérifiez qu'elle est sur le même Wi‑Fi que votre téléphone.
2. Ouvrez l'application **Télécommande TCL**.
   Elle cherche automatiquement les TV autour de vous : la vôtre apparaît avec son nom.
3. Touchez votre TV.
4. Un **code de 6 caractères** s'affiche sur l'écran de la TV.
5. Tapez ce code dans l'application, puis **Valider**.
6. C'est prêt : la télécommande s'affiche.

Cet appairage ne se fait **qu'une seule fois**.
Ensuite, l'application se reconnecte toute seule à votre TV à chaque ouverture.

---

## 🎮 Les boutons

| Bouton | Rôle |
|---|---|
| ⏻ (rouge) | Éteindre / mettre en veille |
| Source | Choisir l'entrée (HDMI, TV…) |
| HDMI 1 / 2 / 3 | Aller directement sur une entrée HDMI |
| Croix + OK | Se déplacer dans les menus et valider |
| Retour / Accueil | Revenir en arrière / écran d'accueil |
| TV | Revenir aux chaînes TV |
| VOL + / − et Muet | Régler le son |
| CH + / − | Changer de chaîne |
| Menu, Info, Guide | Options, informations, guide des programmes |
| Lecture/Pause | Mettre en pause ou relancer une vidéo |
| Netflix, YouTube, Prime Video… | Ouvrir directement l'application sur la TV |

Le bouton **Changer** en haut de l'écran permet de choisir une autre TV.

---

## 🛠️ En cas de problème

**Ma TV n'apparaît pas dans la liste**
- Vérifiez que la TV est allumée et sur le même Wi‑Fi que le téléphone.
- Les réseaux « invités » bloquent souvent la détection : utilisez le réseau principal.
- Sinon, entrez l'adresse IP de la TV en bas de l'écran.
  Elle se trouve sur la TV dans *Paramètres → Réseau et Internet → votre Wi‑Fi*.

**Le code est refusé**
- Vérifiez chaque caractère : le code ne contient que des chiffres et les lettres A à F.
- Relancez l'appairage en touchant à nouveau votre TV : un nouveau code s'affiche.

**La connexion se coupe ou ne marche plus**
- Touchez **Réessayer**.
- Si ça ne suffit pas, touchez **Réappairer** et entrez le nouveau code.

**La TV ne se rallume pas depuis l'application**
Quand la TV est complètement éteinte, elle ne répond plus sur le réseau.
Sur la TV, cherchez une option du type *Allumage par le réseau* ou *Veille réseau* et activez-la.

**Netflix ou une autre appli ne s'ouvre pas**
L'application doit être installée sur la TV.

---

## 🔒 Confidentialité

- L'application communique **uniquement avec votre TV**, sur votre réseau local.
- Aucune donnée n'est envoyée sur Internet, aucun compte n'est nécessaire, aucune publicité.
- La connexion avec la TV est chiffrée. La TV n'accepte que les téléphones que vous avez appairés avec le code.

---

## 👩‍💻 Pour les développeurs

Application Kotlin / Jetpack Compose. Elle utilise le protocole **Android TV Remote v2**
(le même que l'application Google TV), décrit par la communauté et non documenté officiellement.

- Détection des TV : mDNS, service `_androidtvremote2._tcp`
- Appairage : TLS sur le port 6467, avec un certificat client généré sur le téléphone
- Commandes : TLS sur le port 6466 (touches Android `KeyEvent` et ouverture d'applis par lien)

**Compiler** : chaque envoi sur le dépôt déclenche la compilation via GitHub Actions
(`.github/workflows/build.yml`). En local : `gradle assembleDebug` avec JDK 17 et le SDK Android.

**Ajouter une appli** : dans `MainActivity.kt`, utilisez un lien
`market://launch?id=<nom-du-paquet>`.

> La clé `app/debug.keystore` est publique et ne sert qu'aux versions de test.
> Pour distribuer l'application plus largement, signez-la avec votre propre clé privée.

---

## 📄 Licence et marques

Projet personnel fourni tel quel, sans garantie.
TCL, Google TV, Android TV, Netflix, YouTube, Prime Video, Disney+, Apple TV et Spotify
sont des marques de leurs propriétaires respectifs.
