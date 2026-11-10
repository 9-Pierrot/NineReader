# Cahier des charges — NineReader

**Application Android de lecture et de modification de PDF pour smartphones et tablettes**

| Élément | Valeur |
|---|---|
| Projet | NineReader |
| Rédacteur | Pierre ATADEGNON |
| Version du document | 1.1 |
| Date | 10/11/2026 |
| Statut | Validé pour diffusion à l'équipe projet |
| Plateformes | Android : smartphones **et** tablettes |
| Langage / UI | Java 17, layouts XML (Views), Android Studio |

### Historique des versions

| Version | Date | Modifications |
|---|---|---|
| 0.1 | 08/11/2026 | Brouillon initial |
| 1.0 | 08/11/2026 | Tablettes intégrées au périmètre ; outils IA retirés ; numérisation par caméra reportée à une version ultérieure ; ajout de l'organisation projet, des critères d'acceptation et de la Definition of Done |
| 1.1 | 10/11/2026 | MOD-02 étendue à la modification du texte d'origine (mode « Modifier » : détection des blocs, OCR des pages numérisées, édition en temps réel, suppression réelle des glyphes) ; polices Liberation (SIL OFL) ajoutées au §6.2 ; risque correspondant mis à jour au §11 |

---

## Sommaire

1. Contexte et objectifs
2. Périmètre
3. Navigation et structure de l'application
4. Exigences fonctionnelles
5. Exigences non fonctionnelles
6. Architecture technique
7. Interface utilisateur
8. Organisation du projet
9. Planning prévisionnel
10. Tests et recette
11. Risques
12. Évolutions prévues (versions ultérieures)
13. Glossaire

---

## 1. Contexte et objectifs

### 1.1 Contexte
Les lecteurs PDF du marché, comme Adobe Acrobat Reader, offrent beaucoup de fonctions. Elles sont souvent payantes, réparties dans des menus chargés et lentes à l'ouverture. NineReader vise d'abord la **lecture rapide et confortable**. Les outils d'annotation, d'organisation et de conversion viennent ensuite, sans gêner la lecture.

### 1.2 Objectifs
1. **Ouvrir vite** : un PDF de 100 pages doit s'afficher en moins d'une seconde sur un appareil milieu de gamme.
2. **Lire confortablement** sur smartphone comme sur tablette : modes de lecture, thèmes, reflow du texte, reprise automatique.
3. **Annoter et modifier simplement** : surligner, commenter, signer, réorganiser des pages.
4. **Fonctionner entièrement hors ligne** : aucune fonction de la V1 ne dépend du réseau.

### 1.3 Utilisateurs cibles
- **Étudiants** : cours, polycopiés, révisions (souvent sur tablette).
- **Professionnels** : fiches techniques, contrats, formulaires à signer.
- **Lecteurs** : e-books et articles en PDF.

---

## 2. Périmètre

### 2.1 Dans le périmètre (V1)
- Lecture, navigation et recherche dans les PDF.
- Annotation, remplissage de formulaires, signature.
- Opérations sur le document : organiser, fusionner, scinder, compresser, protéger, recadrer, exporter.
- Création de PDF à partir d'images existantes et de PDF vierges.
- OCR des PDF scannés déjà présents sur l'appareil.
- Interfaces adaptées aux **smartphones et aux tablettes** (portrait et paysage).

### 2.2 Hors périmètre (V1)
| Élément | Statut |
|---|---|
| Outils d'intelligence artificielle (assistant, résumé, fiches, quiz, cartes mentales, podcast, espaces d'étude) | Retiré du projet |
| Numérisation de documents par la caméra | Reportée à une version ultérieure (§12) |
| Version iOS / web | Hors projet |
| Édition collaborative en temps réel | Hors projet |
| Conversion Office (DOCX, PPTX…) → PDF | Hors projet en V1 |
| Signature numérique par certificat | Version ultérieure (§12) |

---

## 3. Navigation et structure de l'application

### 3.1 Smartphone
Barre de navigation inférieure à 4 onglets (`BottomNavigationView`) :

