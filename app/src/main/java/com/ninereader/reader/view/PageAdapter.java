package com.ninereader.reader.view;

import android.graphics.ColorFilter;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.ReadingMode;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.reader.SpreadLayout;

/**
 * Adapter of the page list: lays out each spread for the current mode (fit width in continuous
 * mode, fit screen in paged / double page mode) and binds it to a {@link SpreadView}.
 */
public class PageAdapter extends RecyclerView.Adapter<PageAdapter.Holder> {

    private final RenderScheduler scheduler;
    private final PdfEngine engine;
    private SpreadLayout spreads;
    private ReadingMode mode = ReadingMode.CONTINUOUS;
    private int containerW;
    private int containerH;
    private int gutter;
    /** Crop fractions {l, t, r, b} applied to every page, or null. */
    @Nullable
    private float[] crop;
    @Nullable
    private PageDecorator decorator;
    @Nullable
    private ColorFilter colorFilter;
    private int pageBackground = 0xFFFFFFFF;
    private String pageLabelFormat = "%d";

    public PageAdapter(@NonNull RenderScheduler scheduler) {
        this.scheduler = scheduler;
        this.engine = scheduler.engine();
        this.spreads = new SpreadLayout(engine.pageCount(), false, true);
        setHasStableIds(true);
    }

    public void setPageLabelFormat(@NonNull String format) {
        this.pageLabelFormat = format;
    }

    /** Updates mode and container size; returns true when the layout changed. */
    public boolean configure(@NonNull ReadingMode mode, int width, int height, int gutterPx) {
        boolean changed = mode != this.mode || width != containerW || height != containerH || gutterPx != gutter;
        this.mode = mode;
        this.containerW = width;
        this.containerH = height;
        this.gutter = gutterPx;
        SpreadLayout newSpreads = new SpreadLayout(engine.pageCount(), mode == ReadingMode.DOUBLE, true);
        if (newSpreads.spreadCount() != spreads.spreadCount() || newSpreads.isDoublePage() != spreads.isDoublePage()) {
            changed = true;
        }
        spreads = newSpreads;
        if (changed) notifyDataSetChanged();
        return changed;
    }

    public void setCrop(@Nullable float[] crop) {
        this.crop = crop;
        notifyDataSetChanged();
    }

    @Nullable
    public float[] getCrop() {
        return crop;
    }

    public void setDecorator(@Nullable PageDecorator decorator) {
        this.decorator = decorator;
    }

    public void setColorFilter(@Nullable ColorFilter filter, int pageBackground) {
        this.colorFilter = filter;
        this.pageBackground = pageBackground;
    }

    @NonNull
    public SpreadLayout spreads() {
        return spreads;
    }

    public ReadingMode mode() {
        return mode;
    }

    @Override
    public int getItemCount() {
        return containerW > 0 ? spreads.spreadCount() : 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SpreadView v = new SpreadView(parent.getContext());
        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SpreadView v = holder.view;
        PageFrame[] frames = layoutSpread(position);
        int itemH = itemHeight(frames);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.height != itemH || lp.width != containerW) {
            lp.width = containerW;
            lp.height = itemH;
            v.setLayoutParams(lp);
        }
        v.setScheduler(scheduler);
        v.setDecorator(decorator);
        v.setColorFilter(colorFilter, pageBackground);
        v.bind(position, frames, crop == null ? 0 : java.util.Arrays.hashCode(crop));
        int first = spreads.firstPage(position);
        StringBuilder desc = new StringBuilder(String.format(pageLabelFormat, first + 1));
        if (frames.length > 1) desc.append(" – ").append(String.format(pageLabelFormat, first + 2));
        v.setContentDescription(desc);
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        holder.view.unbind();
    }

    private int itemHeight(PageFrame[] frames) {
        if (mode.isHorizontal()) return containerH;
        float bottom = 0;
        for (PageFrame f : frames) bottom = Math.max(bottom, f.slot.bottom);
        return Math.max(1, Math.round(bottom));
    }

    private float pageW(int page) {
        if (engine.isMeasured(page)) return engine.pageWidth(page);
        return engine.isMeasured(0) ? engine.pageWidth(0) : 612f;
    }

    private float pageH(int page) {
        if (engine.isMeasured(page)) return engine.pageHeight(page);
        return engine.isMeasured(0) ? engine.pageHeight(0) : 792f;
    }

    /** Computes the frames of a spread for the current container. */
    @NonNull
    public PageFrame[] layoutSpread(int spread) {
        int first = spreads.firstPage(spread);
        int count = spreads.pagesIn(spread);
        float cl = crop == null ? 0f : crop[0];
        float ct = crop == null ? 0f : crop[1];
        float cr = crop == null ? 1f : crop[2];
        float cb = crop == null ? 1f : crop[3];
        float cw = cr - cl;
        float ch = cb - ct;
        PageFrame[] frames = new PageFrame[count];
        float[] aspects = new float[count];
        float aspectSum = 0;
        for (int i = 0; i < count; i++) {
            int page = first + i;
            float visW = pageW(page) * cw;
            float visH = pageH(page) * ch;
            aspects[i] = visW / visH;
            aspectSum += aspects[i];
        }
        float totalGutter = gutter * (count - 1);
        float slotH;
        float left;
        float top;
        if (!mode.isHorizontal()) {
            slotH = (containerW - totalGutter) / aspectSum;
            left = 0;
            top = 0;
        } else {
            float availW = containerW - totalGutter;
            float hByWidth = availW / aspectSum;
            slotH = Math.min(containerH, hByWidth);
            float usedW = slotH * aspectSum + totalGutter;
            left = (containerW - usedW) / 2f;
            top = (containerH - slotH) / 2f;
        }
        float x = left;
        for (int i = 0; i < count; i++) {
            int page = first + i;
            PageFrame f = new PageFrame();
            f.page = page;
            float slotW = slotH * aspects[i];
            f.slot.set(x, top, x + slotW, top + slotH);
            f.fullW = slotW / cw;
            f.fullH = slotH / ch;
            f.offX = cl * f.fullW;
            f.offY = ct * f.fullH;
            PageTransform t = engine.transformIfMeasured(page);
            f.transform = t != null ? t : PageTransform.simple(pageW(page), pageH(page));
            frames[i] = f;
            x += slotW + gutter;
        }
        return frames;
    }

    /** Holder of a spread view. */
    public static final class Holder extends RecyclerView.ViewHolder {
        final SpreadView view;

        Holder(@NonNull SpreadView view) {
            super(view);
            this.view = view;
        }
    }
}
