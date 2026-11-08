package com.ninereader.ui.common;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.ninereader.R;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.util.FileUtils;

/** List adapters for library documents (cards for recents, rows for lists). */
public final class DocumentAdapters {

    /** Item callbacks. */
    public interface Callbacks {
        void onOpen(@NonNull DocumentEntity doc);

        void onToggleFavorite(@NonNull DocumentEntity doc);

        void onMore(@NonNull DocumentEntity doc, @NonNull View anchor);
    }

    private DocumentAdapters() {
    }

    static final DiffUtil.ItemCallback<DocumentEntity> DIFF = new DiffUtil.ItemCallback<DocumentEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull DocumentEntity a, @NonNull DocumentEntity b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull DocumentEntity a, @NonNull DocumentEntity b) {
            return a.uri.equals(b.uri) && a.name.equals(b.name) && a.lastPage == b.lastPage && a.pageCount == b.pageCount
                    && a.favorite == b.favorite && a.lastOpened == b.lastOpened && a.size == b.size;
        }
    };

    /** Horizontal cards with thumbnail and progress (recent files). */
    public static final class Cards extends ListAdapter<DocumentEntity, Cards.Holder> {
        private final Callbacks callbacks;

        public Cards(@NonNull Callbacks callbacks) {
            super(DIFF);
            this.callbacks = callbacks;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            DocumentEntity d = getItem(position);
            h.title.setText(d.name);
            h.progress.setProgress(Math.round(d.progress() * 1000));
            h.badge.setVisibility(d.favorite ? View.VISIBLE : View.GONE);
            int w = h.itemView.getResources().getDimensionPixelSize(R.dimen.recent_card_width);
            ThumbnailLoader.load(h.thumb, d.uri, w);
            h.itemView.setContentDescription(d.name);
            h.itemView.setOnClickListener(v -> callbacks.onOpen(d));
            h.itemView.setOnLongClickListener(v -> {
                callbacks.onMore(d, v);
                return true;
            });
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final ImageView badge;
            final TextView title;
            final LinearProgressIndicator progress;

            Holder(@NonNull View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                badge = v.findViewById(R.id.favorite_badge);
                title = v.findViewById(R.id.title);
                progress = v.findViewById(R.id.progress);
            }
        }
    }

    /** Vertical rows with favourite toggle and action menu. */
    public static final class Rows extends ListAdapter<DocumentEntity, Rows.Holder> {
        private final Callbacks callbacks;

        public Rows(@NonNull Callbacks callbacks) {
            super(DIFF);
            this.callbacks = callbacks;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            DocumentEntity d = getItem(position);
            h.title.setText(d.name);
            StringBuilder sub = new StringBuilder();
            if (d.size > 0) {
                sub.append(FileUtils.formatSize(d.size, h.itemView.getResources().getConfiguration().getLocales().get(0)));
            }
            if (d.pageCount > 0) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(h.itemView.getContext().getString(R.string.page_of, d.lastPage + 1, d.pageCount));
            }
            if (d.lastOpened > 0) {
                if (sub.length() > 0) sub.append(" · ");
                sub.append(DateUtils.getRelativeTimeSpanString(d.lastOpened));
            }
            h.subtitle.setText(sub);
            h.favorite.setImageResource(d.favorite ? R.drawable.ic_star : R.drawable.ic_star_border);
            h.favorite.setContentDescription(h.itemView.getContext().getString(d.favorite ? R.string.favorite_remove : R.string.favorite_add));
            ThumbnailLoader.load(h.thumb, d.uri, 120);
            h.itemView.setOnClickListener(v -> callbacks.onOpen(d));
            h.favorite.setOnClickListener(v -> callbacks.onToggleFavorite(d));
            h.more.setOnClickListener(v -> callbacks.onMore(d, v));
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView subtitle;
            final ImageButton favorite;
            final ImageButton more;

            Holder(@NonNull View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.title);
                subtitle = v.findViewById(R.id.subtitle);
                favorite = v.findViewById(R.id.favorite);
                more = v.findViewById(R.id.more);
            }
        }
    }
}
