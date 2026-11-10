package com.ninereader.ui.reader;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationExporter;
import com.ninereader.annotations.FormField;
import com.ninereader.data.ReadingStats;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.databinding.ActivityReaderBinding;
import com.ninereader.databinding.BarPlayerBinding;
import com.ninereader.databinding.BarSelectionBinding;
import com.ninereader.reader.ColumnDetector;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.ReaderTheme;
import com.ninereader.reader.ReadingMode;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.reader.TextSearch;
import com.ninereader.reader.tts.TtsController;
import com.ninereader.reader.view.PageAdapter;
import com.ninereader.reader.view.PageFrame;
import com.ninereader.reader.view.SpreadView;
import com.ninereader.reader.view.ZoomableRecyclerView;
import com.ninereader.ui.MainActivity;
import com.ninereader.ui.widget.ResumeWidgetProvider;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.FileUtils;
import com.ninereader.util.Prefs;
import com.ninereader.util.ShareUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.legere.pdfiumandroid.PdfDocument;

/**
 * The reader (§7.1 screen 3): continuous / paged / double page layouts, zoom, themes, immersive
 * bars, search, selection, bookmarks, side panel, annotations, forms, read-aloud and reading tools.
 */
public class ReaderActivity extends AppCompatActivity implements ReaderPanelFragment.Host, ZoomableRecyclerView.Listener {

    public static final String EXTRA_PAGE = "page";
    /** Action to run once the document is open: reflow, rsvp, tts, annotate, sign, redact, presentation. */
    public static final String EXTRA_ACTION = "action";
    private static final String STATE_ZOOM = "zoom";
    private static final String STATE_BARS = "bars";
    private static final String STATE_ANNOTATE = "annotate";
    private static final String STATE_PANEL = "panel";
    private static final String STATE_PRESENTATION = "presentation";
    private static final String STATE_EDIT = "edit";

    ActivityReaderBinding b;
    ReaderViewModel vm;
    Prefs prefs;
    @Nullable
    PageAdapter adapter;
    ReaderDecorator decorator;
    @Nullable
    AnnotationUi annotationUi;
    @Nullable
    EditModeUi editUi;
    private final PagerSnapHelper snapHelper = new PagerSnapHelper();
    private boolean snapAttached;
    private ReadingMode mode = ReadingMode.CONTINUOUS;
    private ReaderTheme theme = ReaderTheme.LIGHT;
    private boolean barsVisible = true;
    private boolean presentation;
    private boolean panelOpen;
    private boolean documentReady;
    private float restoreZoom = -1f;
    private boolean restoreAnnotate;
    private boolean restoreEdit;
    private int lastContainerW;
    private int lastContainerH;
    @Nullable
    private BarSelectionBinding selectionBar;
    @Nullable
    private BarPlayerBinding playerBar;
    private boolean autoScrolling;
    private long lastFrameNanos;
    private float autoScrollRemainder;
    @Nullable
    private TtsController.TextProvider ttsProvider;
    private int ttsHighlightedPage = -1;
    private int ttsHighlightedStart = -1;
    private boolean passwordDialogShown;
    private final Runnable hideIndicator = () -> b.pageIndicator.setVisibility(View.GONE);

