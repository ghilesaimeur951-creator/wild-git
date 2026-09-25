# Sport Chrono

Application Android native, en français, avec :

- chronomètre croissant avec tours, pause et reprise ;
- minuteur simple ;
- entraînement par intervalles configurable (préparation, effort, repos et nombre de tours) ;
- bip sur les cinq dernières secondes de chaque phase et signal final ;
- alarme quotidienne, arrêt et report de cinq minutes ;
- réglages du son, du volume et de la vibration ;
- notification et service au premier plan pendant une séance ;
- autorisation Internet déclarée pour les fonctionnalités futures (aucune transmission aujourd’hui).

Les valeurs initiales d’une séance sont **4 tours, 5 s de préparation, 30 s d’effort et 10 s de repos**. Chaque tour inclut son repos, y compris le dernier. Le chronomètre et les intervalles restent actifs lorsque l’écran est verrouillé tant qu’Android laisse tourner le service au premier plan. Les temps reposent sur une horloge monotone : un affichage retardé ne prolonge pas les phases.

## Installer l’APK

Ouvrir **Actions → APK Android → dernier lancement réussi → Artifacts → SportChrono-APK-debug** sur GitHub. Décompresser le fichier téléchargé, puis installer `app-debug.apk` sur le téléphone Android (Android 8 ou plus). Autoriser l’installation depuis la source utilisée si le téléphone le demande. C’est un APK de test signé automatiquement par GitHub Actions, adapté aux essais, pas une version de distribution publique signée avec une clé pérenne.

## Autorisations

| Autorisation | Utilisation |
| --- | --- |
| Notifications (Android 13+) | Affichage de la séance et de l’alarme ; demandée à l’ouverture. |
| Alarmes exactes (Android 12+) | Alarme à l’heure précise ; le bouton de l’onglet Alarme ouvre le réglage système. Sans accord, une alarme moins précise reste programmée. |
| Service au premier plan, verrouillage de veille | Séance et sonnerie audibles écran éteint. |
| Vibration | Signaux facultatifs. |
| Redémarrage | Reprogrammation de l’alarme quotidienne. |
| Internet | Réservée à une future version ; aucune connexion dans cette version. |

Le son utilise le volume d’alarme Android. Le curseur de l’application règle les bips et, sur Android 9+, la sonnerie. Le volume système et le mode silencieux peuvent aussi influencer la sortie audio. Une notification refusée peut empêcher de voir les commandes de fond ; une alarme exacte non autorisée peut arriver en retard. Une alarme quotidienne désactivée annule aussi son rappel.

## Développement

Projet Java sans bibliothèque tierce, Android Gradle Plugin 8.9.2, SDK 35 et JDK 17. Ouvrir la racine du dépôt dans Android Studio, ou utiliser Gradle 8.11.1 :

```bash
gradle :app:assembleDebug :app:lintDebug
```

Le test autonome `EngineCheck.java` est exécuté avant la compilation dans GitHub Actions. L’application ne demande pas d’accès aux contacts, au microphone ou à la position.