| Onglet | Contenu |
|---|---|
| **Accueil** | Fichiers récents, « Reprendre la lecture », favoris, statistiques rapides |
| **Fichiers** | Explorateur (stockage via SAF, collections, tags, corbeille) |
| **Créer** | Images → PDF, PDF vierge, combiner des fichiers |
| **Outils** | Grille de tous les outils, filtrable par catégorie |

Catégories de l'onglet **Outils** (`ChipGroup` ou `TabLayout`) : **Tous · Lire · Créer · Modifier · Convertir · Organiser · Protéger**.

### 3.2 Tablette
- Barre de navigation **latérale** (`NavigationRailView`) à la place de la barre inférieure, à partir d'une largeur de 600 dp.
- Écrans de liste en **deux volets** (liste + détail ou aperçu), par exemple Fichiers + aperçu du document sélectionné.
- Dans le lecteur, panneau latéral (sommaire, signets, annotations, miniatures) **ancré** en paysage, et non superposé.
- Ressources dédiées : `layout-sw600dp`, `layout-sw600dp-land`, `values-sw600dp` (dimensions, nombre de colonnes des grilles).

---

## 4. Exigences fonctionnelles

Priorités (méthode MoSCoW) : **M** = indispensable, **S** = souhaitable, **C** = optionnel.
Lots : **L1** = MVP lecture, **L2** = annotation et organisation, **L3** = création, conversion et protection (voir §9).

Chaque exigence a un identifiant unique, à reprendre dans les tickets, les branches et les commits (ex. `feat(LEC-07): recherche plein texte`).

### 4.1 Lecture (cœur de l'application)

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| LEC-01 | Ouverture de PDF | Depuis l'explorateur, les récents ou « Ouvrir avec » (intent `ACTION_VIEW`, `application/pdf`) | M | L1 |
| LEC-02 | Défilement continu | Défilement vertical fluide, rendu progressif des pages visibles | M | L1 |
| LEC-03 | Mode page par page | Pagination horizontale (type livre) | M | L1 |
| LEC-04 | Zoom | Pincement, double tap pour zoomer sur une zone | M | L1 |
| LEC-05 | Reprise de lecture | Retour automatique à la dernière page et au dernier zoom | M | L1 |
| LEC-06 | Sommaire | Affichage de l'outline (signets internes du PDF) | M | L1 |
| LEC-07 | Recherche plein texte | Recherche avec surbrillance, navigation entre les résultats | M | L1 |
| LEC-08 | Miniatures | Grille de miniatures pour aller vite à une page | M | L1 |
| LEC-09 | Thèmes de lecture | Clair, sombre, sépia, couleurs inversées (filtre de rendu) | M | L1 |
| LEC-10 | Signets utilisateur | Marquer une page, liste des signets | M | L1 |
| LEC-11 | Sélection et copie de texte | Sélection de texte, copier, partager | S | L1 |
| LEC-12 | Reflow (mode Liquide) | Réagencement du texte à la largeur de l'écran (taille de police et interligne réglables) | S | L2 |
| LEC-13 | Lecture à haute voix | Synthèse vocale native (`TextToSpeech`, sans IA générative), surlignage de la phrase en cours, vitesse réglable, lecture écran éteint (service au premier plan) | S | L2 |
| LEC-14 | Mode double page | Deux pages côte à côte ; **activé par défaut sur tablette en paysage** | M | L1 |
| LEC-15 | Liens internes et externes | Clic sur les liens du document | S | L1 |
| LEC-16 | Ouverture de PDF protégé | Saisie du mot de passe à l'ouverture | M | L1 |

