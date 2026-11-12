package com.musicplayer.lite;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One adapter for every list: songs, folders, albums, artists and the play queue. */
final class RowAdapter extends BaseAdapter implements SectionIndexer {

    static final int TRACK = 0;
    static final int FOLDER = 1;
    static final int GROUP = 2;
    static final int HEADER = 3;
    private static final int TYPES = 4;

    static final int SUB_ARTIST_ALBUM = 0;
    static final int SUB_ARTIST = 1;
    static final int SUB_ALBUM = 2;

    interface HeaderListener {
        void onHeaderAction(Row row, boolean shuffle);
    }

    /** The "more" button at the end of a row (the same actions as a long press). */
    interface MoreListener {
        void onMore(Row row, View anchor);
    }

    static final class Row {
        final int type;
        final String title;
        /** Second line for folder, group and header rows; tracks build theirs from {@link #subMode}. */
        final String sub;
        /** Header only: small label above the title. */
        final String path;
        /** Header only: show Play all and Shuffle. */
        final boolean actions;
        /** Track rows, and group rows that show album art. */
        final Track track;
        final int subMode;
        /** Position of the track inside the list a tap plays from. */
        final int index;
        /** Icon for folder rows and for groups without art. */
        final int icon;
        final Object tag;

        private Row(int type, String title, String sub, String path, boolean actions,
                    Track track, int subMode, int index, int icon, Object tag) {
            this.type = type;
            this.title = title;
            this.sub = sub;
            this.path = path;
            this.actions = actions;
            this.track = track;
            this.subMode = subMode;
            this.index = index;
            this.icon = icon;
            this.tag = tag;
        }

        static Row track(Track t, int subMode, int index) {
            return new Row(TRACK, t.title, "", null, false, t, subMode, index, 0, null);
        }

        static Row folder(String title, String sub, int icon, Object tag) {
            return new Row(FOLDER, title, sub, null, false, null, 0, -1, icon, tag);
        }

        static Row group(String title, String sub, Track art, int icon, Object tag) {
            return new Row(GROUP, title, sub, null, false, art, 0, -1, icon, tag);
        }

        static Row header(String path, String title, String sub, boolean actions, Object tag) {
            return new Row(HEADER, title, sub, path, actions, null, 0, -1, 0, tag);
        }

        /** Songs, albums, artists and folders have songs behind them, so they have a menu. */
        boolean hasMenu() {
            return type == TRACK || type == GROUP || (type == FOLDER && tag != null);
        }
    }

    private static final class ItemHolder {
        final ImageView art;
        final EqBarsView playing;
        final View favorite;
        final View more;
        final TextView title;
        final TextView sub;

        ItemHolder(View v) {
            art = v.findViewById(R.id.row_art);
            playing = v.findViewById(R.id.row_playing);
            favorite = v.findViewById(R.id.row_fav);
            more = v.findViewById(R.id.row_more);
            title = v.findViewById(R.id.row_title);
            sub = v.findViewById(R.id.row_sub);
        }
    }

    private static final class HeaderHolder {
        final TextView path;
        final TextView title;
        final TextView sub;
        final View actions;
        final View play;
        final View shuffle;

