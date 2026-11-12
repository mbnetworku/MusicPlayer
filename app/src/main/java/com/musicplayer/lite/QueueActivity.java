package com.musicplayer.lite;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The play order: tap a song to jump to it, long-press to remove it. */
public class QueueActivity extends BoundActivity {
    private RowAdapter adapter;
    private ListView list;
    private View empty;
    private TextView subtitle;
    private int shownVersion = -1;
    private boolean scrolled;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_queue);
        prepareWindow();
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        subtitle = findViewById(R.id.subtitle);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new RowAdapter(this, null);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, position, id) -> {
            if (service != null) service.skipToQueueItem(adapter.getItem(position).index);
        });
        list.setOnItemLongClickListener((p, v, position, id) -> {
            final int index = adapter.getItem(position).index;
            new AlertDialog.Builder(this)
                    .setTitle(adapter.getItem(position).title)
                    .setItems(new String[]{getString(R.string.action_remove)}, (d, which) -> {
                        if (service != null) service.removeFromQueue(index);
                    })
                    .show();
            return true;
        });
    }

    @Override
    protected void onFavoritesChanged() {
        adapter.refreshFavorites();
    }

    @Override
    protected void onPlaybackUpdated() {
        if (service == null) return;
        adapter.setPlaying(service.isPlaying());
        PlayQueue q = service.queue();
        if (q.version() != shownVersion) {
            shownVersion = q.version();
            List<Track> tracks = q.tracks();
            List<RowAdapter.Row> rows = new ArrayList<>(tracks.size());
            for (int i = 0; i < tracks.size(); i++) {
                rows.add(RowAdapter.Row.track(tracks.get(i), RowAdapter.SUB_ARTIST_ALBUM, i));
            }
            adapter.setRows(rows, false);
            subtitle.setText(getResources().getQuantityString(R.plurals.songs_count, tracks.size(), tracks.size()));
        }
        adapter.setCurrentIndex(q.position());
        empty.setVisibility(q.size() == 0 ? View.VISIBLE : View.GONE);
        if (!scrolled && q.size() > 0) {
            scrolled = true;
            list.setSelection(Math.max(0, q.position() - 2));
        }
    }
}