### 4.2 Confort et lecture rapide

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| RAP-01 | Recadrage intelligent des marges | Suppression automatique des marges blanches à l'affichage | M | L1 |
| RAP-02 | Mode une main | Zones de tap (bord gauche / droit = page précédente / suivante), touches de volume pour tourner les pages | S | L1 |
| RAP-03 | Filtre lumière bleue / luminosité | Réglage dans le lecteur, indépendant du système | C | L1 |
| RAP-04 | Lecture rapide RSVP | Affichage des mots un par un au centre (200 à 600 mots/min), avec point de fixation | S | L2 |
| RAP-05 | Zoom sur colonne | Double tap sur une colonne : zoom ajusté à sa largeur, puis défilement colonne par colonne | S | L2 |
| RAP-06 | Défilement automatique | Vitesse réglable, pause au toucher | C | L2 |
| RAP-07 | Statistiques de lecture | Temps passé, pages lues, estimation du temps restant | C | L2 |
| RAP-08 | Mode présentation | Plein écran, page par page, pour projeter ou montrer | C | L2 |
| RAP-09 | Widget écran d'accueil | « Reprendre la lecture » en un tap | C | L2 |
| RAP-10 | Traduction hors ligne | Traduire une sélection (ML Kit Translation, modèles téléchargés une fois) | C | L3 |
| RAP-11 | Dictionnaire | Appui long sur un mot : définition | C | L3 |

### 4.3 Bibliothèque

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| BIB-01 | Récents et favoris | Liste triée par date d'ouverture, favoris épinglés | M | L1 |
| BIB-02 | Explorateur | Navigation dans les dossiers autorisés via SAF, tri (nom, date, taille), recherche par nom | M | L1 |
| BIB-03 | Collections et tags | Regrouper des documents (Cours, Datasheets, Contrats…) | S | L2 |
| BIB-04 | Renommer, déplacer, supprimer | Avec corbeille interne (restauration pendant 30 jours) | S | L2 |
| BIB-05 | Sauvegarde des données | Export/import des signets, collections et statistiques (JSON) | C | L3 |

### 4.4 Annotation

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| ANN-01 | Surlignage, soulignement, barré | Sur sélection de texte, couleurs personnalisables | M | L2 |
| ANN-02 | Notes / commentaires | Annotation « Text » (post-it) placée sur la page | M | L2 |
| ANN-03 | Dessin libre | Annotation « Ink », épaisseur et couleur réglables, gomme ; **prise en charge du stylet sur tablette** (pression, rejet de la paume) | S | L2 |
| ANN-04 | Formes | Rectangle, cercle, flèche, ligne | C | L2 |
| ANN-05 | Annuler / rétablir | Pile d'actions | M | L2 |
| ANN-06 | Liste des annotations | Panneau listant toutes les annotations, avec export en texte ou Markdown | S | L2 |
| ANN-07 | Enregistrement | Sauvegarde dans le PDF (annotations standard, lisibles par les autres lecteurs) ou dans une copie | M | L2 |
| ANN-08 | Partage d'un extrait | Partager une page ou une zone en image | S | L2 |

### 4.5 Remplir et signer

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| SIG-01 | Remplissage de formulaires | Champs AcroForm : texte, cases, listes | M | L2 |
| SIG-02 | Texte libre | Ajouter du texte n'importe où (formulaires non interactifs) | M | L2 |
| SIG-03 | Signature manuscrite | Dessin au doigt ou au stylet, enregistrement de plusieurs signatures, placement et redimensionnement | M | L2 |
| SIG-04 | Initiales, date, coche | Tampons rapides | S | L2 |

### 4.6 Organiser, modifier, convertir, protéger

