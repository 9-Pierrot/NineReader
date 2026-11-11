# Barre de navigation flottante à arrière-plan flou (Android, Views)

Guide pour reproduire la barre « pilule » flottante de ScanApp : le contenu qui défile dessous
apparaît **flouté en temps réel**, puis teinté par la couleur du thème, avec un liseré fin.

- Langage : Java, système de Views (pas Compose), View Binding.
- `minSdk 26`, `compileSdk/targetSdk 35`. Dépendances : Material Components 3, ConstraintLayout,
  CoordinatorLayout, ViewPager2 (pour les onglets).
- Flou réel à partir d'**Android 12 (API 31)**. En dessous, la teinte devient presque opaque :
  la barre reste lisible, sans flou.

Fichiers d'origine dans ScanApp :

| Rôle | Fichier |
|---|---|
| Conteneur du contenu à flouter | `app/src/main/java/com/scanapp/ui/components/BlurTargetLayout.java` |
| La barre elle-même | `app/src/main/java/com/scanapp/ui/components/FloatingNavBar.java` |
| Mise en page | `app/src/main/res/layout/fragment_main.xml`, `view_nav_tab.xml` |
| Couleurs / attributs | `res/values/attrs.xml`, `res/values/themes.xml`, `res/color/nav_*.xml`, `res/drawable/bg_nav_tab.xml` |
| Branchement | `app/src/main/java/com/scanapp/ui/screens/main/MainFragment.java` |
| Marges système | `app/src/main/java/com/scanapp/ui/components/EdgeInsets.java` |

---

## 1. Principe

Le problème : `View.setRenderEffect(blur)` floute **la vue elle-même et ses enfants**, pas ce qui
se trouve derrière. Il n'y a pas de « backdrop-filter » natif dans le système de Views.

La solution, sans capture d'écran ni copie de bitmap (tout reste sur le GPU) :

```
CoordinatorLayout
├── BlurTargetLayout   ← enregistre ses enfants dans un RenderNode « content »
│   └── ViewPager2 (onglets, listes…)          puis dessine ce RenderNode normalement
└── FloatingNavBar     ← possède un RenderNode « blurNode » muni d'un RenderEffect (flou + saturation)
                          et, à chaque image, y redessine « content » décalé pour que seule la
                          zone située sous la barre s'y retrouve
```

1. **`BlurTargetLayout`** surcharge `dispatchDraw` : au lieu de dessiner ses enfants directement
   sur le canvas, il les enregistre dans un `RenderNode`, puis dessine ce `RenderNode`. Le rendu à
   l'écran est identique, mais la liste d'affichage du contenu devient réutilisable.
2. **`FloatingNavBar`** possède son propre `RenderNode` auquel est attaché un `RenderEffect`
   (flou gaussien + légère hausse de saturation). Avant chaque image (`OnPreDrawListener`), elle
   y enregistre : un fond opaque, puis le `RenderNode` du contenu, translaté de la position
   relative barre/contenu.
3. Dans son `onDraw`, la barre dessine ce nœud flouté, puis la teinte du thème par-dessus, puis
   le liseré. `setClipToOutline(true)` + un contour arrondi découpent le tout en pilule.

> ⚠️ La barre ne doit **jamais** être à l'intérieur du `BlurTargetLayout` : elle se dessinerait
> elle-même dans son propre flou (récursion). Les deux sont **frères**, la barre au-dessus.

---

## 2. `BlurTargetLayout.java`

