# Thèmes NineScanner : guide de reproduction

Ce document décrit **les six thèmes de NineScanner** (version 1.3.0) avec assez de détails pour les
reproduire dans une autre application, Android ou non : palettes complètes, effets (verre, flou,
dégradés), composants, règles d'application et pièges rencontrés.

Les sources de référence dans ce dépôt :

| Fichier | Contenu |
|---|---|
| `app/src/main/res/values/themes.xml` | Palettes de tous les thèmes et surcouches |
| `app/src/main/res/values-v31/themes.xml` | AMOLED + Material You (Android 12+) |
| `app/src/main/res/values/styles.xml` | Textes, cartes, barres, boutons |
| `app/src/main/res/values/attrs.xml` | Attributs propres à l'application (verre, barre flottante) |
| `app/src/main/res/color/*.xml` | Couleurs à états et couleurs avec transparence |
| `app/src/main/res/drawable/bg_crystal_*.xml` | Fonds dégradés du thème Cristal |
| `app/src/main/java/com/scanapp/ui/theme/ThemeManager.java` | Choix et ordre d'application des thèmes |
| `app/src/main/java/com/scanapp/ui/components/FloatingNavBar.java` | Barre du bas flottante et floutée |
| `app/src/main/java/com/scanapp/ui/components/BlurTargetLayout.java` | Contenu à flouter sous la barre |
| `app/src/main/java/com/scanapp/ui/components/ThemeSwatchView.java` | Aperçus des thèmes dans les paramètres |

---

## 1. Vue d'ensemble

| Thème | Clair / sombre | Fond | Material You | Particularité |
|---|---|---|---|---|
| **Système** | suit le téléphone | Clair ou Sombre | oui | bascule automatiquement entre Clair et Sombre |
| **Clair** | clair | `#FBFCFE` | oui | bleu du logo, neutres légèrement bleutés |
| **Sombre** | sombre | `#121318` | oui | gris très foncé bleuté, accents pastel |
| **AMOLED** | sombre | `#000000` | oui (tons ravivés) | noir pur, **texte blanc pur**, accents vifs |
| **Cristal** | suit le téléphone | dégradé lumineux | non | verre dépoli translucide, formes plus arrondies |
| **Sépia** | clair | `#F6EEDC` | non | tons papier chauds, reposants |

Communs à tous les thèmes :

- base **Material 3** (Material Components 1.12, `Theme.Material3.*.NoActionBar`) ;
- **bord à bord** : l'application dessine sous la barre d'état et la barre de navigation du système ;
- **barre du bas flottante en pilule**, avec arrière-plan **flouté** sur Android 12+ (section 9) ;
- couleur de texte **toujours explicite** (section 8.1).

### 1.1 Notation des couleurs

Android écrit la transparence en tête : `#AARRGGBB`. En CSS, l'alpha va à la fin (`#RRGGBBAA`) ou
dans `rgba()`. Pour les couleurs transparentes, les tableaux donnent les deux formes.

| Alpha hex | % | Alpha hex | % | Alpha hex | % |
|---|---|---|---|---|---|
| `0A` | 4 % | `2B` | 17 % | `8C` | 55 % |
| `0F` | 6 % | `2E` | 18 % | `99` | 60 % |
| `14` | 8 % | `4D` | 30 % | `A6` | 65 % |
| `17` | 9 % | `52` | 32 % | `B3` | 70 % |
| `1A` | 10 % | `59` | 35 % | `D9` | 85 % |
| `24` | 14 % | `73` | 45 % | `E6` | 90 % |
| `29` | 16 % | `80` | 50 % | `EB` | 92 % |
| | | | | `F0` / `F2` | 94 % / 95 % |

### 1.2 Rôles de couleur

Les palettes utilisent les rôles Material 3. Leur usage dans l'application :

| Rôle | Utilisé pour |
|---|---|
| `primary` / `onPrimary` | titres de section, éléments actifs, boutons pleins, interrupteurs |
| `primaryContainer` / `onPrimaryContainer` | bouton « Scanner » (thèmes non Cristal) |
| `secondaryContainer` / `onSecondaryContainer` | onglet actif de la barre du bas, choix segmenté coché |
| `colorBackground` | fond des écrans |
| `surface` | barres d'outils |
| `surfaceContainer` | barre d'actions du bas (écran document), pastilles des titres en Cristal, teinte de la barre flottante |
| `surfaceContainerHigh` | boutons segmentés de la caméra (non cochés) ; menus et dialogues (via Material) |
| `onSurface` | texte principal |
| `onSurfaceVariant` | texte secondaire, icônes, onglets inactifs |
| `outline` / `outlineVariant` | contours, séparateurs, liseré de la barre flottante |

