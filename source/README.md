# JARVIS Android — prototype 0.1.0

Premier prototype natif Android en Java, sans dépendance de bibliothèque externe. Il contient une interface de discussion, un historique local, une mémoire explicite via `/remember ...`, des instructions de personnalité configurables, un endpoint compatible avec l'API Chat Completions et un export des données.

## État honnête

- Source du projet créée : oui.
- APK compilée : non, pas dans cet environnement.
- Installée ou testée sur un Samsung S23 : non.
- Connexion à un fournisseur réel : à configurer et tester.

## Sécurité importante

La clé API saisie est conservée dans les préférences privées de l'application, mais elle n'est pas stockée dans un coffre matériel dédié. Une application Android cliente ne peut pas cacher parfaitement un secret. N'utilise pas une clé donnant accès à des dépenses importantes. Pour une version distribuée, il faudra un backend qui conserve la clé côté serveur ou un mécanisme d'authentification sûr.

## Compiler depuis Android

Ce projet utilise Gradle et Android Gradle Plugin 8.7.3, compileSdk 35 et minSdk 26. Il faut un environnement Android compatible et le SDK Android correspondant. Les IDE Android sur téléphone peuvent avoir des limites de compatibilité; vérifie la documentation et la provenance avant d'installer un outil de développement. AndroidIDE officiel est archivé et n'est plus maintenu.

1. Décompresse `JARVIS-Android-Prototype.zip` dans un environnement Android de développement.
2. Ouvre le dossier `JARVIS` comme projet Gradle.
3. Autorise le téléchargement des dépendances Gradle et du SDK si demandé.
4. Lance la tâche `assembleDebug`.
5. Si la compilation réussit, l'APK de débogage sera normalement dans `app/build/outputs/apk/debug/app-debug.apk`.

Aucun `gradlew` ni wrapper JAR n'est inclus dans cette première archive. Il faudra utiliser le Gradle fourni par l'environnement de compilation ou générer un wrapper compatible.

## Configurer l'IA

Dans Réglages, renseigne l'URL complète du endpoint Chat Completions, le nom du modèle et éventuellement la clé. Les API ne sont pas toutes compatibles, même si elles ressemblent à l'API OpenAI. N'utilise que le endpoint officiel d'un fournisseur choisi et vérifie ses tarifs/limites avant de fournir une clé.

## Structure

- `app/src/main/java/com/jarvis/assistant/MainActivity.java` : interface, conversation, mémoire, réglages et appel HTTP.
- `app/src/main/AndroidManifest.xml` : déclaration Android et permission Internet.
- `app/src/main/res/values/styles.xml` : thème.
- `app/build.gradle` : configuration de l'application.

## Compilation à distance depuis le téléphone (GitHub Actions)

L'archive inclut `.github/workflows/build-apk.yml`. Une fois le dossier importé dans un dépôt GitHub, le workflow peut compiler l'APK sur une machine distante. Les workflows Actions standards sur un dépôt public sont généralement sans frais selon la documentation GitHub; vérifie les conditions du compte et du dépôt avant usage.

1. Crée un dépôt GitHub public vide depuis Chrome (ne mets jamais de clé API dans le dépôt).
2. Téléverse les fichiers du projet dans le dépôt, en conservant le dossier caché `.github` et son workflow.
3. Ouvre l'onglet **Actions** du dépôt et autorise les workflows si GitHub le demande.
4. Lance **Build JARVIS APK** via **Run workflow**, ou pousse un changement sur la branche `main`.
5. Quand le workflow est terminé avec succès, ouvre son exécution, puis télécharge l'artefact `JARVIS-debug-APK`.
6. Décompresse l'artefact sur le téléphone et ouvre l'APK. Android peut demander d'autoriser l'installation depuis cette source; n'autorise que les APK que tu as compilées et dont tu comprends la provenance.

Ce workflow n'a pas été exécuté dans cet environnement. Il faudra vérifier son résultat réel sur GitHub avant de considérer l'APK comme compilée.


## Auto-modification expérimentale (v0.2.0)

Depuis le bouton **⌘ Code**, l'utilisateur peut sélectionner explicitement la racine du projet avec le sélecteur de dossier Android, parcourir des fichiers source pris en charge, demander au modèle configuré de proposer une modification d'un fichier, examiner le contenu généré puis confirmer l'écriture. Avant l'écriture, la version précédente est enregistrée dans les préférences privées de l'application. Les dossiers `.git`, `build`, `.gradle`, `.idea` et `node_modules` sont ignorés. La recherche est limitée à 250 fichiers, 12 niveaux de profondeur, 300 Ko par fichier à lire et 80 000 caractères pour une demande IA.

**Limites et précautions :** cette version ne compile pas elle-même le projet et ne remplace pas automatiquement l'APK installé. Le contenu du fichier sélectionné est envoyé au fournisseur IA défini dans Réglages pour générer la modification. Ne sélectionne pas de fichiers contenant des secrets. Les changements doivent être vérifiés et testés dans un environnement de build avant installation. L'écriture dépend des autorisations accordées et du fournisseur de documents Android.


## Boucle de diagnostic et correction (v0.3.0)

Le workflow GitHub Actions a été renforcé pour créer un environnement de compilation reproductible : Java 17, Android SDK 35 et Gradle 8.9 (compatible avec Android Gradle Plugin 8.7.3). Il capture maintenant la sortie Gradle avec `--stacktrace` dans `build.log`.

Après avoir importé le projet dans un dépôt GitHub :

1. Lance **Actions → Build JARVIS APK → Run workflow**.
2. Télécharge `JARVIS-debug-APK` si le build réussit.
3. Télécharge `JARVIS-build-diagnostics` dans tous les cas, y compris si le build échoue.
4. En cas d'échec, ouvre `build.log`, copie la première erreur utile ainsi que le contexte, puis demande à JARVIS de corriger le fichier concerné via **⌘ Code**.
5. Examine le diff et les modifications, enregistre seulement après vérification, puis relance le workflow.

**Limite actuelle :** cette boucle est assistée, pas encore autonome : JARVIS ne déclenche pas directement GitHub Actions, ne lit pas encore automatiquement les artefacts de build et ne corrige pas sans validation. Le dépôt ne reçoit aucune clé API. Cette version et son workflow n'ont pas été exécutés dans cet environnement; la réussite réelle doit être confirmée dans l'onglet Actions.

**Sécurité :** utilise un dépôt privé si le code ne doit pas être public. Ne committe jamais de clé API, token GitHub, mot de passe ou fichier de configuration secret. Toute APK doit être vérifiée avant installation.
