package com.ninereader.ui.compare;

import android.app.Application;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.ninereader.ui.theme.ThemedActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ninereader.R;
import com.ninereader.databinding.ActivityCompareBinding;
import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.ReadingMode;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.reader.view.PageAdapter;
import com.ninereader.reader.view.ZoomableRecyclerView;
import com.ninereader.ui.common.DocumentOpener;
import com.ninereader.util.AppExecutors;

import java.io.IOException;

/** Compare two PDFs side by side (CMP-02): split screen, optional synchronised scrolling. */
public class CompareActivity extends ThemedActivity {

    /** Holds both documents across configuration changes. */
    public static class CompareViewModel extends AndroidViewModel {
        final RenderScheduler[] schedulers = new RenderScheduler[2];
        final MutableLiveData<Integer> loaded = new MutableLiveData<>(0);
        final String[] names = new String[2];

        public CompareViewModel(@NonNull Application app) {
            super(app);
        }

        void open(int side, Uri uri) {
            AppExecutors.get().background().execute(() -> {
                try {
                    PdfEngine e = PdfEngine.open(getApplication(), uri, null);
                    for (int i = 0; i < e.pageCount(); i++) e.ensureSize(i);
                    RenderScheduler old = schedulers[side];
                    schedulers[side] = new RenderScheduler(e, new BitmapCache((int) (Runtime.getRuntime().maxMemory() / 12)));
                    names[side] = com.ninereader.util.FileUtils.displayName(getApplication(), uri);
                    if (old != null) {
                        old.shutdown();
                        old.engine().close();
                    }
                    loaded.postValue(side);
                } catch (IOException | RuntimeException ex) {
                    loaded.postValue(-1);
                }
            });
        }

        @Override
        protected void onCleared() {
            for (RenderScheduler s : schedulers) {
                if (s != null) {
                    s.shutdown();
                    PdfEngine e = s.engine();
                    AppExecutors.get().background().execute(e::close);
                }
            }
            super.onCleared();
        }
    }

    private ActivityCompareBinding b;
    private CompareViewModel vm;
    private boolean sync = true;
    private boolean syncing;
    private int pickingSide;
    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                DocumentOpener.persist(this, uri);
                vm.open(pickingSide, uri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        b = ActivityCompareBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(b.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        vm = new ViewModelProvider(this).get(CompareViewModel.class);
        boolean portraitPhone = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
                && !getResources().getBoolean(R.bool.is_tablet);
        if (portraitPhone) {
            b.panes.setOrientation(LinearLayout.VERTICAL);
            b.divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
            for (int i = 0; i < b.panes.getChildCount(); i++) {
                View child = b.panes.getChildAt(i);
                if (child == b.divider) continue;
                child.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            }
        }
        b.toolbar.setNavigationOnClickListener(v -> finish());
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_sync) {
                sync = !item.isChecked();
                item.setChecked(sync);
                return true;
            }
            return false;
        });
        b.pickA.setOnClickListener(v -> pickSide(0));
        b.pickB.setOnClickListener(v -> pickSide(1));
        b.labelA.setOnClickListener(v -> pickSide(0));
        b.labelB.setOnClickListener(v -> pickSide(1));
        vm.loaded.observe(this, side -> {
            if (side != null && side < 0) Toast.makeText(this, R.string.error_open, Toast.LENGTH_SHORT).show();
            setup(0, b.pagesA, b.pickA, b.labelA);
            setup(1, b.pagesB, b.pickB, b.labelB);
        });
    }

    private void pickSide(int side) {
        pickingSide = side;
        pick.launch(new String[]{"application/pdf"});
    }

    private void setup(int side, ZoomableRecyclerView rv, View pickButton, TextView label) {
        RenderScheduler sch = vm.schedulers[side];
        if (sch == null) return;
        pickButton.setVisibility(View.GONE);
        label.setVisibility(View.VISIBLE);
        label.setText(vm.names[side]);
        if (rv.getAdapter() instanceof PageAdapter && ((PageAdapter) rv.getAdapter()).spreads().spreadCount() == sch.engine().pageCount()
                && rv.getTag() == sch) {
            return;
        }
        rv.setTag(sch);
        PageAdapter adapter = new PageAdapter(sch);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        rv.post(() -> adapter.configure(ReadingMode.CONTINUOUS, rv.getWidth(), rv.getHeight(), 0));
        rv.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (r - l != or - ol || bt - t != ob - ot) adapter.configure(ReadingMode.CONTINUOUS, r - l, bt - t, 0);
        });
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!sync || syncing || dy == 0) return;
                ZoomableRecyclerView other = side == 0 ? b.pagesB : b.pagesA;
                if (other.getAdapter() == null) return;
                syncing = true;
                other.scrollBy(0, dy);
                syncing = false;
            }
        });
        rv.setListener(new ZoomableRecyclerView.Listener() {
            @Override
            public void onSingleTap(@Nullable ZoomableRecyclerView.Hit hit, float x, float y) {
                // no-op
            }

            @Override
            public boolean onDoubleTap(@Nullable ZoomableRecyclerView.Hit hit, float x, float y) {
                return false;
            }

            @Override
            public void onLongPress(@NonNull ZoomableRecyclerView.Hit hit) {
                // no-op
            }

            @Override
            public void onLongPressDrag(@Nullable ZoomableRecyclerView.Hit hit) {
                // no-op
            }

            @Override
            public void onLongPressEnd() {
                // no-op
            }

            @Override
            public void onZoomChanged(float scale) {
                // no-op
            }
        });
    }
}