| ID | Fonctionnalité | Description | Prio | Lot |
|---|---|---|---|---|
| ORG-01 | Organiser les pages | Pivoter, déplacer (glisser-déposer), insérer, supprimer, dupliquer | M | L2 |
| ORG-02 | Combiner des fichiers | Fusionner plusieurs PDF et images dans un seul PDF | M | L2 |
| ORG-03 | Scinder / extraire | Extraire une plage de pages dans un nouveau PDF | S | L2 |
| ORG-04 | Recadrer les pages | Rogner (CropBox) ou redimensionner, recadrage automatique des marges | S | L3 |
| MOD-01 | Modifier un PDF : images et pages | Ajouter ou remplacer des images, ajouter des pages | S | L3 |
| MOD-02 | Modifier un PDF : texte existant | Mode « Modifier » : les blocs de texte de la page sont détectés (police, taille, gras/italique, couleur, interligne), ou reconnus par OCR hors ligne si la page est numérisée. Le texte est corrigé directement sur la page, en temps réel ; les blocs peuvent être déplacés, supprimés, et de nouveaux blocs ajoutés. À l'enregistrement, les glyphes d'origine sont réellement retirés du flux de contenu (le reste de la ligne ne bouge pas) et le nouveau texte est écrit avec une police embarquée. Sur une page numérisée, l'ancien texte est recouvert | S | L3 |
| MOD-03 | Masquage (caviardage) | Suppression réelle du contenu d'une zone, et pas seulement un rectangle noir par-dessus | C | L3 |
| CRE-01 | Créer un PDF depuis des images | JPG/PNG de la galerie ou des fichiers → PDF | M | L3 |
| CRE-02 | Créer un PDF vierge | Formats A4, Letter, A5 ; page blanche, lignée ou quadrillée | S | L3 |
| CRE-03 | OCR | Rendre cherchable un PDF scanné déjà présent sur l'appareil (couche de texte invisible), hors ligne | S | L3 |
| CNV-01 | Exporter en JPEG/PNG | Rendu de pages en images, résolution au choix | M | L3 |
| CNV-02 | Exporter en texte | Extraction du texte brut (.txt) | S | L3 |
| CMP-01 | Compresser | Réencodage et sous-échantillonnage des images, suppression des objets inutiles ; 3 niveaux de compression, taille estimée affichée avant validation | S | L3 |
| SEC-01 | Définir un mot de passe | Chiffrement AES-256, droits (impression, copie) | S | L3 |
| SEC-02 | Retirer un mot de passe | Si l'utilisateur connaît le mot de passe | S | L3 |
| CMP-02 | Comparer deux PDF | Affichage côte à côte (écran partagé, adapté à la tablette) | C | L3 |

---

## 5. Exigences non fonctionnelles

### 5.1 Performances
| Critère | Cible |
|---|---|
| Démarrage à froid de l'application | < 1,5 s |
| Affichage de la 1ʳᵉ page (PDF de 100 pages, ~5 Mo) | < 1 s |
| Défilement | 60 fps, pas de page blanche visible en défilement normal |
| Recherche plein texte (100 pages) | Premiers résultats < 1 s (indexation en arrière-plan) |
| Mémoire | Pas de crash par manque de mémoire (OOM) sur un PDF de 1 000 pages ; cache de bitmaps borné (`LruCache`) |

Moyens techniques :
- Rendu par **tuiles** et en **basse résolution d'abord**, haute résolution ensuite (indispensable sur les écrans de tablette haute définition).
- Rendu hors du thread UI (`ExecutorService`), annulation des rendus des pages sorties de l'écran.
- Pré-rendu des pages N-1 et N+1.
- `RecyclerView` pour la liste des pages, avec recyclage des vues.

### 5.2 Compatibilité
- **minSdk 26** (Android 8.0), **targetSdk** = dernière version imposée par Google Play.
- **Smartphones** (largeur < 600 dp) et **tablettes** (largeur ≥ 600 dp), en portrait et en paysage.
- Changements de configuration (rotation, multi-fenêtre, écran partagé, appareils pliables) sans perte d'état : page, zoom et annotation en cours.
- Prise en charge du **stylet**, du **clavier physique** (raccourcis : flèches, Ctrl+F, Ctrl+Z) et de la souris sur tablette.
- Mode sombre système.
- Langues : français (par défaut) et anglais ; aucun texte en dur dans le code (`strings.xml`).

### 5.3 Ergonomie et accessibilité
- Material Design 3 (thème `Theme.Material3.DayNight`).
- Cibles tactiles ≥ 48 dp, contrastes WCAG AA, `contentDescription` sur toutes les icônes.
- Compatible TalkBack ; taille de police système respectée (hors rendu PDF).
- En lecture, l'interface s'efface : plein écran immersif, un tap au centre affiche ou masque les barres.