    private final ActivityResultLauncher<String> createCopy = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), uri -> {
                if (uri != null) saveTo(uri);
            });
    private final ActivityResultLauncher<String> exportNotes = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/markdown"), this::writeExportedNotes);
    private String pendingExport;
    final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (annotationUi != null) annotationUi.onImagePicked(uri);
            });

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        b = ActivityReaderBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        prefs = NineReaderApp.from(this).prefs();
        vm = new ViewModelProvider(this).get(ReaderViewModel.class);
        decorator = new ReaderDecorator(getResources().getDisplayMetrics().density);
        decorator.painter().setInvalidator(() -> b.pages.invalidatePages());
        decorator.painter().setContext(this);
        if (state != null) {
            restoreZoom = state.getFloat(STATE_ZOOM, -1f);
            barsVisible = state.getBoolean(STATE_BARS, true);
            restoreAnnotate = state.getBoolean(STATE_ANNOTATE, false);
            restoreEdit = state.getBoolean(STATE_EDIT, false);
            panelOpen = state.getBoolean(STATE_PANEL, false);
            presentation = state.getBoolean(STATE_PRESENTATION, false);
        }
        setupInsets();
        setupToolbar();
        setupBottomBar();
        setupSearchBar();
        b.pages.setListener(this);
        b.topBar.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> b.pages.setContentTopInset(bt - t));
        b.pages.setItemAnimator(null);
        b.pages.setHasFixedSize(false);
        b.panelScrim.setOnClickListener(v -> closePanel());
        b.errorClose.setOnClickListener(v -> finish());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        vm.status().observe(this, this::onStatus);
        vm.wrongPassword().observe(this, wrong -> { });
        vm.open(uri, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri uri = intent.getData();
        if (uri != null && !uri.equals(vm.uri())) {
            // Another document: restart the reader for it.
            finish();
            startActivity(intent(this, uri));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putFloat(STATE_ZOOM, b.pages.getScale());
        out.putBoolean(STATE_BARS, barsVisible);
        out.putBoolean(STATE_ANNOTATE, annotationUi != null && annotationUi.isActive());
        out.putBoolean(STATE_PANEL, panelOpen);
        out.putBoolean(STATE_PRESENTATION, presentation);
        out.putBoolean(STATE_EDIT, editUi != null && editUi.isActive());
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.resumeSession();
        applyWindowPrefs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoScroll();
        if (documentReady) {
            vm.savePosition(b.pages.getScale());
            vm.flushSession();
            ResumeWidgetProvider.requestUpdate(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && ttsProvider != null) TtsController.detach(this, ttsProvider);
        super.onDestroy();
    }

    private void onStatus(ReaderViewModel.Status status) {
        b.loading.setVisibility(status == ReaderViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        b.errorView.setVisibility(status == ReaderViewModel.Status.ERROR ? View.VISIBLE : View.GONE);
        switch (status) {
            case PASSWORD:
                askPassword(Boolean.TRUE.equals(vm.wrongPassword().getValue()));
                break;
            case READY:
                setupDocument();
                break;
            case ERROR:
                documentReady = false;
                b.errorText.setText(getString(R.string.error_open));
                setBarsVisible(false, false);
                break;
            case LOADING:
                documentReady = false;
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ password (LEC-16)

    private void askPassword(boolean wrong) {
        if (passwordDialogShown) return;
        passwordDialogShown = true;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.password);
        FrameLayout box = padded(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.password_required)
                .setMessage(wrong ? R.string.password_wrong : R.string.password_message)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton(R.string.open, (d, w) -> {
                    passwordDialogShown = false;
                    Uri uri = vm.uri();
                    if (uri != null) vm.open(uri, input.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .show();
        input.requestFocus();
    }

    @NonNull
    FrameLayout padded(@NonNull View v) {
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(v);
        return box;
    }

    // ------------------------------------------------------------------ document setup

    private void setupDocument() {
        RenderScheduler scheduler = vm.scheduler();
        PdfEngine engine = vm.engine();
        if (scheduler == null || engine == null) return;
        documentReady = true;
        b.toolbar.setTitle(vm.displayName());
        adapter = new PageAdapter(scheduler);
        adapter.setPageLabelFormat(getString(R.string.page_cd_format));
        adapter.setDecorator(decorator);
        b.pages.setAdapter(adapter);
        theme = vm.theme() != null ? vm.theme() : ReaderTheme.fromKey(prefs.readerTheme(), isSystemDark());
        vm.setTheme(theme);
        applyTheme();
        b.pageSlider.setValueFrom(1);
        b.pageSlider.setValueTo(Math.max(2, engine.pageCount()));
        b.pageSlider.setEnabled(engine.pageCount() > 1);

        int startPage = vm.page();
        int extraPage = getIntent().getIntExtra(EXTRA_PAGE, -1);
        if (extraPage >= 0) {
            startPage = Math.min(engine.pageCount() - 1, extraPage);
            getIntent().removeExtra(EXTRA_PAGE);
        }
        vm.setCurrentPage(startPage);
        mode = presentation ? ReadingMode.PAGED : vm.effectiveMode(isTabletLandscape());
        applyMode(mode, startPage);
        float zoom = restoreZoom > 0 ? restoreZoom : vm.takeInitialZoom();
        restoreZoom = -1;
        if (zoom > 1.01f) b.pages.post(() -> b.pages.setScaleCentered(zoom));

        b.pages.clearOnScrollListeners();
        b.pages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateCurrentPageFromScroll();
                if (editUi != null) editUi.onViewportChanged();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_DRAGGING && autoScrolling) pauseAutoScroll();
            }
        });
        b.pages.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int w = r - l;
            int h = bt - t;
            if (w != lastContainerW || h != lastContainerH) configureAdapter(vm.page());
        });

        vm.currentPage().observe(this, this::onPageChanged);
        vm.bookmarks().observe(this, list -> updateBookmarkIcon());
        vm.search().observe(this, this::onSearchState);
        vm.selection().observe(this, this::onSelection);
        vm.crop().observe(this, crop -> {
            if (adapter != null) {
                int page = vm.page();
                adapter.setCrop(crop);
                b.pages.goToPosition(adapter.spreads().spreadOf(page), false);
            }
        });
        vm.sizesUpdated().observe(this, changed -> {
            if (Boolean.TRUE.equals(changed) && adapter != null) configureAdapter(vm.page());
        });
        vm.editor().fields().observe(this, fields -> {
            decorator.setFields(fields, true);
            b.pages.invalidatePages();
        });
        vm.editor().version().observe(this, v -> {
            b.pages.invalidatePages();
            invalidateOptionsMenu();
            if (annotationUi != null) annotationUi.refreshState();
        });
        TtsController.state().observe(this, this::onTtsState);
        TtsController.engineError().observe(this, err -> {
            if (Boolean.TRUE.equals(err)) Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_LONG).show();
        });

        if (annotationUi == null) {
            annotationUi = new AnnotationUi(this);
        }
        decorator.setStore(vm.editor().store(), annotationUi.controller());
        Uri docUri = vm.uri();
        if (docUri != null) vm.textEdits().bind(docUri, vm.password(), vm::engine);
        if (editUi == null) editUi = new EditModeUi(this);
        if (restoreEdit) {
            restoreEdit = false;
            editUi.enter();
        }
        if (restoreAnnotate) {
            restoreAnnotate = false;
            annotationUi.enter();
        }
        setupPanelContainer();
        if (panelOpen) openPanel();
        String action = getIntent().getStringExtra(EXTRA_ACTION);
        if (action != null) {
            getIntent().removeExtra(EXTRA_ACTION);
            b.pages.post(() -> runStartAction(action));
        }
        if (presentation) {
            enterPresentation();
        } else {
            setBarsVisible(barsVisible, false);
        }
        invalidateOptionsMenu();
    }

    private void runStartAction(@NonNull String action) {
        switch (action) {
            case "reflow":
                new ReflowDialogFragment().show(getSupportFragmentManager(), ReflowDialogFragment.TAG);
                break;
            case "rsvp":
                new RsvpDialogFragment().show(getSupportFragmentManager(), RsvpDialogFragment.TAG);
                break;
            case "tts":
                startTts(vm.page(), 0);
                break;
            case "presentation":
                enterPresentation();
                break;
            case "edit":
                if (editUi != null) editUi.enter();
                break;
            case "annotate":
            case "sign":
            case "redact":
                if (annotationUi != null) {
                    annotationUi.enter();
                    if ("redact".equals(action)) {
                        annotationUi.setTool(AnnotationController.Tool.REDACT);
                        Toast.makeText(this, R.string.redact_hint, Toast.LENGTH_LONG).show();
                    } else if ("sign".equals(action)) {
                        Toast.makeText(this, R.string.sign_hint, Toast.LENGTH_LONG).show();
                    }
                }
                break;
            default:
                break;
        }
    }

    private boolean isSystemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    boolean isTabletLandscape() {
        return getResources().getBoolean(R.bool.is_tablet)
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void configureAdapter(int keepPage) {
        if (adapter == null) return;
        int w = b.pages.getWidth();
        int h = b.pages.getHeight();
        if (w <= 0 || h <= 0) {
            b.pages.post(() -> configureAdapter(keepPage));
            return;
        }
        lastContainerW = w;
        lastContainerH = h;
        int gutter = mode == ReadingMode.DOUBLE ? Math.round(4 * getResources().getDisplayMetrics().density) : 0;
        adapter.configure(mode, w, h, gutter);
        b.pages.goToPosition(adapter.spreads().spreadOf(keepPage), false);
    }

    void applyMode(@NonNull ReadingMode newMode, int keepPage) {
        mode = newMode;
        boolean horizontal = newMode.isHorizontal();
        LinearLayoutManager lm = new LinearLayoutManager(this, horizontal ? RecyclerView.HORIZONTAL : RecyclerView.VERTICAL, false) {
            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extra) {
                // Lay out (and pre-render) the previous and next pages (§5.1).
                int space = horizontal ? getWidth() : getHeight() / 2;
                extra[0] = space;
                extra[1] = space;
            }
        };
        b.pages.setLayoutManager(lm);
        b.pages.setHorizontalMode(horizontal);
        while (b.pages.getItemDecorationCount() > 0) b.pages.removeItemDecorationAt(0);
        if (!horizontal) {
            int gap = getResources().getDimensionPixelSize(R.dimen.page_gap);
            b.pages.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                                           @NonNull RecyclerView.State state) {
                    outRect.set(0, 0, 0, gap);
                }
            });
        }
        if (horizontal && !snapAttached) {
            snapHelper.attachToRecyclerView(b.pages);
            snapAttached = true;
        } else if (!horizontal && snapAttached) {
            snapHelper.attachToRecyclerView(null);
            snapAttached = false;
        }
        lastContainerW = 0;
        configureAdapter(keepPage);
        int icon;
        switch (newMode) {
            case PAGED:
                icon = R.drawable.ic_view_paged;
                break;
            case DOUBLE:
                icon = R.drawable.ic_view_double;
                break;
            default:
                icon = R.drawable.ic_view_continuous;
                break;
        }
        b.btnModeIcon.setImageResource(icon);
    }

    void setReadingMode(@NonNull ReadingMode newMode) {
        if (newMode == mode) return;
        vm.setExplicitMode(newMode);
        if (newMode != ReadingMode.CONTINUOUS) stopAutoScroll();
        applyMode(newMode, vm.page());
    }

    void applyTheme() {
        int bg;
        switch (theme) {
            case DARK:
            case INVERTED:
                bg = ContextCompat.getColor(this, R.color.reader_bg_dark);
                break;
            case SEPIA:
                bg = ContextCompat.getColor(this, R.color.reader_bg_sepia);
                break;
            default:
                bg = ContextCompat.getColor(this, R.color.reader_bg_light);
                break;
        }
        if (presentation) bg = 0xFF000000;
        b.root.setBackgroundColor(bg);
        if (adapter != null) {
            adapter.setColorFilter(theme.colorFilter(), theme.apply(0xFFFFFFFF));
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    void setReaderTheme(@NonNull ReaderTheme t) {
        theme = t;
        vm.setTheme(t);
        prefs.setReaderTheme(t.key);
        applyTheme();
    }

    // ------------------------------------------------------------------ position

    private void updateCurrentPageFromScroll() {
        if (adapter == null) return;
        int pos = b.pages.centerPosition();
        if (pos < 0 || pos >= adapter.getItemCount()) return;
        int page = adapter.spreads().firstPage(pos);
        if (page != vm.page()) vm.setCurrentPage(page);
    }

    private void onPageChanged(int page) {
        int count = vm.pageCount();
        if (count <= 0) return;
        String label = getString(R.string.page_of, page + 1, count);
        b.pageLabel.setText(label);
        float v = Math.max(1, Math.min(count, page + 1));
        if (b.pageSlider.getValueTo() >= v && Math.round(b.pageSlider.getValue()) != Math.round(v)) b.pageSlider.setValue(v);
        updateBookmarkIcon();
        if (editUi != null) editUi.onPageVisible(page);
        if (!barsVisible) {
            b.pageIndicator.setText(label);
            b.pageIndicator.setVisibility(View.VISIBLE);
            b.pageIndicator.removeCallbacks(hideIndicator);
            b.pageIndicator.postDelayed(hideIndicator, 1500);
        }
    }

    @Override
    public void goToPage(int page) {
        if (adapter == null) return;
        int p = Math.max(0, Math.min(vm.pageCount() - 1, page));
        vm.setCurrentPage(p);
        b.pages.goToPosition(adapter.spreads().spreadOf(p), false);
        if (panelOpen && !isPanelDocked()) closePanel();
    }

    void nextPage(boolean forward) {
        if (adapter == null) return;
        if (!mode.isHorizontal()) {
            b.pages.scrollScreen(forward);
            return;
        }
        int spread = adapter.spreads().spreadOf(vm.page()) + (forward ? 1 : -1);
        if (spread < 0 || spread >= adapter.getItemCount()) return;
        b.pages.goToPosition(spread, true);
        vm.setCurrentPage(adapter.spreads().firstPage(spread));
    }

    // ------------------------------------------------------------------ bars & insets

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            b.topBar.setPadding(bars.left, bars.top, bars.right, 0);
            b.bottomArea.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            b.panelOverlay.setPadding(bars.left, bars.top, 0, bars.bottom);
            b.panelDocked.setPadding(bars.left, bars.top, 0, bars.bottom);
            return insets;
        });
    }

    void setBarsVisible(boolean visible, boolean animate) {
        barsVisible = visible;
        WindowInsetsControllerCompat ctl = WindowCompat.getInsetsController(getWindow(), b.root);
        ctl.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (visible) {
            ctl.show(WindowInsetsCompat.Type.systemBars());
        } else {
            ctl.hide(WindowInsetsCompat.Type.systemBars());
        }
        ctl.setAppearanceLightStatusBars(!isSystemDark());
        animateBar(b.topBar, visible, true, animate);
        boolean modeActive = (annotationUi != null && annotationUi.isActive()) || (editUi != null && editUi.isActive());
        animateBar(b.bottomBar, visible && !modeActive, false, animate);
    }

    private void animateBar(View bar, boolean show, boolean top, boolean animate) {
        bar.animate().cancel();
        if (show) {
            bar.setVisibility(View.VISIBLE);
            if (animate) {
                bar.animate().translationY(0).alpha(1f).setDuration(180).start();
            } else {
                bar.setTranslationY(0);
                bar.setAlpha(1f);
            }
        } else {
            int h = Math.max(bar.getHeight(), 200);
            if (animate) {
                bar.animate().translationY(top ? -h : h).alpha(0f).setDuration(180)
                        .withEndAction(() -> bar.setVisibility(View.GONE)).start();
            } else {
                bar.setVisibility(View.GONE);
            }
        }
    }

    private void applyWindowPrefs() {
        if (prefs.keepScreenOn() || presentation) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        int brightness = prefs.brightness();
        lp.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : Math.max(0.01f, brightness / 100f);
        getWindow().setAttributes(lp);
        int blue = prefs.blueFilter();
        if (blue > 0) {
            int alpha = Math.round(blue / 100f * 160);
            b.filterOverlay.setBackgroundColor((alpha << 24) | 0x00FF8F00);
            b.filterOverlay.setVisibility(View.VISIBLE);
        } else {
            b.filterOverlay.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ toolbar & menu

    private void setupToolbar() {
        b.toolbar.setNavigationOnClickListener(v -> onBack());
        b.toolbar.inflateMenu(R.menu.reader_toolbar);
        b.toolbar.setOnMenuItemClickListener(this::onMenuItem);
    }

    @Override
    public void invalidateOptionsMenu() {
        super.invalidateOptionsMenu();
        if (b == null) return;
        Menu m = b.toolbar.getMenu();
        MenuItem save = m.findItem(R.id.action_save);
        if (save != null) save.setVisible(documentReady && vm.editor().hasPendingChanges());
        updateBookmarkIcon();
    }

    private void updateBookmarkIcon() {
        MenuItem item = b.toolbar.getMenu().findItem(R.id.action_bookmark);
        if (item == null) return;
        boolean marked = vm.isBookmarked(vm.page());
        item.setIcon(marked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        item.setTitle(marked ? R.string.bookmark_remove : R.string.bookmark_add);
    }

    private boolean onMenuItem(MenuItem item) {
        if (!documentReady) return false;
        int id = item.getItemId();
        if (id == R.id.action_search) {
            openSearch();
        } else if (id == R.id.action_bookmark) {
            vm.toggleBookmark(vm.page(), getString(R.string.page_n, vm.page() + 1));
        } else if (id == R.id.action_save) {
            askSave(false);
        } else if (id == R.id.action_go_to) {
            askGoToPage();
        } else if (id == R.id.action_edit_text) {
            if (editUi != null) editUi.enter();
        } else if (id == R.id.action_reflow) {
            new ReflowDialogFragment().show(getSupportFragmentManager(), ReflowDialogFragment.TAG);
        } else if (id == R.id.action_rsvp) {
            new RsvpDialogFragment().show(getSupportFragmentManager(), RsvpDialogFragment.TAG);
        } else if (id == R.id.action_autoscroll) {
            startAutoScroll();
        } else if (id == R.id.action_presentation) {
            enterPresentation();
        } else if (id == R.id.action_share_page) {
            sharePage();
        } else if (id == R.id.action_save_copy) {
            createCopy.launch(FileUtils.baseName(vm.displayName()) + "_annote.pdf");
        } else if (id == R.id.action_organize) {
            Uri uri = vm.uri();
            if (uri != null) {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra(MainActivity.EXTRA_TOOL, "organize")
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        } else if (id == R.id.action_share_file) {
            Uri uri = vm.uri();
            if (uri != null) ShareUtils.shareStream(this, uri, "application/pdf", getString(R.string.share_file));
        } else if (id == R.id.action_info) {
            showInfo();
        } else {
            return false;
        }
        return true;
    }

    private void askGoToPage() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.page_range_hint, vm.pageCount()));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.go_to_page)
                .setView(padded(input))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        goToPage(Integer.parseInt(input.getText().toString().trim()) - 1);
                    } catch (NumberFormatException ignored) {
                        // invalid input: ignore
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private void showInfo() {
        DocumentEntity doc = vm.document();
        vm.statsAsync(stats -> {
            String h = getString(R.string.unit_hour);
            String m = getString(R.string.unit_minute);
            String lt = getString(R.string.less_than_minute);
            String size = doc == null ? "?" : FileUtils.formatSize(doc.size, getResources().getConfiguration().getLocales().get(0));
            String msg = getString(R.string.document_info_body,
                    vm.displayName(), vm.pageCount(), size,
                    ReadingStats.formatDuration(stats.totalTimeMs, h, m, lt),
                    stats.pagesRead,
                    ReadingStats.formatDuration(stats.estimatedRemainingMs(), h, m, lt));
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.document_info)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    // ------------------------------------------------------------------ bottom bar

    private void setupBottomBar() {
        b.pageSlider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) {
                int page = Math.round(value) - 1;
                b.pageLabel.setText(getString(R.string.page_of, page + 1, vm.pageCount()));
            }
        });
        b.pageSlider.addOnSliderTouchListener(new com.google.android.material.slider.Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull com.google.android.material.slider.Slider slider) {
                // nothing
            }

            @Override
            public void onStopTrackingTouch(@NonNull com.google.android.material.slider.Slider slider) {
                goToPage(Math.round(slider.getValue()) - 1);
            }
        });
        b.pageLabel.setOnClickListener(v -> askGoToPage());
        b.btnPanel.setOnClickListener(v -> togglePanel());
        b.btnAnnotate.setOnClickListener(v -> {
            if (annotationUi != null) annotationUi.enter();
        });
        b.btnEdit.setOnClickListener(v -> {
            if (editUi != null) editUi.enter();
        });
        b.btnDisplay.setOnClickListener(v -> new DisplaySettingsSheet(this).show());
        b.btnMode.setOnClickListener(v -> {
            ReadingMode next;
            switch (mode) {
                case CONTINUOUS:
                    next = ReadingMode.PAGED;
                    break;
                case PAGED:
                    next = ReadingMode.DOUBLE;
                    break;
                default:
                    next = ReadingMode.CONTINUOUS;
                    break;
            }
            setReadingMode(next);
            Toast.makeText(this, modeLabel(next), Toast.LENGTH_SHORT).show();
        });
        b.btnTts.setOnClickListener(v -> startTts(vm.page(), 0));
    }

    @NonNull
    String modeLabel(ReadingMode m) {
        switch (m) {
            case PAGED:
                return getString(R.string.mode_paged);
            case DOUBLE:
                return getString(R.string.mode_double);
            default:
                return getString(R.string.mode_continuous);
        }
    }

    @NonNull
    ReadingMode currentMode() {
        return mode;
    }

    @NonNull
    ReaderTheme currentTheme() {
        return theme;
    }

    // ------------------------------------------------------------------ side panel

    private boolean isPanelDocked() {
        return getResources().getBoolean(R.bool.anchored_side_panel);
    }

    private void setupPanelContainer() {
        Fragment existing = getSupportFragmentManager().findFragmentByTag(ReaderPanelFragment.TAG);
        int containerId = isPanelDocked() ? R.id.panel_docked : R.id.panel_overlay;
        if (existing != null && existing.getId() != containerId) {
            getSupportFragmentManager().beginTransaction().remove(existing).commitNow();
        }
    }

    private void togglePanel() {
        if (panelOpen) {
            closePanel();
        } else {
            openPanel();
        }
    }

    private void openPanel() {
        panelOpen = true;
        boolean docked = isPanelDocked();
        int containerId = docked ? R.id.panel_docked : R.id.panel_overlay;
        if (getSupportFragmentManager().findFragmentByTag(ReaderPanelFragment.TAG) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(containerId, new ReaderPanelFragment(), ReaderPanelFragment.TAG)
                    .commitNow();
        }
        if (docked) {
            b.panelDocked.setVisibility(View.VISIBLE);
        } else {
            b.panelOverlay.setVisibility(View.VISIBLE);
            b.panelScrim.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void closePanel() {
        panelOpen = false;
        b.panelDocked.setVisibility(View.GONE);
        b.panelOverlay.setVisibility(View.GONE);
        b.panelScrim.setVisibility(View.GONE);
    }

    @Override
    public void onPendingAnnotationClicked(@NonNull Annotation annotation) {
        goToPage(annotation.page);
        if (annotationUi != null) {
            annotationUi.enter();
            annotationUi.selectAnnotation(annotation);
        }
    }

    @Override
    public void exportAnnotations(boolean markdown) {
        List<AnnotationExporter.Entry> entries = new ArrayList<>();
        ReaderPanelFragment panel = (ReaderPanelFragment) getSupportFragmentManager().findFragmentByTag(ReaderPanelFragment.TAG);
        for (Annotation a : vm.editor().store().all()) {
            String label = panel != null ? panel.labelFor(a) : a.type.name();
            entries.add(new AnnotationExporter.Entry(a.page, label, a.text == null ? "" : a.text));
        }
        List<AnnotationExporter.Entry> existing = vm.editor().existingAnnotations().getValue();
        if (existing != null) entries.addAll(existing);
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.annotations_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String title = vm.displayName();
        String pageWord = getString(R.string.page_word);
        String text = markdown ? AnnotationExporter.toMarkdown(title, entries, pageWord)
                : AnnotationExporter.toText(title, entries, pageWord);
        new MaterialAlertDialogBuilder(this)
                .setTitle(markdown ? R.string.export_markdown : R.string.export_text)
                .setItems(new CharSequence[]{getString(R.string.share), getString(R.string.save_as_file)}, (d, which) -> {
                    if (which == 0) {
                        ShareUtils.shareText(this, text, getString(R.string.share));
                    } else {
                        pendingExport = text;
                        exportNotes.launch(FileUtils.baseName(title) + (markdown ? "_notes.md" : "_notes.txt"));
                    }
                })
                .show();
    }

    private void writeExportedNotes(@Nullable Uri uri) {
        String text = pendingExport;
        pendingExport = null;
        if (uri == null || text == null) return;
        AppExecutors.get().diskIo().execute(() -> {
            boolean ok;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("null stream");
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                ok = true;
            } catch (IOException | SecurityException e) {
                ok = false;
            }
            boolean success = ok;
            AppExecutors.get().mainThread().execute(() -> Toast.makeText(this,
                    success ? R.string.saved : R.string.error_generic, Toast.LENGTH_SHORT).show());
        });
    }

    // ------------------------------------------------------------------ gestures

    @Override
    public void onSingleTap(@Nullable ZoomableRecyclerView.Hit hit, float x, float y) {
        if (editUi != null && editUi.isActive()) {
            editUi.onTap(hit);
            return;
        }
        if (vm.selection().getValue() != null) {
            vm.clearSelection();
            return;
        }
        if (autoScrolling) {
            pauseAutoScroll();
            return;
        }
        if (presentation) {
            float w = b.pages.getWidth();
            if (x < w * 0.3f) {
                nextPage(false);
            } else {
                nextPage(true);
            }
            return;
        }
        if (hit == null || !hit.hasPagePoint) {
            handleTapZones(x);
            return;
        }
        FormField field = decorator.fieldAt(hit.frame.page, hit.pageX, hit.pageY);
        if (field != null && !field.readOnly) {
            FormFieldEditor.edit(this, vm.editor().store(), field,
                    decorator.widgetAt(field, hit.frame.page, hit.pageX, hit.pageY));
            return;
        }
        vm.linkAt(hit.frame.page, hit.pageX, hit.pageY, link -> {
            if (link != null) {
                openLink(link);
            } else {
                handleTapZones(x);
            }
        });
    }

    private void handleTapZones(float x) {
        float w = b.pages.getWidth();
        if (prefs.tapZones() && b.pages.getScale() <= 1.01f) {
            if (x < w * 0.25f) {
                nextPage(false);
                return;
            }
            if (x > w * 0.75f) {
                nextPage(true);
                return;
            }
        }
        setBarsVisible(!barsVisible, true);
    }

    private void openLink(@NonNull PdfDocument.Link link) {
        Integer dest = link.getDestPageIdx();
        if (dest != null && dest >= 0) {
            goToPage(dest);
            return;
        }
        String uri = link.getUri();
        if (uri == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open_link)
                .setMessage(uri)
                .setPositiveButton(R.string.open, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onDoubleTap(@Nullable ZoomableRecyclerView.Hit hit, float x, float y) {
        if (b.pages.getScale() > 1.05f || hit == null || mode.isHorizontal() && mode == ReadingMode.DOUBLE) return false;
        // Column zoom (RAP-05): fit the text column under the finger to the screen width.
        SpreadView view = hit.view;
        PageFrame frame = hit.frame;
        float spreadX = hit.x;
        vm.pageLineRects(frame.page, rects -> {
            List<RectF> lines = new ArrayList<>();
            RectF out = new RectF();
            for (RectF r : rects) {
                if (frame.pageToView(new RectF(r), out)) lines.add(new RectF(out));
            }
            float[] col = ColumnDetector.columnAt(lines, spreadX, frame.slot.width());
            if (col != null && view.isAttachedToWindow()) {
                b.pages.zoomToColumn(view, col[0], col[1], y);
            } else {
                b.pages.animateScale(ZoomableRecyclerView.DOUBLE_TAP_SCALE, x, y);
            }
        });
        return true;
    }

    @Override
    public void onLongPress(@NonNull ZoomableRecyclerView.Hit hit) {
        if (editUi != null && editUi.isActive()) {
            editUi.onLongPress(hit);
            return;
        }
        if (!hit.hasPagePoint) return;
        float tolerance = 8f / Math.max(0.1f, hit.frame.pageUnitToPx() * b.pages.getScale());
        vm.beginSelection(hit.frame.page, hit.pageX, hit.pageY, tolerance, started -> {
            if (Boolean.TRUE.equals(started)) b.pages.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        });
    }

    @Override
    public void onLongPressDrag(@Nullable ZoomableRecyclerView.Hit hit) {
        if (editUi != null && editUi.isActive()) {
            editUi.onLongPressDrag(hit);
            return;
        }
        if (hit == null || !hit.hasPagePoint) return;
        float tolerance = 12f / Math.max(0.1f, hit.frame.pageUnitToPx() * b.pages.getScale());
        vm.extendSelection(hit.frame.page, hit.pageX, hit.pageY, tolerance);
    }

    @Override
    public void onLongPressEnd() {
        if (editUi != null && editUi.isActive()) {
            editUi.onLongPressEnd();
            return;
        }
        // Selection stays until dismissed; the selection bar offers the actions.
    }

    @Override
    public void onZoomChanged(float scale) {
        if (editUi != null) b.pages.post(editUi::onViewportChanged);
    }

    // ------------------------------------------------------------------ selection (LEC-11)

    private void onSelection(@Nullable TextSelection sel) {
        decorator.setSelection(sel);
        b.pages.invalidatePages();
        if (annotationUi != null && annotationUi.isActive()) return;
        if (sel == null || sel.length() == 0) {
            hideSelectionBar();
            return;
        }
        showSelectionBar();
    }

    private void showSelectionBar() {
        if (selectionBar == null) {
            selectionBar = BarSelectionBinding.inflate(LayoutInflater.from(this), b.contextualBar, false);
            selectionBar.selCopy.setOnClickListener(v -> withSelection(sel -> {
                ShareUtils.copyToClipboard(this, vm.displayName(), TextSelection.clean(sel.text));
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                vm.clearSelection();
            }));
            selectionBar.selShare.setOnClickListener(v -> withSelection(sel ->
                    ShareUtils.shareText(this, TextSelection.clean(sel.text), getString(R.string.share))));
            selectionBar.selHighlight.setOnClickListener(v -> markupFromSelection(Annotation.Type.HIGHLIGHT));
            selectionBar.selUnderline.setOnClickListener(v -> markupFromSelection(Annotation.Type.UNDERLINE));
            selectionBar.selStrike.setOnClickListener(v -> markupFromSelection(Annotation.Type.STRIKEOUT));
            selectionBar.selSpeak.setOnClickListener(v -> withSelection(sel -> {
                startTts(sel.page, sel.start);
                vm.clearSelection();
            }));
            selectionBar.selDefine.setOnClickListener(v -> withSelection(sel -> processText(TextSelection.clean(sel.text))));
            selectionBar.selClose.setOnClickListener(v -> vm.clearSelection());
        }
        if (selectionBar.getRoot().getParent() == null) {
            b.contextualBar.removeAllViews();
            b.contextualBar.addView(selectionBar.getRoot());
        }
    }

    private void hideSelectionBar() {
        if (selectionBar != null && selectionBar.getRoot().getParent() != null) {
            b.contextualBar.removeView(selectionBar.getRoot());
        }
    }

    private void withSelection(@NonNull java.util.function.Consumer<TextSelection> action) {
        TextSelection sel = vm.selection().getValue();
        if (sel != null && sel.length() > 0) action.accept(sel);
    }

    /** Creates a text markup annotation (ANN-01) from the current selection. */
    void markupFromSelection(@NonNull Annotation.Type type) {
        TextSelection sel = vm.selection().getValue();
        if (sel == null || sel.rects.isEmpty()) return;
        Annotation a = new Annotation();
        a.type = type;
        a.page = sel.page;
        a.color = type == Annotation.Type.HIGHLIGHT ? prefs.highlightColor()
                : (annotationUi != null ? annotationUi.markupColor(type) : 0xFFE53935);
        for (RectF r : sel.rects) {
            a.rects.add(new float[]{Math.min(r.left, r.right), Math.min(r.top, r.bottom),
                    Math.max(r.left, r.right), Math.max(r.top, r.bottom)});
        }
        a.text = TextSelection.clean(sel.text);
        vm.editor().store().add(a);
        vm.clearSelection();
    }

    /** Offline dictionary / translation through installed apps (RAP-10, RAP-11). */
    private void processText(@NonNull String text) {
        Intent intent = new Intent(Intent.ACTION_PROCESS_TEXT)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.define_translate)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_dictionary_app, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ search (LEC-07)

    private void setupSearchBar() {
        b.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                vm.search(b.searchInput.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });
        b.searchNext.setOnClickListener(v -> vm.searchStep(1));
        b.searchPrev.setOnClickListener(v -> vm.searchStep(-1));
        b.searchClose.setOnClickListener(v -> closeSearch());
        b.searchResults.setOnClickListener(v -> showSearchResults());
    }

    void openSearch() {
        setBarsVisible(true, true);
        b.searchBar.setVisibility(View.VISIBLE);
        b.searchInput.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(b.searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        b.searchBar.setVisibility(View.GONE);
        vm.clearSearch();
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(b.searchInput.getWindowToken(), 0);
    }

    private void onSearchState(@NonNull SearchState s) {
        if (s.query.isEmpty()) {
            b.searchCount.setText("");
            decorator.clearSearch();
            b.pages.invalidatePages();
            return;
        }
        if (s.results.isEmpty()) {
            b.searchCount.setText(s.finished ? getString(R.string.search_none) : "…");
        } else {
            b.searchCount.setText(getString(R.string.search_count, s.current + 1, s.results.size())
                    + (s.finished ? "" : "…"));
        }
        TextSearch.Match m = s.currentMatch();
        if (m == null) {
            decorator.clearSearch();
            b.pages.invalidatePages();
            return;
        }
        vm.matchRects(m, rects -> {
            decorator.setSearchHit(m.page, rects);
            b.pages.invalidatePages();
        });
        if (adapter != null && adapter.spreads().spreadOf(m.page) != adapter.spreads().spreadOf(vm.page())) goToPage(m.page);
    }

    private void showSearchResults() {
        SearchState s = vm.search().getValue();
        if (s == null || s.results.isEmpty()) return;
        int max = Math.min(300, s.results.size());
        CharSequence[] items = new CharSequence[max];
        for (int i = 0; i < max; i++) {
            TextSearch.Match m = s.results.get(i);
            items[i] = getString(R.string.page_n, m.page + 1) + " — " + m.excerpt;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.search_count_total, s.results.size()))
                .setItems(items, (d, which) -> vm.selectSearchResult(which))
                .show();
    }

    // ------------------------------------------------------------------ read aloud (LEC-13)

    void startTts(int page, int fromChar) {
        PdfEngine engine = vm.engine();
        if (engine == null) return;
        if (ttsProvider == null) {
            String title = vm.displayName();
            ttsProvider = new TtsController.TextProvider() {
                @Override
                public int pageCount() {
                    PdfEngine e = vm.engine();
                    return e == null ? 0 : e.pageCount();
                }

                @NonNull
                @Override
                public String pageText(int p) {
                    PdfEngine e = vm.engine();
                    return e == null || e.isClosed() ? "" : e.pageText(p);
                }

                @NonNull
                @Override
                public String title() {
                    return title;
                }
            };
        }
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        TtsController.start(this, ttsProvider, page, fromChar, prefs.ttsRate());
    }

    private void onTtsState(@NonNull TtsController.State s) {
        if (!s.isActive()) {
            decorator.setTts(-1, new ArrayList<>());
            ttsHighlightedPage = -1;
            ttsHighlightedStart = -1;
            b.pages.invalidatePages();
            if (playerBar != null && !autoScrolling) hidePlayerBar();
            return;
        }
        showPlayerBar(false);
        if (playerBar != null) {
            boolean playing = s.status == TtsController.State.PLAYING;
            playerBar.playerToggle.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            playerBar.playerToggle.setContentDescription(getString(playing ? R.string.tts_pause : R.string.tts_resume));
            playerBar.playerLabel.setText(getString(R.string.page_n, s.page + 1));
        }
        if (s.page == ttsHighlightedPage && s.start == ttsHighlightedStart) return;
        ttsHighlightedPage = s.page;
        ttsHighlightedStart = s.start;
        if (s.end > s.start) {
            vm.matchRects(new TextSearch.Match(s.page, s.start, s.end - s.start, ""), rects -> {
                decorator.setTts(s.page, rects);
                b.pages.invalidatePages();
            });
        }
        if (adapter != null && adapter.spreads().spreadOf(s.page) != adapter.spreads().spreadOf(vm.page())) goToPage(s.page);
    }

    private void showPlayerBar(boolean forAutoScroll) {
        if (playerBar == null) {
            playerBar = BarPlayerBinding.inflate(LayoutInflater.from(this), b.contextualBar, false);
        }
        if (playerBar.getRoot().getParent() == null) {
            hideSelectionBar();
            b.contextualBar.addView(playerBar.getRoot());
        }
        if (forAutoScroll) {
            playerBar.playerLabel.setText(R.string.autoscroll);
            playerBar.playerSpeed.setValueFrom(10);
            playerBar.playerSpeed.setValueTo(200);
            playerBar.playerSpeed.setStepSize(5);
            playerBar.playerSpeed.setValue(Math.max(10, Math.min(200, Math.round(prefs.autoScrollSpeed() / 5f) * 5)));
            playerBar.playerSpeed.clearOnChangeListeners();
            playerBar.playerSpeed.addOnChangeListener((sl, v, user) -> prefs.setAutoScrollSpeed(Math.round(v)));
            playerBar.playerToggle.setOnClickListener(v -> {
                if (autoScrolling) {
                    pauseAutoScroll();
                } else {
                    resumeAutoScroll();
                }
            });
            playerBar.playerClose.setOnClickListener(v -> stopAutoScroll());
        } else {
            playerBar.playerSpeed.setValueFrom(50);
            playerBar.playerSpeed.setValueTo(250);
            playerBar.playerSpeed.setStepSize(10);
            playerBar.playerSpeed.setValue(Math.max(50, Math.min(250, Math.round(prefs.ttsRate() * 10) * 10)));
            playerBar.playerSpeed.clearOnChangeListeners();
            playerBar.playerSpeed.addOnChangeListener((sl, v, user) -> {
                if (!user) return;
                float rate = v / 100f;
                prefs.setTtsRate(rate);
                TtsController.setRate(this, rate);
            });
            playerBar.playerToggle.setOnClickListener(v -> {
                TtsController.State st = TtsController.state().getValue();
                if (st != null && st.status == TtsController.State.PLAYING) {
                    TtsController.pause(this);
                } else {
                    TtsController.resume(this);
                }
            });
            playerBar.playerClose.setOnClickListener(v -> TtsController.stop(this));
        }
    }

    private void hidePlayerBar() {
        if (playerBar != null && playerBar.getRoot().getParent() != null) b.contextualBar.removeView(playerBar.getRoot());
    }

    // ------------------------------------------------------------------ auto-scroll (RAP-06)

    private final Choreographer.FrameCallback autoScrollFrame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!autoScrolling) return;
            if (lastFrameNanos != 0) {
                float dt = (now - lastFrameNanos) / 1e9f;
                float px = prefs.autoScrollSpeed() * getResources().getDisplayMetrics().density * dt / b.pages.getScale();
                autoScrollRemainder += px;
                int step = (int) autoScrollRemainder;
                if (step != 0) {
                    autoScrollRemainder -= step;
                    if (!b.pages.canScrollVertically(1)) {
                        pauseAutoScroll();
                        return;
                    }
                    b.pages.scrollBy(0, step);
                }
            }
            lastFrameNanos = now;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private void startAutoScroll() {
        if (mode != ReadingMode.CONTINUOUS) setReadingMode(ReadingMode.CONTINUOUS);
        TtsController.stop(this);
        setBarsVisible(false, true);
        showPlayerBar(true);
        resumeAutoScroll();
    }

    private void resumeAutoScroll() {
        autoScrolling = true;
        lastFrameNanos = 0;
        if (playerBar != null) {
            playerBar.playerToggle.setImageResource(R.drawable.ic_pause);
            playerBar.playerToggle.setContentDescription(getString(R.string.tts_pause));
        }
        Choreographer.getInstance().postFrameCallback(autoScrollFrame);
    }

    private void pauseAutoScroll() {
        autoScrolling = false;
        Choreographer.getInstance().removeFrameCallback(autoScrollFrame);
        if (playerBar != null) {
            playerBar.playerToggle.setImageResource(R.drawable.ic_play);
            playerBar.playerToggle.setContentDescription(getString(R.string.tts_resume));
            playerBar.playerToggle.setOnClickListener(v -> resumeAutoScroll());
        }
    }

    private void stopAutoScroll() {
        boolean wasShowing = autoScrolling || (playerBar != null && playerBar.getRoot().getParent() != null
                && getString(R.string.autoscroll).contentEquals(playerBar.playerLabel.getText()));
        autoScrolling = false;
        Choreographer.getInstance().removeFrameCallback(autoScrollFrame);
        if (wasShowing) hidePlayerBar();
    }

    // ------------------------------------------------------------------ presentation (RAP-08)

    private void enterPresentation() {
        presentation = true;
        stopAutoScroll();
        if (vm.isCropEnabled()) vm.setCropEnabled(false);
        if (mode != ReadingMode.PAGED) applyMode(ReadingMode.PAGED, vm.page());
        setBarsVisible(false, true);
        applyTheme();
        applyWindowPrefs();
        Toast.makeText(this, R.string.presentation_hint, Toast.LENGTH_SHORT).show();
    }

    private void exitPresentation() {
        presentation = false;
        applyMode(vm.effectiveMode(isTabletLandscape()), vm.page());
        applyTheme();
        applyWindowPrefs();
        setBarsVisible(true, true);
    }

    // ------------------------------------------------------------------ share page (ANN-08)

    private void sharePage() {
        PdfEngine engine = vm.engine();
        RenderScheduler sch = vm.scheduler();
        if (engine == null || sch == null) return;
        int page = vm.page();
        List<Annotation> pending = vm.editor().store().forPage(page);
        sch.post(RenderScheduler.PRIORITY_VISIBLE, () -> {
            float w = engine.pageWidth(page);
            float h = engine.pageHeight(page);
            int bw = 1600;
            int bh = Math.max(1, Math.round(bw * h / w));
            Bitmap bmp;
            try {
                bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                return;
            }
            engine.render(page, bmp, 0, 0, bw, bh, true);
            PageFrame frame = new PageFrame();
            frame.page = page;
            frame.slot.set(0, 0, bw, bh);
            frame.fullW = bw;
            frame.fullH = bh;
            frame.transform = engine.transform(page);
            AppExecutors.get().mainThread().execute(() -> {
                Canvas c = new Canvas(bmp);
                for (Annotation a : pending) decorator.painter().draw(c, frame, a, false);
                try {
                    Uri uri = ShareUtils.pngForSharing(this, bmp, FileUtils.baseName(vm.displayName()) + "_p" + (page + 1));
                    ShareUtils.shareStream(this, uri, "image/png", getString(R.string.share_page));
                } catch (IOException e) {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ------------------------------------------------------------------ saving (ANN-07)

    /** Asks where to save pending changes; never overwrites silently (§5.4). */
    void askSave(boolean thenFinish) {
        if (editUi != null) editUi.commit();
        if (!vm.editor().hasPendingChanges()) {
            if (thenFinish) finish();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_changes_title)
                .setMessage(R.string.save_changes_message)
                .setPositiveButton(R.string.save_in_file, (d, w) -> saveTo(null))
                .setNeutralButton(R.string.save_copy, (d, w) ->
                        createCopy.launch(FileUtils.baseName(vm.displayName()) + "_annote.pdf"));
        if (thenFinish) {
            builder.setNegativeButton(R.string.keep_draft, (d, w) -> finish());
        } else {
            builder.setNegativeButton(android.R.string.cancel, null);
        }
        builder.show();
    }

    private void saveTo(@Nullable Uri copy) {
        Uri source = vm.uri();
        if (source == null) return;
        Snackbar progress = Snackbar.make(b.root, R.string.saving, Snackbar.LENGTH_INDEFINITE);
        progress.show();
        vm.editor().save(source, vm.password(), copy, () -> {
            // The original is about to be replaced: release it first.
            documentReady = false;
            b.pages.setAdapter(null);
            adapter = null;
            vm.closeDocument();
        }, result -> {
            progress.dismiss();
            if (result.success) {
                Toast.makeText(this, result.overwritten ? R.string.saved : R.string.copy_saved, Toast.LENGTH_SHORT).show();
                if (result.failures > 0) {
                    Toast.makeText(this, getString(R.string.save_partial, result.failures), Toast.LENGTH_LONG).show();
                }
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.save_failed)
                        .setMessage(result.overwritten ? R.string.save_failed_overwrite : R.string.save_failed_message)
                        .setPositiveButton(R.string.save_copy, (d, w) ->
                                createCopy.launch(FileUtils.baseName(vm.displayName()) + "_annote.pdf"))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
            if (result.overwritten) vm.reload();
            invalidateOptionsMenu();
        });
    }

    // ------------------------------------------------------------------ back & keys

    private void onBack() {
        if (presentation) {
            exitPresentation();
        } else if (vm.selection().getValue() != null) {
            vm.clearSelection();
        } else if (b.searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else if (panelOpen && !isPanelDocked()) {
            closePanel();
        } else if (editUi != null && editUi.isActive()) {
            editUi.exit();
        } else if (annotationUi != null && annotationUi.isActive()) {
            annotationUi.exit();
        } else if (autoScrolling) {
            stopAutoScroll();
        } else if (documentReady && vm.editor().hasPendingChanges()) {
            askSave(true);
        } else {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!documentReady) return super.onKeyDown(keyCode, event);
        boolean ctrl = event.isCtrlPressed();
        if (ctrl) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_F:
                    openSearch();
                    return true;
                case KeyEvent.KEYCODE_Z:
                    if (event.isShiftPressed()) {
                        vm.editor().store().redo();
                    } else {
                        vm.editor().store().undo();
                    }
                    return true;
                case KeyEvent.KEYCODE_Y:
                    vm.editor().store().redo();
                    return true;
                case KeyEvent.KEYCODE_S:
                    askSave(false);
                    return true;
                case KeyEvent.KEYCODE_EQUALS:
                case KeyEvent.KEYCODE_PLUS:
                case KeyEvent.KEYCODE_NUMPAD_ADD:
                    b.pages.zoomBy(1.25f);
                    return true;
                case KeyEvent.KEYCODE_MINUS:
                case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                    b.pages.zoomBy(0.8f);
                    return true;
                case KeyEvent.KEYCODE_0:
                    b.pages.resetZoom();
                    return true;
                default:
                    break;
            }
        }
        if (b.searchInput.hasFocus() || b.inlineEditor.hasFocus()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (prefs.volumeKeys()) {
                    nextPage(true);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (prefs.volumeKeys()) {
                    nextPage(false);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_SPACE:
                nextPage(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_PAGE_UP:
                nextPage(false);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!mode.isHorizontal()) {
                    b.pages.smoothScrollBy(0, Math.round(80 * getResources().getDisplayMetrics().density));
                    return true;
                }
                nextPage(true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!mode.isHorizontal()) {
                    b.pages.smoothScrollBy(0, -Math.round(80 * getResources().getDisplayMetrics().density));
                    return true;
                }
                nextPage(false);
                return true;
            case KeyEvent.KEYCODE_MOVE_HOME:
                goToPage(0);
                return true;
            case KeyEvent.KEYCODE_MOVE_END:
                goToPage(vm.pageCount() - 1);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                onBack();
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (prefs.volumeKeys() && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Opens a document in the reader. */
    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull Uri uri) {
        return new Intent(context, ReaderActivity.class)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
}