```java
package com.example.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Conteneur dont le contenu peut être flouté par une FloatingNavBar posée au-dessus.
 * À partir d'Android 12, ses enfants sont enregistrés dans un RenderNode que la barre redessine
 * flouté : ni capture d'écran, ni copie d'image, le GPU fait tout le travail.
 */
public class BlurTargetLayout extends FrameLayout {

    /** Le flou d'une vue (RenderEffect) n'existe qu'à partir d'Android 12. */
    public static final boolean BLUR_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    // Typé Object : aucune référence à RenderNode dans la signature de la classe,
    // pour qu'elle se charge sans souci sur les API < 29/31.
    @Nullable
    private final Object content;

    public BlurTargetLayout(@NonNull Context context) {
        this(context, null);
    }

    public BlurTargetLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        content = BLUR_SUPPORTED ? Api31.create() : null;
    }

    /** Contenu enregistré lors du dernier dessin, ou null avant Android 12. */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.S)
    RenderNode content() {
        return (RenderNode) content;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Rendu logiciel (ex. capture d'une vue dans un Bitmap) : drawRenderNode n'y est pas permis
        if (BLUR_SUPPORTED && content != null && canvas.isHardwareAccelerated()) {
            Api31.record((RenderNode) content, this, canvas);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    private void drawChildren(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    /** Tout le code API 31 isolé ici. */
    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31 {
        static RenderNode create() {
            return new RenderNode("BlurTarget");
        }

        static void record(RenderNode node, BlurTargetLayout layout, Canvas canvas) {
            node.setPosition(0, 0, layout.getWidth(), layout.getHeight());
            RecordingCanvas recording = node.beginRecording();
            layout.drawChildren(recording);
            node.endRecording();
            canvas.drawRenderNode(node);
        }
    }
}
```

---

## 3. `FloatingNavBar.java`