### 5.4 Fiabilité
- **Jamais d'écrasement silencieux** : sauvegarde incrémentale ou « Enregistrer une copie » ; écriture dans un fichier temporaire, puis remplacement atomique.
- Sauvegarde automatique des annotations en cours (Room) en cas de crash.
- Gestion des PDF corrompus ou partiels avec un message clair.
- Taux de sessions sans crash ≥ 99,5 % (mesuré via Android Vitals).

### 5.5 Sécurité et confidentialité
- Accès aux fichiers via **Storage Access Framework** (pas de permission `MANAGE_EXTERNAL_STORAGE`).
- **Aucune permission réseau requise en V1**. Exception à valider : téléchargement unique des modèles de traduction (RAP-10).
- Mots de passe des PDF jamais stockés.
- Aucune collecte de données personnelles ; politique de confidentialité publiée pour le Play Store.

### 5.6 Qualité du code
- Java 17, architecture MVVM, ViewBinding.
- Android Lint et Checkstyle sans erreur bloquante.
- Couverture de tests unitaires ≥ 60 % sur les couches `pdfops`, `data` et ViewModels.
- Code et commentaires en anglais ; documentation projet en français.

---

## 6. Architecture technique

### 6.1 Architecture applicative
- **MVVM** : `Activity`/`Fragment` (XML + ViewBinding) → `ViewModel` + `LiveData` → `Repository` → sources de données.
- **Navigation Component** (graphe XML) pour la navigation entre fragments.
- Une seule `Activity` principale ; adaptation smartphone/tablette par les ressources `sw600dp` et des fragments réutilisables.

```
com.ninereader
├── ui/            (home, files, reader, tools, create, settings)
├── reader/        (moteur de rendu, cache, gestes, thèmes, modes de lecture)
├── annotations/   (modèle, outils, sérialisation)
├── pdfops/        (fusion, organisation, compression, chiffrement, export, OCR)
├── data/          (Room : documents, signets, collections, stats, signatures ; DataStore : préférences)
└── util/
```

### 6.2 Bibliothèques

| Besoin | Choix recommandé | Alternatives / remarques |
|---|---|---|
| Rendu PDF | **PdfiumAndroid** (fork maintenu) : rapide, accès au texte et aux liens | `android.graphics.pdf.PdfRenderer` (natif mais limité) ; `androidx.pdf` (Jetpack, maturité à évaluer en L0) |
| Lecture/écriture, annotations, formulaires, fusion, chiffrement | **PdfBox-Android** (Apache 2.0) | — |
| Moteur tout-en-un | MuPDF | ⚠️ Licence **AGPL** : impose de publier le code source de l'application ; à écarter sauf licence commerciale |
| Création à partir d'images | `android.graphics.pdf.PdfDocument` | Natif |
| OCR | **ML Kit Text Recognition** (hors ligne) | Tesseract (tess-two) |
| Polices du texte modifié (MOD-02) | **Liberation Sans / Serif / Mono** 2.1.5 (SIL OFL 1.1), embarquées en sous-ensemble | Noto (plus lourd) |
| Traduction | ML Kit Translation (hors ligne) | — |
| Synthèse vocale | `android.speech.tts.TextToSpeech` | Natif |
| Base locale | Room | — |
| Préférences | DataStore | SharedPreferences |
| Tâches longues (compression, OCR, indexation) | WorkManager | — |
| Images / miniatures | Glide | — |

> **Règle** : toute nouvelle dépendance doit être validée par le lead technique, licence vérifiée (Apache, MIT ou BSD de préférence ; pas de GPL/AGPL).

### 6.3 Modèle de données (Room, simplifié)

| Entité | Champs principaux |
|---|---|
| `Document` | id, uri, nom, taille, nbPages, hash, dernièrePage, dernierZoom, modeLecture, dateOuverture, favori |
| `Bookmark` | id, documentId, page, libellé, date |
| `Collection` / `DocumentCollection` | regroupements et tags |
| `Signature` | id, cheminImage (stockage interne), date |
| `ReadingSession` | documentId, début, fin, pagesLues |
| `TrashItem` | documentId, cheminTemporaire, dateSuppression |

