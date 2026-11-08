package com.ninereader.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.ui.MainActivity;
import com.ninereader.ui.reader.ReaderActivity;
import com.ninereader.util.AppExecutors;

/** Home screen widget "Resume reading" (RAP-09): opens the last document in one tap. */
public class ResumeWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(@NonNull Context context, @NonNull AppWidgetManager manager, @NonNull int[] ids) {
        PendingResult pending = goAsync();
        Context app = context.getApplicationContext();
        AppExecutors.get().diskIo().execute(() -> {
            try {
                DocumentEntity last = NineReaderApp.from(app).documents().lastOpened();
                for (int id : ids) manager.updateAppWidget(id, build(app, last));
            } finally {
                pending.finish();
            }
        });
    }

    @NonNull
    static RemoteViews build(@NonNull Context context, DocumentEntity last) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_resume);
        Intent intent;
        if (last != null) {
            views.setTextViewText(R.id.widget_title, last.name);
            views.setTextViewText(R.id.widget_subtitle,
                    context.getString(R.string.page_of, last.lastPage + 1, Math.max(1, last.pageCount)));
            views.setProgressBar(R.id.widget_progress, 1000, Math.round(last.progress() * 1000), false);
            intent = new Intent(context, ReaderActivity.class).setData(Uri.parse(last.uri))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name));
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_empty));
            views.setProgressBar(R.id.widget_progress, 1000, 0, false);
            intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        return views;
    }

    /** Refreshes all widgets (called when the reader closes). */
    public static void requestUpdate(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ResumeWidgetProvider.class));
        if (ids.length == 0) return;
        Intent i = new Intent(context, ResumeWidgetProvider.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        context.sendBroadcast(i);
    }
}