```java
package com.example.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.example.R;

/**
 * Barre de navigation flottante en forme de pilule. À partir d'Android 12, le contenu qui passe
 * dessous (BlurTargetLayout) y apparaît flouté, puis teinté par la couleur du thème (navBarFill).
 * Avant Android 12, la teinte est rendue presque opaque pour rester lisible.
 */
public class FloatingNavBar extends LinearLayout {

    private static final float BLUR_RADIUS_DP = 20f;
    private static final float CORNER_RADIUS_DP = 32f;
    /** Couleurs du flou légèrement ravivées, comme un verre dépoli. */
    private static final float BLUR_SATURATION = 1.25f;
    /** Opacité minimale de la teinte quand rien n'est flouté dessous (≈ 92 %). */
    private static final int UNBLURRED_MIN_ALPHA = 0xEB;

    private final float cornerRadius;
    private final int blurPadding;
    private final int fillColor;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF strokeBounds = new RectF();
    /** Ce qui est dessiné sous le contenu des onglets : fond de l'écran ou dégradé de la fenêtre. */
    @Nullable
    private final Drawable backdrop;
    @Nullable
    private final Object blurNode;
    @Nullable
    private BlurTargetLayout target;
    private final int[] location = new int[2];
    private final int[] targetLocation = new int[2];

    /**
     * Réenregistre le flou avant chaque image, sans invalider la barre : son affichage fait
     * référence au RenderNode, la nouvelle version est donc prise en compte dans la même image.
     */
    private final ViewTreeObserver.OnPreDrawListener updateBlur = () -> {
        if (BlurTargetLayout.BLUR_SUPPORTED) Api31.record(this);
        return true;
    };

    public FloatingNavBar(@NonNull Context context) {
        this(context, null);
    }

    public FloatingNavBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false); // un LinearLayout n'appelle pas onDraw par défaut
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = CORNER_RADIUS_DP * density;
        float blurRadius = BLUR_RADIUS_DP * density;
        // Marge autour de la barre incluse dans le flou : sans elle, ses bords seraient trop nets
        blurPadding = Math.round(blurRadius * 2);

        fillColor = MaterialColors.getColor(this, R.attr.navBarFill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density); // 1 dp
        stroke.setColor(MaterialColors.getColor(this, R.attr.navBarStroke));

        // Forme pilule : découpe le flou, la teinte et les enfants
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
        TypedValue elevation = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.navBarElevation, elevation, true)) {
            setElevation(elevation.getDimension(getResources().getDisplayMetrics()));
        }

        backdrop = resolveBackdrop(context);
        blurNode = BlurTargetLayout.BLUR_SUPPORTED ? Api31.create(blurRadius) : null;
    }

    /** Le contenu à flouter sous la barre. */
    public void setBlurTarget(@Nullable BlurTargetLayout target) {
        this.target = target;
        invalidate();
    }

    /**
     * Fond opaque à placer sous le contenu dans le flou : colorBackground s'il est opaque,
     * sinon le windowBackground (thème à fond transparent + dégradé de fenêtre).
     */
    @Nullable
    private static Drawable resolveBackdrop(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
                && Color.alpha(value.data) == 0xFF) {
            return new ColorDrawable(value.data);
        }
        if (context.getTheme().resolveAttribute(android.R.attr.windowBackground, value, true)) {
            if (value.resourceId != 0) {
                Drawable drawable = ContextCompat.getDrawable(context, value.resourceId);
                return drawable == null ? null : drawable.mutate();
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return new ColorDrawable(value.data);
            }
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (BlurTargetLayout.BLUR_SUPPORTED) getViewTreeObserver().addOnPreDrawListener(updateBlur);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (BlurTargetLayout.BLUR_SUPPORTED) getViewTreeObserver().removeOnPreDrawListener(updateBlur);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        boolean blurred = BlurTargetLayout.BLUR_SUPPORTED && target != null && canvas.isHardwareAccelerated()
                && Api31.draw(this, canvas);
        // 1) flou (si dispo)  2) teinte du thème  3) liseré
        canvas.drawColor(blurred ? fillColor : withMinAlpha(fillColor));
        if (Color.alpha(stroke.getColor()) > 0) {
            float inset = stroke.getStrokeWidth() / 2f;
            strokeBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(strokeBounds, cornerRadius - inset, cornerRadius - inset, stroke);
        }
    }

    private static int withMinAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, Math.max(Color.alpha(color), UNBLURRED_MIN_ALPHA));
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31 {
        static RenderNode create(float blurRadius) {
            RenderNode node = new RenderNode("FloatingNavBarBlur");
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(BLUR_SATURATION);
            // createChainEffect(outer, inner) : d'abord le flou (inner), puis la saturation (outer)
            node.setRenderEffect(RenderEffect.createChainEffect(
                    RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(saturation)),
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)));
            return node;
        }

        /** Recopie la partie du contenu située sous la barre (avec la marge du flou). */
        static void record(FloatingNavBar bar) {
            BlurTargetLayout target = bar.target;
            if (target == null || bar.getWidth() == 0 || bar.getHeight() == 0) return;
            RenderNode content = target.content();
            if (content == null) return;
            bar.getLocationInWindow(bar.location);
            target.getLocationInWindow(bar.targetLocation);
            int dx = bar.location[0] - bar.targetLocation[0];
            int dy = bar.location[1] - bar.targetLocation[1];
            int pad = bar.blurPadding;

            RenderNode node = (RenderNode) bar.blurNode;
            // Le nœud déborde de « pad » de chaque côté : le flou échantillonne les vrais pixels
            // voisins au lieu de s'estomper sur les bords. Le clipToOutline coupe l'excédent.
            node.setPosition(-pad, -pad, bar.getWidth() + pad, bar.getHeight() + pad);
            RecordingCanvas canvas = node.beginRecording();
            canvas.translate(pad - dx, pad - dy);
            // Fond opaque sous le contenu : sinon le contenu net transparaîtrait à travers le flou
            if (bar.backdrop != null) {
                bar.backdrop.setBounds(0, 0, target.getWidth(), target.getHeight());
                bar.backdrop.draw(canvas);
            }
            canvas.drawRenderNode(content);
            node.endRecording();
        }

        static boolean draw(FloatingNavBar bar, Canvas canvas) {
            RenderNode node = (RenderNode) bar.blurNode;
            if (node == null || !node.hasDisplayList()) {
                record(bar);
                if (node == null || !node.hasDisplayList()) return false;
            }
            canvas.drawRenderNode(node);
            return true;
        }
    }
}
```

---

## 4. Ressources

### 4.1 `res/values/attrs.xml`

Les couleurs de la barre passent par des attributs de thème, pour pouvoir varier selon le thème.