---

## 2. Ordre d'application

C'est l'ordre qui permet aux thèmes de se combiner (Android : `ThemeManager.apply`, avant
`super.onCreate()`) :

1. **Thème de base** selon le réglage et le mode sombre :
   `AppTheme.Light`, `AppTheme.Dark` (aussi pour AMOLED), `AppTheme.Sepia`, `AppTheme.CrystalLight` ou `AppTheme.CrystalDark`.
2. **Couleurs dynamiques Material You** (si activées, Android 12+, hors Cristal et Sépia) :
   `ThemeOverlay.Material3.DynamicColors.Light` ou `.Dark`.
3. **AMOLED** seulement : `ThemeOverlay.ScanApp.Amoled` (noir et blanc purs, accents vifs), puis
   `ThemeOverlay.ScanApp.AmoledDynamic` si Material You est actif. Ces surcouches viennent **après**
   Material You pour remplacer ses surfaces grises et ses accents pastel.
4. **Composants** : `ThemeOverlay.ScanApp.Glass` pour Cristal, `ThemeOverlay.ScanApp.Solid` pour tous les autres.

Autres règles :

- **Système** et **Cristal** suivent le mode sombre du téléphone. **Clair** et **Sépia** sont toujours clairs.
  **Sombre** et **AMOLED** sont toujours sombres.
- Changer de thème **recrée l'écran** : les vues XML ne changent pas de thème en direct.
- Le thème de la fenêtre au lancement (`Theme.ScanApp`) a pour fond `#FBFCFE` en clair et `#121318` en sombre.
  Cela évite un flash blanc avant l'application du thème choisi.
- Les icônes de la barre d'état suivent **le thème choisi**, pas celui du système : icônes claires si le thème est sombre.
- La barre d'état est transparente. Avec la navigation à 3 boutons, la barre de navigation reçoit un voile :
  `rgba(255,255,255,0.90)` en clair, `rgba(27,27,27,0.50)` en sombre. Avec la navigation par gestes, elle est transparente.

---

## 3. Thème Clair

Base `Theme.Material3.Light.NoActionBar`. Les rôles marqués *M3* gardent la valeur Material 3 par
défaut (Material Components 1.12).

| Rôle | Couleur |
|---|---|
| primary | `#1F45E0` |
| onPrimary | `#FFFFFF` |
| primaryContainer | `#DDE1FF` |
| onPrimaryContainer | `#001257` |
| secondary | `#00658E` |
| onSecondary | `#FFFFFF` |
| secondaryContainer | `#C7E7FF` |
| onSecondaryContainer | `#001E2E` |
| tertiary | `#6B4EA2` |
| background / surface | `#FBFCFE` |
| surfaceContainerLowest | `#FFFFFF` |
| surfaceContainerLow | `#F4F5FA` |
| surfaceContainer | `#EEF0F6` |
| surfaceContainerHigh | `#E8EAF1` |
| surfaceContainerHighest | `#E2E4EC` |
| onSurface / onBackground | `#1D1B20` *(M3)* |
| onSurfaceVariant | `#49454F` *(M3)* |
| surfaceVariant | `#E7E0EC` *(M3)* |
| outline | `#79747E` *(M3)* |
| outlineVariant | `#CAC4D0` *(M3)* |
| error | `#B3261E` *(M3)* |

