# Sport Chrono

Application Android native, en français, avec :

- chronomètre croissant avec tours, pause et reprise ;
- minuteur simple ;
- entraînement par intervalles configurable (préparation, effort, repos et nombre de tours) ;
- bip sur les cinq dernières secondes de chaque phase et signal final ;
- alarme quotidienne, arrêt et report de cinq minutes ;
- réglages du son, du volume et de la vibration ;
- notification et service au premier plan pendant une séance ;
- programmes d’intervalles enregistrés localement (jusqu’à 20) ;
- reprise automatique après une fermeture du processus, tant que le téléphone n’a pas redémarré ;
- historique local des 50 dernières séances et des repères du chronomètre ;
- annonces vocales françaises, avec baisse temporaire de la musique pendant les signaux ;
- plusieurs alarmes avec jours de la semaine et sonnerie au choix ;
- widget d’accueil pour démarrer le chronomètre ou la séance 4 × 30/10 ;
- éditeur de séances street workout avec exercices ordonnés, réglages par mouvement et modèles enregistrables ;
- catalogue d’exercices modifiable, recherche, favoris, exercices personnels et charges ajoutées ou assistance distinctes ;
- modes séries classiques, EMOM, pyramide, circuit, supersérie et AMRAP ;
- validation anticipée des séries, repos automatiques, suivi des répétitions et récapitulatif détaillé ;
- autorisation Internet déclarée pour les fonctionnalités futures (aucune transmission aujourd’hui).

Les valeurs initiales d’une séance sont **4 tours, 5 s de préparation, 30 s d’effort et 10 s de repos**. Chaque tour inclut son repos, y compris le dernier. Le chronomètre et les intervalles restent actifs lorsque l’écran est verrouillé tant qu’Android laisse tourner le service au premier plan. Les temps reposent sur une horloge monotone : un affichage retardé ne prolonge pas les phases. La reprise est prévue après une fermeture du processus pendant le même démarrage du téléphone ; elle n’est pas garantie après un arrêt forcé de l’application ou un redémarrage du téléphone.

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

Le son utilise le volume d’alarme Android. Le curseur de l’application règle les bips et, sur Android 9+, la sonnerie. La voix française nécessite un moteur de synthèse vocale installé et peut être désactivée. Le volume système et le mode silencieux peuvent aussi influencer la sortie audio. Une notification refusée peut empêcher de voir les commandes de fond ; une alarme exacte non autorisée peut arriver en retard. Désactiver ou supprimer une alarme annule aussi son rappel.

## Séances street workout

Dans **Créer séance**, nommez la séance, choisissez le mode et les paramètres par défaut, puis ajoutez des exercices depuis le catalogue. Les flèches changent l’ordre. L’écran **Configurer** d’un exercice permet d’activer séparément ses propres séries, répétitions, durée maximale, repos, charge ou assistance et note. La durée `0` signifie une série sans limite : elle attend la validation manuelle.

Pendant l’effort, **Série terminée** enregistre les répétitions réellement effectuées et lance immédiatement le repos. À l’expiration de la durée, la série est enregistrée comme **non validée** puis le repos commence. Le repos entre séries n’est appliqué qu’entre deux séries du même exercice ; après la dernière série, seul le repos entre exercices s’applique. Les temps de repos se terminent automatiquement. **Passer** et **Série précédente** demandent confirmation lorsqu’une progression est effacée.

- **EMOM** : la liste des exercices alterne sur le nombre de cycles choisi ; valider tôt laisse le repos jusqu’à la prochaine échéance fixe. Une tâche non validée est enregistrée à expiration.
- **Pyramide** : le premier exercice suit des marches ascendantes, descendantes ou aller-retour ; le sommet peut être répété. Les autres exercices, s’il y en a, utilisent les séries classiques.
- **Circuit** : tous les exercices s’enchaînent sans repos interne, puis le repos du tour commence. **Supersérie** : même fonctionnement par groupes de deux exercices.
- **AMRAP** : les exercices recommencent en boucle jusqu’à la durée globale ; l’historique indique les tours entièrement terminés.

Les modèles sont modifiables, duplicables ou supprimables. L’historique enregistre, sur l’appareil, le statut de chaque série, le temps, les répétitions et les kilogrammes ajoutés ou d’assistance. Le service au premier plan et sa notification permettent de continuer écran verrouillé ; la reprise après fermeture du processus est prévue tant que le téléphone n’a pas redémarré.

## Développement

Projet Java sans bibliothèque tierce, Android Gradle Plugin 8.9.2, SDK 35 et JDK 17. Ouvrir la racine du dépôt dans Android Studio, ou utiliser Gradle 8.11.1 :

```bash
gradle :app:assembleDebug :app:lintDebug
```

Les tests autonomes `EngineCheck.java` et `WorkoutEngineCheck.java` sont exécutés avant la compilation dans GitHub Actions. Le second vérifie notamment quatre exercices, quatre séries de tractions chronométrées, une validation anticipée et la transition vers l’exercice suivant. Les programmes, modèles, catalogues, alarmes et historiques sont enregistrés uniquement sur l’appareil. Le widget utilise un programme fixe de 4 tours (5 s de préparation, 30 s d’effort, 10 s de repos). L’application ne demande pas d’accès aux contacts, au microphone ou à la position.