```xml
<resources>
    <attr name="navBarFill" format="color" />      <!-- teinte posée sur le flou -->
    <attr name="navBarStroke" format="color" />    <!-- liseré 1 dp -->
    <attr name="navBarHighlight" format="color" /> <!-- fond de l'onglet sélectionné -->
    <attr name="navBarSelected" format="color" />  <!-- icône + texte de l'onglet sélectionné -->
    <attr name="navBarElevation" format="dimension" />
</resources>
```

### 4.2 Valeurs par défaut (thèmes « pleins » Material 3)

`res/color/nav_bar_fill.xml` — teinte à 72 % d'opacité :

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:alpha="0.72" android:color="?attr/colorSurfaceContainer" />
</selector>
```

`res/color/nav_bar_stroke.xml` :

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:alpha="0.6" android:color="?attr/colorOutlineVariant" />
</selector>
```

Dans le thème (ScanApp les met dans un overlay `ThemeOverlay.ScanApp.Solid` appliqué aux thèmes
Clair / Sombre / Sépia / AMOLED) :

```xml
<item name="navBarFill">@color/nav_bar_fill</item>
<item name="navBarStroke">@color/nav_bar_stroke</item>
<item name="navBarHighlight">?attr/colorSecondaryContainer</item>
<item name="navBarSelected">?attr/colorOnSecondaryContainer</item>
<item name="navBarElevation">3dp</item>
```

### 4.3 Variante « verre » (thème Cristal, fond de fenêtre en dégradé)

Le thème Cristal a `android:colorBackground` **transparent** et un `android:windowBackground`
en `layer-list` de dégradés. La barre le détecte (`resolveBackdrop`) et utilise ce dégradé comme
fond opaque sous le flou. Valeurs :

```xml
<!-- Overlay Glass -->
<item name="navBarFill">?attr/glassNavFill</item>
<item name="navBarStroke">?attr/glassStroke</item>
<item name="navBarHighlight">?attr/glassNavHighlight</item>
<item name="navBarSelected">?attr/colorPrimary</item>
<item name="navBarElevation">0dp</item>  <!-- pas d'ombre : elle se verrait à travers le verre -->

<!-- Cristal clair -->
<item name="glassStroke">#A6FFFFFF</item>
<item name="glassNavFill">#8CFFFFFF</item>       <!-- blanc 55 % -->
<item name="glassNavHighlight">#D9FFFFFF</item>

<!-- Cristal sombre -->
<item name="glassStroke">#2BFFFFFF</item>
<item name="glassNavFill">#8C1B1E3A</item>       <!-- bleu nuit 55 % -->
<item name="glassNavHighlight">#29FFFFFF</item>
```

### 4.4 Onglet : `res/color/nav_tab_tint.xml` et `res/drawable/bg_nav_tab.xml`

```xml
<!-- nav_tab_tint.xml -->
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="?attr/navBarSelected" android:state_selected="true" />
    <item android:color="?attr/colorOnSurfaceVariant" />
</selector>
```

```xml
<!-- bg_nav_tab.xml : pastille arrondie en fondu sous l'onglet sélectionné -->
<selector xmlns:android="http://schemas.android.com/apk/res/android"
    android:enterFadeDuration="150" android:exitFadeDuration="150">
    <item android:state_selected="true">
        <shape>
            <solid android:color="?attr/navBarHighlight" />
            <corners android:radius="26dp" />
        </shape>
    </item>
    <item>
        <shape>
            <solid android:color="@android:color/transparent" />
            <corners android:radius="26dp" />
        </shape>
    </item>
</selector>
```

Rayons cohérents : barre 32 dp, padding interne 6 dp → onglet 26 dp (32 − 6), pour des arrondis
concentriques.

### 4.5 `res/layout/view_nav_tab.xml`

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/tab"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="2dp"
    android:layout_weight="1"
    android:background="@drawable/bg_nav_tab"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    android:gravity="center_horizontal"
    android:orientation="vertical"
    android:paddingVertical="8dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:importantForAccessibility="no"
        app:tint="@color/nav_tab_tint" />

    <TextView
        android:id="@+id/label"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:ellipsize="end"
        android:maxLines="1"
        android:textAppearance="?attr/textAppearanceLabelSmall"
        android:textColor="@color/nav_tab_tint" />
