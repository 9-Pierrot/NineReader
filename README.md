# NineReader

Lecteur et éditeur de PDF pour Android, pour smartphones **et** tablettes, qui fonctionne entièrement hors ligne.
Le cahier des charges est dans [`NineReader_Cahier_des_charges.md`](NineReader_Cahier_des_charges.md).

- Java 17, layouts XML (Views), ViewBinding, architecture MVVM
- minSdk 26 (Android 8.0), targetSdk 36
- Material Design 3 (DayNight), français par défaut, anglais
- Aucune permission réseau : les fichiers passent par le Storage Access Framework

## Fonctionnalités

| Domaine | Exigences | Contenu |
|---|---|---|
| Lecture | LEC-01 à LEC-16 | « Ouvrir avec », défilement continu, page par page, double page (par défaut sur tablette en paysage), zoom (pincement, double tap), reprise de lecture, sommaire, recherche plein texte, miniatures, thèmes (clair, sombre, sépia, inversé), signets, sélection et copie, liens, PDF protégés, mode liquide, lecture à voix haute (écran éteint) |
| Confort | RAP-01 à RAP-11 | Recadrage auto des marges, mode une main et touches de volume, luminosité et filtre lumière bleue, lecture rapide RSVP, zoom sur colonne, défilement automatique, statistiques, mode présentation, widget, définir / traduire via les applications installées |
| Bibliothèque | BIB-01 à BIB-05 | Récents, favoris, explorateur SAF (tri, recherche), collections, renommer / déplacer / supprimer avec corbeille de 30 jours, sauvegarde JSON |
| Annotation | ANN-01 à ANN-08 | Surligner, souligner, barrer, notes, dessin libre (pression du stylet, rejet de la paume), formes, gomme, annuler / rétablir, liste et export texte / Markdown, enregistrement dans le PDF (annotations standard) ou dans une copie, partage d'une page en image |
| Remplir et signer | SIG-01 à SIG-04 | Formulaires AcroForm, texte libre, signatures manuscrites, initiales, date, coche |
| Opérations | ORG, MOD, CRE, CNV, CMP, SEC | Organiser les pages (glisser-déposer), fusionner, extraire, recadrer, ajouter texte et images, masquer, caviarder, créer depuis des images ou un PDF vierge, OCR hors ligne, export JPEG / PNG / texte, compression à 3 niveaux avec estimation, chiffrement AES-256, retrait du mot de passe, comparaison côte à côte |

Sur tablette (largeur ≥ 600 dp) : barre de navigation latérale, Fichiers en deux volets (liste + aperçu), panneau du lecteur ancré en paysage, grilles de 3 à 6 colonnes.

## Architecture

```
com.ninereader
├── ui/           écrans : home, files, create, tools, reader, settings, signatures, compare, widget
├── reader/       moteur Pdfium, ordonnanceur de rendu, cache, vues (zoom, pages), recherche, reflow, TTS, RSVP
├── annotations/  modèle, pile annuler / rétablir, rendu, écriture PdfBox, formulaires, export
├── pdfops/       organiser, fusionner, recadrer, compresser, chiffrer, exporter, créer, OCR, caviarder, worker
├── data/         Room (documents, signets, collections, signatures, sessions, corbeille, brouillons), sauvegarde JSON
└── util/
```

| Besoin | Bibliothèque | Licence |
|---|---|---|
| Rendu, texte, liens, sommaire | PdfiumAndroid (`io.legere`) | Apache 2.0 |
| Écriture, annotations, formulaires, fusion, chiffrement | PdfBox-Android | Apache 2.0 |
| OCR hors ligne | ML Kit Text Recognition (modèle embarqué) | Conditions ML Kit |
| Base locale / tâches longues | Room, WorkManager | Apache 2.0 |

Il n'y a aucune dépendance GPL / AGPL (MuPDF est écarté, voir §6.2).

**Rendu (§5.1)** : rendu hors du thread UI, avec une file prioritaire où les rendus annulés sont abandonnés. Chaque page s'affiche d'abord en basse résolution, puis en résolution écran. Au zoom, seule une tuile haute définition de la zone visible est rendue. Les pages N-1 et N+1 sont pré-rendues, et le cache `LruCache` est borné à 1/6 du tas. L'initialisation native de Pdfium est lancée au démarrage de l'application.

**Fiabilité (§5.4)** : aucun écrasement silencieux. Le nouveau fichier est d'abord écrit en entier dans un fichier temporaire. Ensuite, l'utilisateur choisit de remplacer le fichier ou d'enregistrer une copie. Les annotations en cours sont sauvegardées en continu dans Room.

## Compiler et lancer

Prérequis : Android Studio (JDK 21 fourni), SDK Android 36.

```bash
./gradlew :app:assembleDebug          # APK : app/build/outputs/apk/debug/
./gradlew :app:installDebug           # installe sur l'appareil ou l'émulateur connecté
```

## Qualité

```bash
./gradlew :app:checkstyle             # règles : config/checkstyle/checkstyle.xml
./gradlew :app:lintDebug              # Lint, bloquant en cas d'erreur
./gradlew :app:testDebugUnitTest      # tests JUnit + Robolectric
./gradlew :app:jacocoUnitTestReport   # couverture : app/build/reports/jacoco/
```

La CI GitHub Actions (`.github/workflows/android.yml`) lance Checkstyle, Lint, les tests et la couverture à chaque push ou Pull Request sur `main`, et publie l'APK de test en artefact.

## Conventions

- Branches `feature/<ID>-description`, Pull Request vers `main`
- Commits au format *Conventional Commits* avec l'identifiant d'exigence : `feat(ANN-01): surlignage`
- Code et commentaires en anglais ; textes de l'interface dans `strings.xml` (FR + EN)

## Limites connues (V1)

- **Caviardage (MOD-03)** : la page concernée est convertie en image pour supprimer réellement le contenu. Sur une page pivotée, les autres annotations ajoutées sur cette même page peuvent être décalées.
- **Modification du texte d'origine (MOD-02)** : V1 limitée à l'ajout de texte et au masquage, comme le prévoit le §11.
- **Définition et traduction (RAP-10, RAP-11)** : déléguées aux applications installées (`ACTION_PROCESS_TEXT`), pour rester sans accès réseau.
- **Préférences** : stockées dans `SharedPreferences`, l'alternative prévue au §6.2 (pas DataStore).
- **Opérations Pdfium dans les tests** : OCR, recadrage, export en images et caviardage reposent sur Pdfium natif et ne sont pas couverts par les tests JVM. Il faut des tests instrumentés.