Les annotations sont stockées **dans le PDF** (format standard). Un brouillon est conservé dans Room jusqu'à la sauvegarde.

---

## 7. Interface utilisateur

### 7.1 Écrans à maquetter (format smartphone **et** tablette)
1. Accueil (récents, reprendre la lecture, favoris)
2. Explorateur de fichiers et collections (deux volets sur tablette)
3. **Lecteur** : barre supérieure masquable (titre, recherche, menu), barre d'outils (annotation, thème, mode de lecture), curseur de pages
4. Panneau sommaire / signets / annotations / miniatures (superposé sur smartphone, ancré sur tablette en paysage)
5. Grille des outils filtrable (cartes : icône, titre, description) : 2 colonnes sur smartphone, 3 à 4 sur tablette
6. Organisation des pages (grille avec glisser-déposer)
7. Création et gestion des signatures
8. Créer un PDF (images, vierge, combiner)
9. Paramètres

### 7.2 Livrable design
Maquettes Figma (ou équivalent) validées avant le début de chaque lot, avec les états : vide, chargement, erreur, et les variantes clair/sombre.

---

## 8. Organisation du projet

### 8.1 Rôles

| Rôle | Responsabilités | Personne |
|---|---|---|
| Chef de projet / Product Owner | Priorités, validation des lots, arbitrages de périmètre | Pierre ATADEGNON |
| Lead technique | Architecture, choix des bibliothèques, revue de code | *À désigner* |
| Développeurs Android | Implémentation, tests unitaires | *À désigner* |
| UI/UX | Maquettes smartphone et tablette | *À désigner* |
| Testeur / QA | Corpus de test, recette, tests sur appareils | *À désigner* |

### 8.2 Méthode
- Sprints de **2 semaines** ; revue de sprint avec démonstration sur appareil réel.
- Tickets (GitHub Issues ou Jira) liés aux identifiants d'exigences (§4).
- Toute modification du périmètre passe par une nouvelle version de ce document.

### 8.3 Outils et conventions
- **Git** sur GitHub/GitLab : branche `main` protégée, branches `feature/<ID>-description`, merge par Pull Request avec au moins une revue.
- Commits au format *Conventional Commits* avec l'ID d'exigence : `feat(ANN-01): surlignage`.
- **CI** (GitHub Actions) : build, Lint, tests unitaires à chaque Pull Request ; APK de test généré automatiquement.
- Versionnement sémantique de l'application : `0.x` pendant le développement, `1.0.0` à la publication.

### 8.4 Definition of Done (une exigence est « terminée » quand…)
- [ ] Le code est fusionné dans `main` après revue.
- [ ] Les tests unitaires associés passent dans la CI.
- [ ] La fonction est vérifiée sur smartphone **et** tablette, en portrait et en paysage.
- [ ] Les textes sont dans `strings.xml` (FR + EN).
- [ ] Aucune régression de performance par rapport aux cibles du §5.1.
- [ ] Les critères d'acceptation de l'exigence sont validés par le Product Owner.

---

## 9. Planning prévisionnel

| Lot | Durée indicative | Contenu | Critère de sortie |
|---|---|---|---|
| **L0** — Cadrage | 2 semaines | Prototype de rendu comparatif (PdfiumAndroid / PdfRenderer / androidx.pdf), maquettes L1, squelette du projet, CI | Bibliothèque de rendu choisie sur mesures ; maquettes L1 validées |
| **L1** — MVP Lecture | 6 semaines | LEC-01 à 11, LEC-14 à 16, RAP-01 à 03, BIB-01 et 02, mise en page tablette | v0.1 : lecture complète sur le corpus de test, cibles du §5.1 atteintes |
| **L2** — Annoter et organiser | 6 semaines | LEC-12 et 13, RAP-04 à 09, BIB-03 et 04, ANN-*, SIG-*, ORG-01 à 03 | v0.2 : PDF annotés relus correctement dans Adobe Reader et Chrome |
| **L3** — Créer, convertir, protéger | 5 semaines | RAP-10 et 11, BIB-05, ORG-04, MOD-*, CRE-*, CNV-*, CMP-*, SEC-* | v0.3 : toutes les opérations validées sur le corpus de test |
| **L4** — Finitions et publication | 3 semaines | Accessibilité, optimisation, bêta fermée (Play Console), corrections, fiche Play Store | v1.0.0 publiée |