</LinearLayout>
```

---

## 5. Mise en page de l'écran (`fragment_main.xml`)

```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground">

    <!-- Le contenu occupe TOUT l'écran et défile sous la barre -->
    <com.example.ui.components.BlurTargetLayout
        android:id="@+id/blur_target"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.viewpager2.widget.ViewPager2
            android:id="@+id/pager"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    </com.example.ui.components.BlurTargetLayout>

    <!-- Conteneur : marges latérales, espace au-dessus de la barre système, largeur max -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/bar_container"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:clipToPadding="false"
        android:paddingHorizontal="20dp"
        android:paddingVertical="12dp">

        <com.example.ui.components.FloatingNavBar
            android:id="@+id/floating_nav"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="6dp"
            app:layout_constraintEnd_toEndOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintWidth_max="480dp">

            <include android:id="@+id/nav_home" layout="@layout/view_nav_tab" />
            <include android:id="@+id/nav_documents" layout="@layout/view_nav_tab" />
            <include android:id="@+id/nav_settings" layout="@layout/view_nav_tab" />
        </com.example.ui.components.FloatingNavBar>
    </androidx.constraintlayout.widget.ConstraintLayout>

    <!-- Optionnel : bouton d'action placé au-dessus de la barre (marge calculée en code) -->
    <com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
        android:id="@+id/fab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="16dp"
        android:text="Scanner" />
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

Points clés :
- `ConstraintLayout` + `layout_constraintWidth_max="480dp"` : la barre s'étire sur téléphone mais
  reste centrée et limitée sur tablette / paysage.
- `clipToPadding="false"` sur le conteneur : l'ombre (`elevation`) de la barre n'est pas coupée.

---

## 6. Branchement en code (`MainFragment`)

```java
private final MutableLiveData<Integer> barHeight = new MutableLiveData<>(0);

public LiveData<Integer> barHeight() { return barHeight; }

@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    binding.pager.setAdapter(new TabsAdapter(getChildFragmentManager(), getViewLifecycleOwner().getLifecycle()));

    // Écart sous la barre = barre de navigation système (app en edge-to-edge)
    EdgeInsets.padNavigationBar(binding.barContainer);
    // ⇩ La ligne qui active le flou
    binding.floatingNav.setBlurTarget(binding.blurTarget);

    bindTab(binding.navHome, R.drawable.ic_tab_home, R.string.tab_home, 0);
    bindTab(binding.navDocuments, R.drawable.ic_tab_documents, R.string.tab_documents, 1);
    bindTab(binding.navSettings, R.drawable.ic_tab_settings, R.string.tab_settings, 2);

    binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            selectTab(position);
        }
    });

    binding.barContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> onBarLaidOut(v.getHeight()));
}

private void bindTab(ViewNavTabBinding tab, @DrawableRes int icon, @StringRes int label, int index) {
    tab.icon.setImageResource(icon);
    tab.label.setText(label);
    tab.tab.setClipToOutline(true); // le ripple suit les coins arrondis de la pastille
    tab.tab.setOnClickListener(v -> binding.pager.setCurrentItem(index, true));
}

private void selectTab(int position) {
    binding.navHome.tab.setSelected(position == 0);
    binding.navDocuments.tab.setSelected(position == 1);
    binding.navSettings.tab.setSelected(position == 2);
}

private void onBarLaidOut(int height) {
    // Différé : modifier un padding pendant la mise en page serait ignoré
    binding.getRoot().post(() -> {
        if (binding == null) return;
        Integer current = barHeight.getValue();
        if (current == null || current != height) barHeight.setValue(height);
        // Bouton d'action juste au-dessus de la barre (revérifié à chaque fois : la vue peut être recréée)
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) binding.fab.getLayoutParams();
        int margin = height + EdgeInsets.dp(binding.fab, 16);
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            binding.fab.setLayoutParams(params);
        }
    });
}
```

### 6.1 `EdgeInsets.padNavigationBar`