        HeaderHolder(View v) {
            path = v.findViewById(R.id.hdr_path);
            title = v.findViewById(R.id.hdr_title);
            sub = v.findViewById(R.id.hdr_sub);
            actions = v.findViewById(R.id.hdr_actions);
            play = v.findViewById(R.id.hdr_play);
            shuffle = v.findViewById(R.id.hdr_shuffle);
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final HeaderListener headerListener;
    private MoreListener moreListener;
    private final int artPx;
    private final int accent;
    private final int primary;
    private final ColorStateList accentTint;
    private final boolean glass;
    private final Favorites favorites;

    private List<Row> rows = Collections.emptyList();
    private long currentId = -1;
    /** The music is playing (the bars on the current row move) or paused (they rest). */
    private boolean playing;
    /** When set (the queue screen), the playing row is picked by position, not by track id. */
    private int currentIndex = -1;

    private Object[] sections = new Object[0];
    private int[] sectionStart = new int[0];
    private int[] sectionOfRow = new int[0];

    RowAdapter(Context context, HeaderListener headerListener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.headerListener = headerListener;
        this.artPx = context.getResources().getDimensionPixelSize(R.dimen.row_art);
        this.accent = context.getColor(R.color.accent_text);
        this.primary = context.getColor(R.color.text_primary);
        this.accentTint = ColorStateList.valueOf(accent);
        this.glass = Appearance.glass(context);
        this.favorites = Favorites.get(context);
    }

    void setMoreListener(MoreListener l) {
        moreListener = l;
        notifyDataSetChanged();
    }

    void setRows(List<Row> newRows, boolean indexed) {
        rows = newRows;
        if (indexed) buildSections();
        else {
            sections = new Object[0];
            sectionStart = new int[0];
            sectionOfRow = new int[0];
        }
        notifyDataSetChanged();
    }

    void setCurrentTrack(long id) {
        if (id == currentId) return;
        currentId = id;
        notifyDataSetChanged();
    }

    void setPlaying(boolean on) {
        if (on == playing) return;
        playing = on;
        notifyDataSetChanged();
    }

    /** The favorites changed: redraw the hearts. */
    void refreshFavorites() {
        notifyDataSetChanged();
    }

    void setCurrentIndex(int index) {
        if (index == currentIndex) return;
        currentIndex = index;
        notifyDataSetChanged();
    }

    private void buildSections() {
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        sectionOfRow = new int[rows.size()];
        String last = null;
        for (int i = 0; i < rows.size(); i++) {
            String s = Fmt.section(rows.get(i).title);
            if (!s.equals(last)) {
                names.add(s);
                starts.add(i);
                last = s;
            }
            sectionOfRow[i] = names.size() - 1;
        }
        sections = names.toArray();
        sectionStart = new int[starts.size()];
        for (int i = 0; i < sectionStart.length; i++) sectionStart[i] = starts.get(i);
    }

    // ---- BaseAdapter ----------------------------------------------------------------

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Row getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return TYPES;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public boolean isEnabled(int position) {
        return rows.get(position).type != HEADER;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row r = rows.get(position);
        return r.type == HEADER ? headerView(r, convertView, parent) : itemView(r, convertView, parent);
    }

    private View itemView(Row r, View convertView, ViewGroup parent) {
        View v = convertView;
        ItemHolder h;
        if (v == null) {
            v = inflater.inflate(R.layout.row_item, parent, false);
            h = new ItemHolder(v);
            v.setTag(h);
        } else {
            h = (ItemHolder) v.getTag();
        }
        final boolean current = isCurrent(r);
        h.title.setText(r.title);
        h.title.setTextColor(current ? accent : primary);

        final String sub = r.type == TRACK ? trackSubtitle(r) : r.sub;
        h.sub.setText(sub);
        h.sub.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        final boolean menu = moreListener != null && r.hasMenu();
        h.more.setVisibility(menu ? View.VISIBLE : View.GONE);
        h.more.setOnClickListener(menu ? x -> moreListener.onMore(r, x) : null);
        h.playing.setVisibility(current ? View.VISIBLE : View.GONE);
        h.playing.setPlaying(current && playing);
        h.favorite.setVisibility(r.type == TRACK && favorites.contains(r.track) ? View.VISIBLE : View.GONE);

        if (r.track != null) {
            h.art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.art.setImageTintList(null);
            h.art.setBackgroundResource(R.drawable.bg_art);
            ArtLoader.get(context).load(r.track, h.art, artPx);
        } else {
            h.art.setTag(R.id.tag_art, null);
            h.art.setBackgroundResource(R.drawable.bg_tile);
            h.art.setScaleType(ImageView.ScaleType.CENTER);
            h.art.setImageTintList(accentTint);
            h.art.setImageResource(r.icon);
        }
        return v;
    }

    private View headerView(final Row r, View convertView, ViewGroup parent) {
        View v = convertView;
        HeaderHolder h;
        if (v == null) {
            v = inflater.inflate(R.layout.row_header, parent, false);
            h = new HeaderHolder(v);
            v.setTag(h);
        } else {
            h = (HeaderHolder) v.getTag();
        }
        setTextOrHide(h.path, r.path);
        setTextOrHide(h.title, r.title);
        setTextOrHide(h.sub, r.sub);
        h.actions.setVisibility(r.actions ? View.VISIBLE : View.GONE);
        if (glass && r.actions) {
            h.play.setBackground(Glass.pill(context, false));
            h.shuffle.setBackground(Glass.pill(context, false));
        }
        h.play.setOnClickListener(x -> {
            if (headerListener != null) headerListener.onHeaderAction(r, false);
        });
        h.shuffle.setOnClickListener(x -> {
            if (headerListener != null) headerListener.onHeaderAction(r, true);
        });
        return v;
    }

    private static void setTextOrHide(TextView tv, String s) {
        boolean has = s != null && !s.isEmpty();
        tv.setText(has ? s : "");
        tv.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    private boolean isCurrent(Row r) {
        if (r.type != TRACK) return false;
        return currentIndex >= 0 ? r.index == currentIndex : r.track.id == currentId;
    }

    private static String trackSubtitle(Row r) {
        Track t = r.track;
        switch (r.subMode) {
            case SUB_ARTIST:
                return t.artist;
            case SUB_ALBUM:
                return t.album;
            default:
                return t.artist + " · " + t.album;
        }
    }

    // ---- SectionIndexer (the alphabet bubble while fast-scrolling) --------------------------

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int section) {
        if (sectionStart.length == 0) return 0;
        return sectionStart[Fmt.clamp(section, 0, sectionStart.length - 1)];
    }

    @Override
    public int getSectionForPosition(int position) {
        if (sectionOfRow.length == 0) return 0;
        return sectionOfRow[Fmt.clamp(position, 0, sectionOfRow.length - 1)];
    }
}