Durée totale indicative : **environ 22 semaines**, à ajuster selon la taille de l'équipe.

---

## 10. Tests et recette

- **Tests unitaires** (JUnit) : ViewModels, repositories, opérations PDF (fusion, rotation, chiffrement, compression).
- **Tests instrumentés** (Espresso) : parcours ouvrir → lire → annoter → enregistrer.
- **Corpus de PDF de test** (versionné dans le dépôt ou un stockage partagé) : texte simple, scans, multi-colonnes, formulaires, chiffrés, très volumineux (1 000+ pages), corrompus, polices exotiques.
- **Tests de performance** : Android Profiler, Macrobenchmark (démarrage, défilement).
- **Interopérabilité** : les PDF modifiés doivent s'ouvrir correctement dans Adobe Reader, Chrome et l'aperçu macOS.
- **Parc d'appareils minimum** :
  - un smartphone bas de gamme (≤ 3 Go de RAM) ;
  - un smartphone milieu de gamme ;
  - une tablette 10–11 pouces, avec stylet si possible ;
  - un émulateur pliable / grand écran.
- **Bêta fermée** avant publication (L4), sur 10 à 20 testeurs minimum.

---

## 11. Risques

| Risque | Impact | Mitigation |
|---|---|---|
| Modification du texte existant trop complexe (polices embarquées, encodages) | Fort | Le texte modifié est réécrit avec une police métriquement compatible embarquée (Liberation ≈ Arial / Times / Courier) au lieu de la police d'origine, souvent incomplète ; seuls les opérateurs de texte du bloc modifié sont retirés ; texte non supprimable (XObject, police Type 3) recouvert ; tests unitaires dédiés (MOD-02) |
| Reflow de mauvaise qualité sur les PDF complexes | Moyen | Heuristiques par blocs de texte ; retour automatique à la vue originale |
| Licence d'une bibliothèque incompatible (GPL/AGPL) | Fort | Vérification systématique en L0 et à chaque nouvelle dépendance |
| Mémoire insuffisante sur gros PDF ou appareils modestes | Fort | Rendu par tuiles, cache borné, tests sur appareil bas de gamme |
| Mises en page tablette négligées en fin de projet | Moyen | Tablette incluse dans la Definition of Done dès L1 |
| Dérive du périmètre | Fort | Respect strict des lots ; changements validés par nouvelle version du cahier des charges |

---

## 12. Évolutions prévues (versions ultérieures)

| Évolution | Remarque |
|---|---|
| **Numérisation par caméra** | Scan avec détection des bords, correction de perspective, filtres, puis OCR automatique. Piste : ML Kit Document Scanner ou CameraX + OpenCV. L'architecture de `pdfops` (OCR, création depuis images) doit permettre de l'ajouter sans refonte |
| Signature numérique par certificat | Signature cryptographique conforme PAdES |
| Synchronisation cloud | Annotations, signets et collections entre appareils |
| Export DOCX / conversion Office → PDF | Nécessite probablement un service distant |

---

## 13. Glossaire

- **SAF** : Storage Access Framework, l'API Android d'accès aux fichiers choisis par l'utilisateur.
- **AcroForm** : formulaires interactifs standard des PDF.
- **Reflow** : réagencement du texte pour l'adapter à la largeur de l'écran.
- **RSVP** : Rapid Serial Visual Presentation, technique de lecture rapide mot par mot.
- **OCR** : reconnaissance optique de caractères.
- **CropBox** : zone visible d'une page PDF.
- **sw600dp** : qualificatif de ressources Android pour les écrans d'au moins 600 dp de large (tablettes).
- **MoSCoW** : méthode de priorisation (Must, Should, Could, Won't).
- **Definition of Done** : liste des conditions à remplir pour considérer une tâche comme terminée.