L'application dessine sous les barres système (edge-to-edge). On **ajoute** la hauteur de la
barre système au padding d'origine (et non une marge Material, qui inclurait le clavier et ferait
remonter la barre au-dessus de lui) :

```java
public static void padNavigationBar(View view) {
    int left = view.getPaddingLeft(), top = view.getPaddingTop();
    int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
    ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
        Insets bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        v.setPadding(left + bars.left, top, right + bars.right, bottom + bars.bottom);
        return insets;
    });
}

public static int dp(View view, int value) {
    return Math.round(value * view.getResources().getDisplayMetrics().density);
}
```

### 6.2 Listes des onglets : défiler sous la barre

Pour que le contenu passe réellement sous la barre (et donc soit flouté) tout en laissant le
dernier élément atteignable, chaque liste a `android:clipToPadding="false"` et un padding bas
égal à la hauteur de la barre :

```java
// Dans le fragment d'un onglet
MainFragment main = (MainFragment) requireParentFragment();
main.barHeight().observe(getViewLifecycleOwner(), height ->
        binding.list.setPadding(0, 0, 0, height + EdgeInsets.dp(binding.list, 88))); // 88 = place du FAB
```

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/list"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipToPadding="false" />
```

---

## 7. Pièges et réglages

| Point | Pourquoi |
|---|---|
| Barre **hors** du `BlurTargetLayout` | Sinon elle se redessine dans son propre flou (récursion / artefacts). |
| Code API 31 dans une classe interne `Api31`, champs typés `Object` | `minSdk 26` : évite de référencer `RenderNode`/`RenderEffect` dans les signatures de la classe publique. |
| `canvas.isHardwareAccelerated()` | `drawRenderNode` est interdit sur un canvas logiciel (capture en Bitmap, aperçu…) → repli sans flou. |
| `OnPreDrawListener` qui réenregistre sans `invalidate()` | Suit le défilement et le déplacement de la barre à chaque image ; invalider depuis le pre-draw provoquerait une boucle de redessin. |
| Fond opaque (`backdrop`) dessiné avant le contenu | Si le contenu a des zones transparentes, le flou serait translucide et le contenu **net** dessous se verrait à travers. |
| Thème à `colorBackground` transparent | Le `backdrop` devient le `windowBackground` (dégradé) : sinon zones noires/vides dans le flou. |
| `blurPadding = 2 × rayon` + `TileMode.CLAMP` | Bords du flou réalistes ; sans marge, les bords de la pilule restent nets / s'estompent. |
| `setClipToOutline(true)` + `ViewOutlineProvider` | Découpe flou + teinte + ripples en pilule. Pas besoin de `clipPath`. |
| `setWillNotDraw(false)` | Un `LinearLayout` sans fond n'appelle pas `onDraw` sinon. |
| Teinte ~55–72 % d'alpha | Trop transparente : texte illisible sur contenu chargé. Trop opaque : l'effet disparaît. |
| Repli < API 31 : alpha min `0xEB` | Pas de flou → teinte quasi opaque pour garder le contraste. |
| `elevation` 0 dp sur les thèmes translucides | L'ombre portée apparaît à travers une surface très transparente. |
| Saturation 1,25 après le flou | Le flou ternit les couleurs ; une légère hausse donne l'aspect « verre dépoli » iOS-like. |

Paramètres à ajuster : `BLUR_RADIUS_DP` (20), `CORNER_RADIUS_DP` (32), `BLUR_SATURATION` (1.25),
`UNBLURRED_MIN_ALPHA` (0xEB), alpha de `nav_bar_fill` (0.72).

### Tester

- Appareil/émulateur **Android 12+** pour le flou ; un Android 8–11 pour vérifier le repli opaque.
- Faire défiler une liste colorée (vignettes d'images) sous la barre : le flou doit suivre sans
  retard d'une image.
- Changer de thème (clair, sombre, fond transparent + dégradé) : pas de zone noire dans la barre.
- Rotation / tablette : barre centrée, largeur ≤ 480 dp ; navigation 3 boutons en paysage : padding
  latéral correct.