> Pour une reproduction plus cohérente, `inversePrimary` (bouton d'action des snackbars) peut prendre
> `#B8C4FF` au lieu du violet Material par défaut (`#D0BCFF`).

## 4. Thème Sombre

Base `Theme.Material3.Dark.NoActionBar`.

| Rôle | Couleur |
|---|---|
| primary | `#B8C4FF` |
| onPrimary | `#002388` |
| primaryContainer | `#0035C4` |
| onPrimaryContainer | `#DDE1FF` |
| secondary | `#8BCEFF` |
| onSecondary | `#00344C` |
| secondaryContainer | `#004C6C` |
| onSecondaryContainer | `#C7E7FF` |
| tertiary | `#D3BBFF` |
| background / surface | `#121318` |
| surfaceContainerLowest | `#0D0E13` |
| surfaceContainerLow | `#1A1B21` |
| surfaceContainer | `#1E1F25` |
| surfaceContainerHigh | `#292A2F` |
| surfaceContainerHighest | `#34343A` |
| onSurface / onBackground | `#E6E0E9` *(M3)* |
| onSurfaceVariant | `#CAC4D0` *(M3)* |
| surfaceVariant | `#49454F` *(M3)* |
| outline | `#938F99` *(M3)* |
| outlineVariant | `#49454F` *(M3)* |
| error / onError | `#F2B8B5` / `#601410` *(M3)* |

> Même remarque : `inversePrimary` peut prendre `#1F45E0` au lieu de `#6750A4`.

## 5. Thème AMOLED

Objectif : **noir pur** (les pixels éteints d'un écran OLED ne consomment rien), **blanc pur** pour le
texte et **couleurs vives**, pour un contraste maximal. AMOLED est une **surcouche** posée sur le thème
Sombre : les rôles absents ci-dessous reprennent ceux du Sombre.

### 5.1 Surfaces et textes

| Rôle | Couleur | Remarque |
|---|---|---|
| background / surface / surfaceDim | `#000000` | noir pur |
| surfaceContainerLowest | `#000000` | |
| surfaceContainerLow | `#0B0B0D` | |
| surfaceContainer | `#101013` | |
| surfaceContainerHigh | `#17171B` | |
| surfaceContainerHighest / surfaceVariant / surfaceBright | `#1F1F24` | |
| onBackground / onSurface | `#FFFFFF` | **blanc pur** |
| onSurfaceVariant | `#DCDCE4` | texte secondaire, contraste ≈ 15:1 |
| surfaceInverse / onSurfaceInverse | `#FFFFFF` / `#000000` | snackbars blancs, texte noir |
| outline | `#9C9CAA` | |
| outlineVariant | `#3A3A44` | |
| textColorPrimary | `#FFFFFF` (désactivé : blanc à 38 %) | voir 5.4 |
| textColorSecondary / textColorTertiary | `#DCDCE4` (désactivé : blanc à 38 %) | voir 5.4 |

### 5.2 Accents vifs (sans Material You)

| Rôle | Couleur | Contraste |
|---|---|---|
| primary / onPrimary | `#6F8CFF` / `#000000` | primary sur noir ≈ 6,5:1 |
| primaryContainer / onPrimaryContainer | `#2448FF` / `#FFFFFF` | ≈ 6:1 (bleu électrique, proche du logo `#0030FF`) |
| inversePrimary | `#2448FF` | action des snackbars blancs |
| secondary / onSecondary | `#2EC5FF` / `#000000` | |
| secondaryContainer / onSecondaryContainer | `#0062A3` / `#FFFFFF` | ≈ 6,4:1 |
| tertiary / onTertiary | `#C77DFF` / `#000000` | |
| tertiaryContainer / onTertiaryContainer | `#7A22E0` / `#FFFFFF` | |
| error / onError | `#FF5A5F` / `#000000` | |
| errorContainer / onErrorContainer | `#B0001C` / `#FFFFFF` | |

Principe : **conteneurs saturés et assez sombres avec texte blanc**, **couleurs d'accent lumineuses avec texte noir**.

### 5.3 Accents vifs avec Material You (Android 12+)

La teinte vient du fond d'écran, mais avec des **tons** plus saturés et plus lumineux que ceux du
thème Sombre standard (qui utilise le ton 80 pour primary, et le ton 30 pour les conteneurs).
Correspondance des ressources système : `accent_300` = ton 70, `accent_600` = ton 40, `accent_700` = ton 30.

| Rôle | Ressource système | Sombre standard (pour comparaison) |
|---|---|---|
| primary | `system_accent1_300` (ton 70) | `accent1_200` (ton 80) |
| onPrimary | `#000000` | ton 20 |
| primaryContainer | `system_accent1_600` (ton 40) | ton 30 |
| onPrimaryContainer | `#FFFFFF` | ton 90 |
| inversePrimary | `system_accent1_600` | |
| secondary | `system_accent2_300` | `accent2_200` |
| onSecondary | `#000000` | |
| secondaryContainer | **`system_accent1_700`** (accent principal, ton 30) | `accent2_700` (gris coloré) |
| onSecondaryContainer | `#FFFFFF` | |
| tertiary | `system_accent3_300` | `accent3_200` |
| onTertiary | `#000000` | |
| tertiaryContainer | `system_accent3_600` | |
| onTertiaryContainer | `#FFFFFF` | |

`secondaryContainer` prend volontairement la teinte **principale** : l'onglet actif et les choix cochés
deviennent colorés au lieu d'être gris. Les surfaces et textes de 5.1 restent appliqués : Material You
ne change que les accents.

Hors Android (iOS, web…), on obtient le même effet en partant de la couleur d'accent de l'utilisateur
et en montant sa saturation et sa luminosité (ton 70 pour l'accent, ton 40 pour les conteneurs).

### 5.4 Piège : la couleur du texte

Material 3 fixe `android:textColorPrimary` et `textColorSecondary` sur des couleurs **constantes**,
qui ne lisent pas `colorOnSurface`. Sans les remplacer, une partie des textes reste en gris
`#E6E0E9` au lieu du blanc pur. Il faut donc les redéfinir dans la surcouche AMOLED, en gardant
l'état désactivé (blanc à 38 %).

## 6. Thème Sépia

Base `Theme.Material3.Light.NoActionBar`. Pas de Material You : la palette papier perdrait son sens.

| Rôle | Couleur |
|---|---|
| primary / onPrimary | `#7A4E1D` / `#FFFFFF` |
| primaryContainer / onPrimaryContainer | `#FFDCBC` / `#2C1600` |
| secondary / onSecondary | `#6F5B40` / `#FFFFFF` |
| secondaryContainer / onSecondaryContainer | `#F9DFBB` / `#261904` |
| tertiary | `#52643F` |
| background / surface | `#F6EEDC` |
| onBackground / onSurface | `#2E2416` |
| surfaceVariant / onSurfaceVariant | `#EADFC8` / `#5C4E3C` |
| surfaceContainerLowest | `#FBF6EA` |
| surfaceContainerLow | `#F1E7D2` |
| surfaceContainer | `#EDE2CB` |
| surfaceContainerHigh | `#E7DBC2` |
| surfaceContainerHighest | `#E1D4B9` |
| outline | `#8C7B62` |
| outlineVariant | `#D6C6A8` |

Principe : aucun blanc ni noir purs. Les neutres sont des beiges et des bruns chauds, et chaque
couleur « sur » (`on…`) est un brun très foncé.

## 7. Thème Cristal

Effet « verre dépoli » façon Liquid Glass : les surfaces sont **translucides** au-dessus d'un **fond
dégradé coloré**. Le thème suit le mode clair/sombre du téléphone. Pas de Material You.

Fonctionnement : `colorBackground` est **transparent**, donc chaque écran laisse voir le fond de la
fenêtre (le dégradé). Les cartes et la barre du bas sont en blanc translucide, avec un liseré clair
qui imite le reflet du bord d'une plaque de verre.

### 7.1 Cristal clair

| Rôle | Android | CSS |
|---|---|---|
| primary / onPrimary | `#3346F0` / `#FFFFFF` | |
| primaryContainer | `#8CFFFFFF` | `rgba(255,255,255,0.55)` |
| onPrimaryContainer | `#0B1A7A` | |
| secondary | `#0B6FA8` | |
| secondaryContainer | `#80FFFFFF` | `rgba(255,255,255,0.50)` |
| onSecondaryContainer | `#07304A` | |
| tertiary | `#8A3FC4` | |
| background | transparent | `transparent` |
| onBackground / onSurface | `#141833` | |
| surface | `#59FFFFFF` | `rgba(255,255,255,0.35)` |
| surfaceVariant | `#73FFFFFF` | `rgba(255,255,255,0.45)` |
| onSurfaceVariant | `#3F4466` | |
| surfaceContainerLowest | `#4DFFFFFF` | `rgba(255,255,255,0.30)` |
| surfaceContainerLow | `#73FFFFFF` | `rgba(255,255,255,0.45)` |
| surfaceContainer | `#E6F3F5FF` | `rgba(243,245,255,0.90)` |
| surfaceContainerHigh | `#F0F6F7FF` | `rgba(246,247,255,0.94)` |
| surfaceContainerHighest | `#99FFFFFF` | `rgba(255,255,255,0.60)` |
| outline | `#7A80A8` | |
| outlineVariant | `#B3FFFFFF` | `rgba(255,255,255,0.70)` |
| **glassFill** (cartes) | `#80FFFFFF` | `rgba(255,255,255,0.50)` |
| **glassStroke** (liserés) | `#A6FFFFFF` | `rgba(255,255,255,0.65)` |
| **glassNavFill** (barre du bas) | `#8CFFFFFF` | `rgba(255,255,255,0.55)` |
| **glassNavHighlight** (onglet actif) | `#D9FFFFFF` | `rgba(255,255,255,0.85)` |

### 7.2 Cristal sombre

| Rôle | Android | CSS |
|---|---|---|
| primary / onPrimary | `#AEB9FF` / `#0A1570` | |
| primaryContainer | `#29FFFFFF` | `rgba(255,255,255,0.16)` |
| onPrimaryContainer | `#E2E6FF` | |
| secondary | `#8FD3FF` | |
| secondaryContainer | `#24FFFFFF` | `rgba(255,255,255,0.14)` |
| onSecondaryContainer | `#D5EEFF` | |
| tertiary | `#E2B6FF` | |
| background | transparent | `transparent` |
| onBackground / onSurface | `#EFF1FF` | |
| surface | `#0FFFFFFF` | `rgba(255,255,255,0.06)` |
| surfaceVariant | `#1AFFFFFF` | `rgba(255,255,255,0.10)` |
| onSurfaceVariant | `#C3C7E6` | |
| surfaceContainerLowest | `#0AFFFFFF` | `rgba(255,255,255,0.04)` |
| surfaceContainerLow | `#14FFFFFF` | `rgba(255,255,255,0.08)` |
| surfaceContainer | `#EB1B1E3A` | `rgba(27,30,58,0.92)` |
| surfaceContainerHigh | `#F220234A` | `rgba(32,35,74,0.95)` |
| surfaceContainerHighest | `#24FFFFFF` | `rgba(255,255,255,0.14)` |
| outline | `#8C91B8` | |
| outlineVariant | `#2EFFFFFF` | `rgba(255,255,255,0.18)` |
| **glassFill** | `#17FFFFFF` | `rgba(255,255,255,0.09)` |
| **glassStroke** | `#2BFFFFFF` | `rgba(255,255,255,0.17)` |
| **glassNavFill** | `#8C1B1E3A` | `rgba(27,30,58,0.55)` |
| **glassNavHighlight** | `#29FFFFFF` | `rgba(255,255,255,0.16)` |

> **Pourquoi `surfaceContainer` et `surfaceContainerHigh` sont presque opaques** : Material s'en sert
> pour les menus, les dialogues et les feuilles du bas. Ceux-ci s'affichent par-dessus du contenu net
> et doivent rester lisibles. Seules les surfaces posées directement sur le dégradé sont vraiment transparentes.

### 7.3 Fond dégradé

Quatre couches superposées : un dégradé linéaire en diagonale, puis trois halos radiaux qui
s'estompent vers le transparent. Centres en % de la largeur et de la hauteur de l'écran. Les rayons
sont en % de la **plus petite** dimension de l'écran (la largeur en portrait).

**Clair** (`bg_crystal_light.xml`)

| Couche | Type | Position / rayon | Couleurs |
|---|---|---|---|
| 1 | linéaire, haut gauche → bas droite | plein écran | `#E4EEFF` → `#F1E8FF` → `#E0F5FF` |
| 2 | radial | centre (12 %, 10 %), rayon 80 % | `rgba(127,166,255,0.60)` → transparent |
| 3 | radial | centre (98 %, 42 %), rayon 75 % | `rgba(255,166,216,0.55)` → transparent |
| 4 | radial | centre (25 %, 92 %), rayon 85 % | `rgba(134,225,255,0.55)` → transparent |

**Sombre** (`bg_crystal_dark.xml`)

| Couche | Type | Position / rayon | Couleurs |
|---|---|---|---|
| 1 | linéaire, haut gauche → bas droite | plein écran | `#0B1030` → `#1A1446` → `#0B2643` |
| 2 | radial | centre (12 %, 10 %), rayon 80 % | `rgba(53,80,255,0.45)` → transparent |
| 3 | radial | centre (98 %, 42 %), rayon 75 % | `rgba(180,53,158,0.32)` → transparent |
| 4 | radial | centre (25 %, 92 %), rayon 85 % | `rgba(14,159,181,0.30)` → transparent |

Équivalent CSS du fond clair (`vmin` = plus petite dimension) :

```css
.crystal-light {
  background:
    radial-gradient(circle 85vmin at 25% 92%, rgba(134,225,255,.55), transparent),
    radial-gradient(circle 75vmin at 98% 42%, rgba(255,166,216,.55), transparent),
    radial-gradient(circle 80vmin at 12% 10%, rgba(127,166,255,.60), transparent),
    linear-gradient(135deg, #E4EEFF, #F1E8FF, #E0F5FF);
  background-attachment: fixed;
}
```
(En CSS, la première couche listée est dessus : l'ordre est l'inverse de celui du `layer-list` Android.)

### 7.4 Formes plus arrondies

Le verre est plus doux avec des angles plus ronds. Cristal remplace l'échelle d'arrondis de Material 3 :

| Taille | Material 3 (autres thèmes) | Cristal |
|---|---|---|
| Très petite | 4 dp | **10 dp** |
| Petite | 8 dp | **14 dp** |
| Moyenne (cartes) | 12 dp | **22 dp** |
| Grande | 16 dp | **28 dp** |
| Très grande | 28 dp | **36 dp** |

---

## 8. Composants

### 8.1 Textes

Material 3 ne fixe **pas** la couleur des styles de texte. Sur un thème sombre ou transparent, un
texte sans couleur explicite peut devenir illisible. Tous les styles de texte de l'application
fixent donc leur couleur :

| Style | Typographie M3 | Couleur |
|---|---|---|
| HeadlineSmall | headlineSmall, **gras** | onSurface |
| HeadlineMedium | headlineMedium | onSurface |
| TitleMedium / TitleSmall | titleMedium / titleSmall | onSurface |
| BodyLarge / BodyMedium | bodyLarge / bodyMedium | onSurface |
| BodySmall | bodySmall | **onSurfaceVariant** |
| LabelMedium / LabelSmall | labelMedium / labelSmall | onSurface |
| Titre de section | labelLarge | **primary** |
| Titre de section (paramètres) | labelLarge, marges 28 / 16 / 16 / 8 dp | primary |

### 8.2 Ce qui change entre Cristal et les autres thèmes

| Élément | Thèmes « pleins » (Clair, Sombre, AMOLED, Sépia, Système) | Cristal |
|---|---|---|
| Cartes | carte Material **surélevée**, arrondi moyen (12 dp) | carte **contour** : fond `glassFill`, liseré 1 dp `glassStroke`, sans ombre, arrondi 22 dp |
| Bouton « Scanner » | fond `primaryContainer`, contenu `onPrimaryContainer` | fond **`primary` plein**, contenu `onPrimary` (lisible sur le verre) |
| Titres de section fixés en haut des listes | bande pleine de la couleur du fond | fond transparent, titre dans une **pastille** : fond `surfaceContainer`, arrondi complet, marges 12 × 4 dp |
| Barre du bas | voir section 9 : teinte `surfaceContainer` à 72 % | teinte `glassNavFill` |

### 8.3 Autres composants (tous thèmes)

- **Barre d'outils** : fond `surface`, titre et icônes `onSurface`.
- **Barre d'actions du bas** (écran document) : hauteur mini 80 dp, fond `surfaceContainer`, marges 8 dp à gauche et 16 dp à droite.
- **Boutons icônes** : zone 48 × 48 dp, ondulation sans bordure, icône `onSurfaceVariant`.
- **Choix segmenté** (boutons à contour) : coché → fond `secondaryContainer`, texte `onSecondaryContainer` ;
  non coché → fond transparent, texte `onSurface` ; désactivé → `onSurface` à 38 % ; contour `outline`.
  Sur l'image de la caméra, le fond non coché devient `surfaceContainerHigh` pour rester lisible.
- **Bouton « Scanner »** : bouton flottant étendu (icône + texte), à 16 dp du bord droit et 16 dp au-dessus de la barre du bas.

---

## 9. Barre du bas flottante et floutée (tous les thèmes)

### 9.1 Géométrie

```
┌──────────────────────── largeur de l'écran ────────────────────────┐
│ 20dp ┌───────────── pilule, largeur max 480dp, centrée ──────┐ 20dp │  ↑ 12dp
│      │ 6dp ┌─────────┐ ┌─────────┐ ┌─────────┐ 6dp           │      │
│      │     │ ▣ icône │ │  icône  │ │  icône  │               │      │  arrondi 32dp
│      │     │ Accueil │ │Documents│ │Paramètr.│               │      │  liseré 1dp
│      │     └─────────┘ └─────────┘ └─────────┘               │      │
│      └────────────────────────────────────────────────────────┘      │  ↓ 12dp
│                    + hauteur de la barre de navigation système        │
└───────────────────────────────────────────────────────────────────────┘
```

| Élément | Valeur |
|---|---|
| Marges autour de la pilule | 20 dp sur les côtés, 12 dp en haut et en bas, + hauteur de la barre système en bas |
| Pilule | largeur max **480 dp**, centrée ; marge intérieure **6 dp** ; arrondi **32 dp** ; liseré **1 dp** |
| Ombre | **3 dp** (thèmes pleins), **aucune** (Cristal) |
| Onglets | 3, largeur égale, 2 dp d'écart de chaque côté, 8 dp de marge verticale |
| Icône / texte | icône 24 dp ; texte labelSmall, une ligne, coupé par « … » |
| Onglet actif | fond `navBarHighlight`, arrondi **26 dp**, fondu de **150 ms** ; icône et texte `navBarSelected` |
| Onglet inactif | fond transparent ; icône et texte `onSurfaceVariant` |
| Ondulation au toucher | limitée à la forme de l'onglet |

### 9.2 Couleurs par thème

| Thème | Teinte (`navBarFill`) | Liseré (`navBarStroke`) | Onglet actif : fond / contenu |
|---|---|---|---|
| Clair | `rgba(238,240,246,0.72)` (surfaceContainer à 72 %) | `#CAC4D0` à 60 % | `#C7E7FF` / `#001E2E` |
| Sombre | `rgba(30,31,37,0.72)` | `#49454F` à 60 % | `#004C6C` / `#C7E7FF` |
| AMOLED | `rgba(16,16,19,0.72)` | `#3A3A44` à 60 % | `#0062A3` / `#FFFFFF` |
| Sépia | `rgba(237,226,203,0.72)` | `#D6C6A8` à 60 % | `#F9DFBB` / `#261904` |
| Cristal clair | `rgba(255,255,255,0.55)` | `rgba(255,255,255,0.65)` | `rgba(255,255,255,0.85)` / `#3346F0` |
| Cristal sombre | `rgba(27,30,58,0.55)` | `rgba(255,255,255,0.17)` | `rgba(255,255,255,0.16)` / `#AEB9FF` |

Avec Material You, les thèmes pleins gardent ces règles (`surfaceContainer` à 72 %, `outlineVariant`
à 60 %, `secondaryContainer` / `onSecondaryContainer`), mais avec les couleurs de la palette dynamique.

### 9.3 Rendu, de bas en haut

1. **Fond opaque** sous le contenu : `colorBackground` du thème, ou le dégradé de la fenêtre pour Cristal.
   Sans ce fond, le contenu net transparaîtrait à travers le flou.
2. **Contenu des onglets** situé sous la barre, élargi de **2 × le rayon du flou** de chaque côté.
   Sinon, les bords du flou seraient trop nets.
3. **Saturation × 1,25** (couleurs ravivées, comme un verre dépoli).
4. **Flou** de rayon **20 dp** (≈ écart-type 12 dp), bords prolongés (mode « clamp »).
5. **Teinte** `navBarFill` (tableau 9.2).
6. **Liseré** 1 dp `navBarStroke`.
7. Les onglets.

Le tout est découpé à la forme de la pilule.

**Pour que le flou serve à quelque chose, le contenu doit passer sous la barre** : les listes
dessinent jusqu'en bas de l'écran (`clipToPadding="false"`), avec une marge basse
= hauteur de la barre + 88 dp (bouton « Scanner ») ; + 16 dp pour l'écran Paramètres.

**Sans flou** (Android 8 à 11, ou rendu logiciel) : la teinte est portée à **92 % d'opacité minimum**
pour que les onglets restent lisibles au-dessus du contenu net.

### 9.4 Implémentation Android (12+)

Android ne sait pas flouter « ce qu'il y a derrière » une vue de la même fenêtre :
`RenderEffect` floute le contenu de la vue elle-même. La solution, sans bibliothèque ni capture d'image :

- **`BlurTargetLayout`** (un `FrameLayout` autour du `ViewPager2`) enregistre ses enfants dans un
  `RenderNode` à chaque dessin, puis dessine ce `RenderNode` normalement.
- **`FloatingNavBar`** possède un second `RenderNode` portant le `RenderEffect` (saturation puis flou).
  Avant chaque image (`OnPreDrawListener`), elle y **réenregistre** le fond opaque, puis le `RenderNode`
  du contenu, décalé de sa propre position. Dans `onDraw`, elle dessine ce nœud, puis la teinte et le liseré.
- La barre **n'est pas invalidée** dans le `OnPreDrawListener` : son affichage fait référence au nœud
  par pointeur, et le nouvel enregistrement est pris en compte dans la même image. Invalider la vue à
  cet endroit relancerait une image à chaque image : l'application redessinerait en continu et viderait la batterie.
- Découpe à la forme de la pilule par `setClipToOutline(true)` (anticrénelage, et les ondulations sont découpées aussi).

### 9.5 Équivalents sur d'autres plateformes

**Web (CSS)**

```css
.nav-bar {
  position: fixed; left: 50%; transform: translateX(-50%);
  bottom: calc(12px + env(safe-area-inset-bottom));
  width: min(480px, calc(100% - 40px));
  padding: 6px; border-radius: 32px;
  background: rgba(238, 240, 246, .72);          /* navBarFill du thème */
  border: 1px solid rgba(202, 196, 208, .60);    /* navBarStroke */
  backdrop-filter: saturate(1.25) blur(12px);
  -webkit-backdrop-filter: saturate(1.25) blur(12px);
  box-shadow: 0 2px 6px rgba(0, 0, 0, .15);      /* thèmes pleins seulement */
}
@supports not (backdrop-filter: blur(1px)) {
  .nav-bar { background: rgba(238, 240, 246, .92); }
}
.nav-tab { flex: 1; margin: 0 2px; padding: 8px 0; border-radius: 26px; transition: background .15s; }
.nav-tab[aria-selected="true"] { background: #C7E7FF; color: #001E2E; }   /* navBarHighlight / navBarSelected */
```

**Flutter** : `ClipRRect(borderRadius: 32)` > `BackdropFilter(filter: ImageFilter.compose(outer:
ImageFilter.blur(sigmaX: 12, sigmaY: 12), inner: ColorFilter.matrix(saturation 1.25)))` > `Container(color: navBarFill)`.

**Jetpack Compose** : `Modifier.blur` floute le composable lui-même, pas l'arrière-plan. Il faut
reprendre l'approche `RenderNode` ci-dessus, ou une bibliothèque de flou d'arrière-plan
(par exemple *Haze*).

**SwiftUI** : `.background(.ultraThinMaterial)` donne un flou d'arrière-plan natif. Ajouter
par-dessus la teinte `navBarFill` avec une opacité un peu plus faible, car le matériau iOS est déjà teinté.

---

## 10. Aperçus dans les paramètres

Chaque thème est présenté par une mini-maquette : rapport largeur/hauteur 0,85, arrondi 14 dp,
marges 8 dp. Elle contient une barre de titre (texte à 75 % d'opacité), deux cartes de 16 dp de haut
(arrondi 5 dp) et une pastille d'accent de 16 dp en bas à droite. Le thème choisi a un contour de 3 dp
`primary` et une coche ; les autres, un contour de 1 dp `outlineVariant`.

| Thème | Fond | Cartes | Titre | Accent |
|---|---|---|---|---|
| Système | moitié `#FBFCFE`, moitié `#121318` (coupure verticale) | `rgba(142,144,154,0.45)` | `#8E909A` | `#5C7BFF` |
| Clair | `#FBFCFE` | `#E8EAF1` | `#1A1C1E` | `#1F45E0` |
| Sombre | `#121318` | `#292A2F` | `#E3E2E6` | `#B8C4FF` |
| AMOLED | `#000000` | `#17171B` | `#FFFFFF` | `#6F8CFF` |
| Cristal | dégradé diagonal `#8FB0FF` → `#F3B2E0` → `#8DE0FF` | `rgba(255,255,255,0.55)` | `#141833` | `#3346F0` |
| Sépia | `#F6EEDC` | `#E7DBC2` | `#2E2416` | `#7A4E1D` |

---

## 11. Liste de contrôle pour une autre application

1. Définir les **rôles** de la section 1.2 pour chaque thème, sans en laisser aux valeurs par défaut
   si la cohérence compte (notamment `inversePrimary`, `error`, `tertiaryContainer`).
2. Appliquer les thèmes dans l'**ordre** de la section 2 : base → Material You → AMOLED → composants.
3. Donner une **couleur explicite à chaque texte** (section 8.1), et pour AMOLED, remplacer aussi les
   couleurs de texte « primaire » et « secondaire » du système (section 5.4).
4. **Cristal** : fond de fenêtre en dégradé, fond des écrans **transparent**, surfaces en blanc
   translucide, menus et dialogues presque opaques, arrondis agrandis, bouton principal plein.
5. **Barre du bas** : pilule flottante, contenu qui défile **dessous**, flou avec fond opaque et marge
   de flou, teinte par thème, solution de repli presque opaque sans flou.
6. **Bord à bord** : icônes de la barre d'état selon le thème **choisi**, marges selon la hauteur des barres système.
7. Vérifier les **contrastes** : au moins 4,5:1 pour le texte (AMOLED : 21:1 pour le texte principal, ≈ 15:1 pour le secondaire).
