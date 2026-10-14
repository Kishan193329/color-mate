package com.colourmate.paintbynumber.activity;

import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.ImageInfo;
import com.colourmate.paintbynumber.Utils.ImageManager;
import com.colourmate.paintbynumber.util.DailyChallengeManager;
import com.colourmate.paintbynumber.util.DailyImageAssignments;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The Daily Challenge calendar: a month of days, each showing its own picture.
 *
 * <p>The picture for a date comes from {@link DailyChallengeManager#getChallengeIndexFor}, which is
 * a pure function of that date — so the grid looks the same on every device, every time it is
 * opened, and every picture in the library appears once before any repeats.
 *
 * <p>Days ahead of today are shown dimmed and cannot be opened; the app already hides future-dated
 * content elsewhere, and a calendar that let you play tomorrow would undo the daily habit.
 */
public class DailyCalendarActivity extends BaseActivity {

    private static final int COLUMNS = 7;
    /** Pictures offered for one date (the date's own seeded arrangement, first N). */
    private static final int DAILY_PICK_COUNT = 12;

    private final Calendar shownMonth = Calendar.getInstance();
    private final List<Cell> cells = new ArrayList<>();

    private List<ImageInfo> library = new ArrayList<>();
    private DayAdapter adapter;
    private RowAdapter rowAdapter;

    /** Categories from the By Number tab, and the pictures inside the one currently selected. */
    private final List<String> categories = new ArrayList<>();
    private final List<com.colourmate.paintbynumber.model.model.Level> categoryLevels =
            new ArrayList<>();
    private Calendar selectedDate = Calendar.getInstance();
    private String selectedCategory;

    /** Side of one picture tile, worked out once. See tileSize(). */
    private int tileSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_calendar);
        setupEdgeToEdge();

        library = safeLibrary();

        findViewById(R.id.cal_back).setOnClickListener(v -> finish());
        findViewById(R.id.cal_play_today).setOnClickListener(v -> playToday());
        findViewById(R.id.cal_prev).setOnClickListener(v -> stepMonth(-1));
        findViewById(R.id.cal_next).setOnClickListener(v -> stepMonth(1));

        buildWeekdayHeader();

        RecyclerView grid = findViewById(R.id.cal_grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        // No setHasFixedSize: this grid is wrap_content inside a scrolling column, which lint
        // (InvalidSetHasFixedSize) rejects as a fatal release error — it would wrongly cap the grid.
        adapter = new DayAdapter();
        grid.setAdapter(adapter);

        tileSize = tileSize();

        RecyclerView rows = findViewById(R.id.cal_list);
        rows.setLayoutManager(new GridLayoutManager(this, 2));
        rowAdapter = new RowAdapter();
        rows.setAdapter(rowAdapter);

        bindStreak();
        rebuildMonth();
        loadCategories();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from a finished picture: the tick, the streak and the total all changed.
        bindStreak();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /**
     * Same treatment as Home: the status bar is transparent and the toolbar pads itself by the
     * inset, so the screen no longer wears a solid lavender band across the top.
     */
    private void setupEdgeToEdge() {
        android.view.Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
        androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView())
                .setAppearanceLightStatusBars(true);
        View toolbar = findViewById(R.id.cal_toolbar);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top + Math.round(12 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        View scroll = findViewById(R.id.cal_scroll);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            androidx.core.graphics.Insets nav = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    nav.bottom + Math.round(28 * getResources().getDisplayMetrics().density));
            return insets;
        });
    }

    /** The hero button: today's first picture, straight into the game. */
    private void playToday() {
        selectedDate = Calendar.getInstance();
        if (categoryLevels.isEmpty()) {
            Toast.makeText(this, R.string.daily_loading_pictures, Toast.LENGTH_SHORT).show();
            applyCategoryForSelectedDate();
            rebuildMonth();
            return;
        }
        applyCategoryForSelectedDate();
        rebuildMonth();
        List<Cell> entries = buildSelectedDateEntries();
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.daily_no_library, Toast.LENGTH_SHORT).show();
            return;
        }
        openGame(entries.get(0));
    }

    /** Never let a missing or half-loaded library crash the screen. */
    private List<ImageInfo> safeLibrary() {
        try {
            List<ImageInfo> list = ImageManager.getInstance().getImageInfoList();
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void bindStreak() {
        int streak = DailyChallengeManager.getDailyStreak(this);
        TextView streakView = findViewById(R.id.cal_streak);
        TextView subView = findViewById(R.id.cal_streak_sub);
        TextView countdown = findViewById(R.id.cal_countdown);

        streakView.setText(getResources().getQuantityString(
                R.plurals.daily_streak_days, streak, streak));
        subView.setText(DailyChallengeManager.isTodayCompleted(this)
                ? getString(R.string.daily_done_today)
                : getString(R.string.daily_not_done_today));
        countdown.setText(DailyChallengeManager.getCountdownText());
        TextView total = findViewById(R.id.cal_total);
        total.setText(String.valueOf(DailyChallengeManager.getTotalCompleted(this)));
    }

    private void stepMonth(int delta) {
        shownMonth.add(Calendar.MONTH, delta);
        rebuildMonth();
    }

    /**
     * Lays out one month: blanks for the days before the 1st, then every date with its picture.
     */
    private void rebuildMonth() {
        cells.clear();

        Calendar cursor = (Calendar) shownMonth.clone();
        cursor.set(Calendar.DAY_OF_MONTH, 1);

        int firstWeekday = cursor.get(Calendar.DAY_OF_WEEK);          // Sunday = 1
        int leadingBlanks = (firstWeekday - cursor.getFirstDayOfWeek() + 7) % 7;
        for (int i = 0; i < leadingBlanks; i++) {
            cells.add(Cell.blank());
        }

        Calendar today = Calendar.getInstance();
        int daysInMonth = cursor.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int day = 1; day <= daysInMonth; day++) {
            Calendar date = (Calendar) cursor.clone();
            date.set(Calendar.DAY_OF_MONTH, day);

            Cell cell = new Cell();
            cell.day = day;
            cell.date = date;
            cell.isToday = sameDate(date, today);
            cell.isFuture = date.after(today) && !cell.isToday;
            cell.isSelected = selectedDate != null && sameDate(date, selectedDate);
            resolvePicture(cell);
            cells.add(cell);
        }

        TextView month = findViewById(R.id.cal_month);
        month.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(shownMonth.getTime()));

        TextView footer = findViewById(R.id.cal_footer);
        // Describes the source actually feeding the grid — the selected By Number category — not
        // the old local library, which is no longer what the dates draw from.
        if (categoryLevels.isEmpty()) {
            footer.setText(R.string.daily_no_library);
        } else {
            // One plain line; the "picked by you" variant described an admin picker nobody reaches.
            footer.setText(getString(R.string.daily_footer, categoryLevels.size()));
        }

        // The heading under the calendar names the selected date; the tiles no longer badge it.
        TextView listHeader = findViewById(R.id.cal_list_header);
        TextView listSub = findViewById(R.id.cal_list_sub);
        if (selectedDate != null) {
            String dateLabel = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
                    .format(selectedDate.getTime());
            listHeader.setText(getString(R.string.daily_pictures_for, dateLabel));
            listSub.setText(categoryLevels.isEmpty()
                    ? getString(R.string.daily_loading_pictures)
                    : getString(R.string.daily_pictures_sub,
                            capitalise(selectedCategory), Math.min(DAILY_PICK_COUNT, categoryLevels.size())));
        }

        adapter.notifyDataSetChanged();
        if (rowAdapter != null) {
            rowAdapter.submit(buildSelectedDateEntries());
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Works out which picture a date shows: a hand-pinned one if there is one, otherwise the
     * automatic rotation. Kept in one place so the grid and the list below can never disagree.
     */
    private void resolvePicture(Cell cell) {
        String pinned = DailyImageAssignments.get(this, cell.date);
        if (pinned != null) {
            cell.assignedByHand = true;
            cell.imageId = pinned;
            cell.imageIndex = indexOfImage(pinned);
            return;
        }

        cell.assignedByHand = false;
        if (library.isEmpty()) {
            cell.imageIndex = -1;
            cell.imageId = null;
            return;
        }
        cell.imageIndex = DailyChallengeManager.getChallengeIndexFor(cell.date, library.size());
        cell.imageId = library.get(cell.imageIndex).getImageID();
    }

    private int indexOfImage(String imageId) {
        for (int i = 0; i < library.size(); i++) {
            if (library.get(i).getImageID().equals(imageId)) {
                return i;
            }
        }
        return -1;      // pinned to something no longer in the library
    }

    /**
     * Width of one tile: half the screen less the gutters and the tile's own margins.
     *
     * <p>Computed rather than measured because Picasso's fit() waits for the view to be laid out,
     * and these tiles get their height from a post() that can land after the image request — which
     * left every tile past the first screenful permanently blank.
     */
    private int tileSize() {
        float density = getResources().getDisplayMetrics().density;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int gutters = Math.round(2 * 18 * density);      // cm_gutter either side
        int margins = Math.round(4 * 4 * density);       // 4dp on both sides of two tiles
        return Math.max(1, (screen - gutters - margins) / 2);
    }

    /**
     * The pictures for the date currently selected on the calendar.
     *
     * <p>Every tile is badged with that date, so what the grid belongs to is readable without a
     * heading. Rebuilt from scratch on each selection — the lists are short, and rebuilding is what
     * guarantees the grid can never be left showing the date before.
     */
    private List<Cell> buildSelectedDateEntries() {
        List<Cell> entries = new ArrayList<>();
        if (selectedDate == null || categoryLevels.isEmpty()) return entries;

        Calendar today = Calendar.getInstance();
        boolean isToday = sameDate(selectedDate, today);
        boolean isFuture = selectedDate.after(today) && !isToday;
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        for (com.colourmate.paintbynumber.model.model.Level level : arrangeForDate(selectedDate)) {
            // A daily challenge is a handful of pictures, not the whole category — and this grid
            // sits in a ScrollView, so every tile is inflated at once (1,124 for "flowers").
            if (entries.size() >= DAILY_PICK_COUNT) break;
            Cell cell = new Cell();
            cell.day = day;
            cell.date = (Calendar) selectedDate.clone();
            cell.isToday = isToday;
            cell.isFuture = isFuture;
            cell.level = level;
            entries.add(cell);
        }
        return entries;
    }

    /**
     * That date's own arrangement of its category.
     *
     * <p>Ordering by date rather than just handing back the category as-is matters: neighbouring
     * dates often fall in the same category, and without this, picking a different day would leave
     * an identical grid on screen and look like the selection had not registered.
     */
    private List<com.colourmate.paintbynumber.model.model.Level> arrangeForDate(Calendar date) {
        List<com.colourmate.paintbynumber.model.model.Level> ordered =
                new ArrayList<>(categoryLevels);

        // Seeded by the date, so the same day always arranges the same way — on this device and
        // on any other. Nothing is stored.
        java.util.Random random = new java.util.Random(
                date.get(Calendar.YEAR) * 10000L
                        + date.get(Calendar.MONTH) * 100L
                        + date.get(Calendar.DAY_OF_MONTH));
        for (int i = ordered.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            if (i != j) java.util.Collections.swap(ordered, i, j);
        }
        return ordered;
    }

    private static boolean sameDate(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void buildWeekdayHeader() {
        LinearLayout header = findViewById(R.id.cal_weekdays);
        header.removeAllViews();

        String[] shortNames = new SimpleDateFormat("EEE", Locale.getDefault())
                .getDateFormatSymbols().getShortWeekdays();
        Calendar cal = Calendar.getInstance();
        int first = cal.getFirstDayOfWeek();

        for (int i = 0; i < COLUMNS; i++) {
            int weekday = ((first - 1 + i) % 7) + 1;
            TextView label = new TextView(this);
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            label.setGravity(Gravity.CENTER);
            label.setText(shortNames[weekday].toUpperCase(Locale.getDefault()));
            label.setTextSize(10f);
            label.setTextColor(getResources().getColor(R.color.cm_ink_3));
            label.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(
                    this, R.font.quicksand_bold));
            header.addView(label);
        }
    }

    // ─── Cell model ───

    private static final class Cell {
        int day;                 // 0 = blank padding
        int imageIndex = -1;
        String imageId;          // what this date actually shows
        boolean assignedByHand;  // pinned here, rather than from the rotation
        boolean isToday;
        boolean isFuture;
        boolean isSelected;
        Calendar date;

        /** Set on the tiles under the calendar: the picture that tile stands for. */
        com.colourmate.paintbynumber.model.model.Level level;

        static Cell blank() {
            return new Cell();
        }

        boolean isBlank() {
            return day == 0;
        }
    }

    // ─── Adapter ───

    private final class DayAdapter extends RecyclerView.Adapter<DayAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(cells.get(position));
        }

        @Override
        public int getItemCount() {
            return cells.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            private final View frame;
            private final ImageView image;
            private final View tint;
            private final TextView dayLabel;
            private final ImageView done;

            Holder(@NonNull View itemView) {
                super(itemView);
                frame = itemView.findViewById(R.id.cell_frame);
                image = itemView.findViewById(R.id.cell_image);
                tint = itemView.findViewById(R.id.cell_tint);
                dayLabel = itemView.findViewById(R.id.cell_day);
                done = itemView.findViewById(R.id.cell_done);

                // Cells are square, so the month always lays out on a predictable grid.
                itemView.post(() -> {
                    ViewGroup.LayoutParams lp = frame.getLayoutParams();
                    lp.height = itemView.getWidth();
                    frame.setLayoutParams(lp);
                });
            }

            void bind(Cell cell) {
                if (cell.isBlank()) {
                    itemView.setVisibility(View.INVISIBLE);
                    itemView.setOnClickListener(null);
                    return;
                }
                itemView.setVisibility(View.VISIBLE);
                dayLabel.setText(String.valueOf(cell.day));

                // Monochrome calendar (2026-10-14): state is the only thing that colours a cell.
                // The per-picture pastel tints were noise — thirty random colours said nothing.
                image.setImageDrawable(null);
                tint.setBackgroundColor(Color.TRANSPARENT);

                boolean completed = DailyChallengeManager.isCompleted(DailyCalendarActivity.this, cell.date);
                frame.setBackgroundResource(completed
                        ? R.drawable.cm_cal_cell_done
                        : cell.isToday ? R.drawable.cm_cal_cell_today : R.drawable.cm_cal_cell);
                dayLabel.setTextColor(getColor(completed || cell.isToday ? R.color.cm_violet : R.color.cm_ink));

                // Foreground, not background: the ring has to sit over the fill.
                frame.setForeground(cell.isSelected
                        ? androidx.core.content.ContextCompat.getDrawable(
                                DailyCalendarActivity.this, R.drawable.cm_cal_cell_selected)
                        : null);

                itemView.setAlpha(cell.isFuture ? 0.35f : 1f);
                done.setVisibility(completed ? View.VISIBLE : View.GONE);

                itemView.setOnClickListener(cell.isFuture ? null : v -> openDay(cell));
                itemView.setClickable(!cell.isFuture);
            }
        }
    }

    /** A stable colour per picture, so neighbouring days are visibly different at a glance. */
    private int colourFor(int imageIndex) {
        if (imageIndex < 0) return Color.parseColor("#14201636");
        int[] paints = {
                0x337A5CFF, 0x3337A9FF, 0x33FF6BA9, 0x3321D4C2, 0x33FFC24B,
                0x339B7BFF, 0x336FC4FF, 0x33FF9AC4, 0x336BE6D9, 0x33FFD777
        };
        return paints[imageIndex % paints.length];
    }

    /**
     * Plays the picture a date belongs to.
     *
     * <p>It goes straight into the ordinary By Number game with the same Level object the tab
     * itself would pass, so the picture downloads, plays and saves its progress exactly as it does
     * anywhere else in the app — this screen is only another way in, not a second game.
     */
    private void openGame(Cell cell) {
        // The tile's own picture; levelForDate only as a fallback for a cell built without one.
        com.colourmate.paintbynumber.model.model.Level level =
                cell.level != null ? cell.level : levelForDate(cell.date);
        if (level == null) {
            Toast.makeText(this, R.string.daily_no_library, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = GameActivity.newIntent(this, level);
        // Finishing it marks this date done (tick, streak, total) — see GameActivity.EXTRA_DAILY_DATE.
        intent.putExtra(GameActivity.EXTRA_DAILY_DATE, DailyChallengeManager.dateKey(cell.date));
        startActivity(intent);
    }

    /**
     * Tapping a date on the calendar selects it and re-fills the grid below with that date's
     * pictures.
     *
     * <p>The grid is rebuilt <em>before</em> the category is resolved, so the change is on screen
     * immediately even when the new date belongs to a category whose pictures still have to be
     * fetched; the fetch rebuilds it again when it lands.
     */
    private void openDay(Cell cell) {
        if (cell == null || cell.date == null) return;

        selectedDate = (Calendar) cell.date.clone();
        // Resolve the category first: if it has changed, that drops the old date's pictures, so the
        // rebuild below can never draw them under the new date's badge.
        applyCategoryForSelectedDate();
        rebuildMonth();
    }

    // ─── By Number data ───

    /**
     * Categories come from the same endpoint the By Number tab uses, so the calendar draws on the
     * live catalogue rather than a second, separate list that could drift out of step with it.
     */
    private void loadCategories() {
        com.colourmate.paintbynumber.Utils.Manager.getInstance()
                .executeApiRequest("category/", json -> runOnUiThread(() -> {
                    categories.clear();
                    if (json != null && json.has("categories")) {
                        com.google.gson.JsonArray arr = json.getAsJsonArray("categories");
                        for (int i = 0; i < arr.size(); i++) {
                            String c = arr.get(i).getAsString();
                            if (c != null && !c.isEmpty()) categories.add(c);
                        }
                    }
                    applyCategoryForSelectedDate();
                }));
    }

    /**
     * The selected date picks the category — deterministically, so the same date always belongs to
     * the same category on every device.
     */
    private void applyCategoryForSelectedDate() {
        if (categories.isEmpty()) return;
        int index = DailyChallengeManager.getChallengeIndexFor(selectedDate, categories.size());
        String category = categories.get(index);
        if (category.equals(selectedCategory) && !categoryLevels.isEmpty()) {
            rebuildMonth();          // same category, pictures already loaded
            return;
        }
        selectedCategory = category;
        // Let go of the previous category's pictures now rather than when the new ones arrive.
        // Holding on to them shows the last date's artwork under this date's badge for as long as
        // the fetch takes — an empty grid for a moment is honest, that is not.
        categoryLevels.clear();
        loadLevelsFor(category);
    }

    private void loadLevelsFor(final String category) {
        com.colourmate.paintbynumber.Utils.Manager.getInstance()
                .executeApiRequest("gimg/c/" + category, json -> runOnUiThread(() -> {
                    // Tapping through dates quickly can leave two fetches in flight; the slower one
                    // must not overwrite the newer date's pictures when it finally lands.
                    if (!category.equals(selectedCategory)) return;

                    categoryLevels.clear();
                    if (json != null && json.has("data")) {
                        com.google.gson.JsonArray data = json.getAsJsonArray("data");
                        for (int i = 0; i < data.size(); i++) {
                            com.colourmate.paintbynumber.model.model.Level level =
                                    com.colourmate.paintbynumber.model.model.Level.fromJson(
                                            data.get(i).getAsJsonObject());
                            if (level != null && level.getFileName() != null
                                    && !level.getFileName().isEmpty()) {
                                categoryLevels.add(level);
                            }
                        }
                    }
                    rebuildMonth();
                }));
    }

    /**
     * The picture a date shows: a stable pick from the selected category. Stable rather than truly
     * random so the grid does not reshuffle itself every time the screen is opened — a "daily"
     * picture that changed on each visit would not be daily at all.
     */
    private com.colourmate.paintbynumber.model.model.Level levelForDate(Calendar date) {
        if (categoryLevels.isEmpty()) return null;
        int index = DailyChallengeManager.getChallengeIndexFor(date, categoryLevels.size());
        return categoryLevels.get(index);
    }

    private String thumbnailUrl(com.colourmate.paintbynumber.model.model.Level level) {
        if (level == null) return null;
        return com.colourmate.paintbynumber.Utils.Manager.BASE_URL + "thumb/"
                + level.getCategory() + "/t/" + level.getFileName() + ".png";
    }

    private void showPicker(final Cell cell) {
        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_pick_image);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }

        String dateLabel = new SimpleDateFormat("d MMMM", Locale.getDefault())
                .format(cell.date.getTime());
        ((TextView) dialog.findViewById(R.id.pick_title))
                .setText(getString(R.string.daily_pick_title, dateLabel));

        final RecyclerView list = dialog.findViewById(R.id.pick_list);
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        final PickAdapter pickAdapter = new PickAdapter(cell, dialog);
        list.setAdapter(pickAdapter);

        android.widget.EditText search = dialog.findViewById(R.id.pick_search);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                pickAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        dialog.findViewById(R.id.pick_auto).setOnClickListener(v -> {
            DailyImageAssignments.clear(this, cell.date);
            Toast.makeText(this, getString(R.string.daily_cleared_toast, dateLabel),
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            rebuildMonth();
        });

        dialog.show();
    }

    /**
     * The library can run to tens of thousands of pictures, so the picker is a plain list with a
     * search box rather than a grid of thumbnails — it stays responsive and is quicker to use.
     */
    private final class PickAdapter extends RecyclerView.Adapter<PickAdapter.Holder> {
        private final Cell cell;
        private final android.app.Dialog dialog;
        private List<ImageInfo> shown = new ArrayList<>();

        PickAdapter(Cell cell, android.app.Dialog dialog) {
            this.cell = cell;
            this.dialog = dialog;
            this.shown = capped(library);
        }

        /** Only the first slice is listed until the user searches; 19k rows help nobody. */
        private List<ImageInfo> capped(List<ImageInfo> source) {
            return source.size() <= 200 ? new ArrayList<>(source)
                    : new ArrayList<>(source.subList(0, 200));
        }

        void filter(String query) {
            if (query == null || query.trim().isEmpty()) {
                shown = capped(library);
            } else {
                String q = query.trim().toLowerCase(Locale.getDefault());
                List<ImageInfo> hits = new ArrayList<>();
                for (ImageInfo info : library) {
                    if (info.getImageID().toLowerCase(Locale.getDefault()).contains(q)) {
                        hits.add(info);
                        if (hits.size() >= 200) break;
                    }
                }
                shown = hits;
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pick_image, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final ImageInfo info = shown.get(position);
            holder.name.setText(info.getImageID());
            holder.swatch.setBackgroundColor(colourFor(indexOfImage(info.getImageID())));
            holder.itemView.setOnClickListener(v -> {
                DailyImageAssignments.assign(DailyCalendarActivity.this, cell.date,
                        info.getImageID());
                String dateLabel = new SimpleDateFormat("d MMMM", Locale.getDefault())
                        .format(cell.date.getTime());
                Toast.makeText(DailyCalendarActivity.this,
                        getString(R.string.daily_assigned_toast, info.getImageID(), dateLabel),
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                rebuildMonth();
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final View swatch;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.pick_name);
                swatch = itemView.findViewById(R.id.pick_swatch);
            }
        }
    }

    /** The pictures under the calendar: one tile per date, two to a row. */
    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<Cell> entries = new ArrayList<>();

        void submit(List<Cell> all) {
            entries.clear();
            entries.addAll(all);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_daily_image, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(entries.get(position));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView date;
            final View frame;

            Holder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.tile_image);
                date = itemView.findViewById(R.id.tile_date);
                frame = itemView.findViewById(R.id.tile_frame);

                // Square tiles at a known size, set now rather than after layout.
                ViewGroup.LayoutParams lp = frame.getLayoutParams();
                lp.height = tileSize;
                frame.setLayoutParams(lp);
            }

            void bind(Cell cell) {
                // The heading above the grid names the date; a badge saying the same number on
                // every tile only looked like a bug.
                date.setVisibility(View.GONE);
                itemView.setAlpha(cell.isFuture ? 0.45f : 1f);

                com.colourmate.paintbynumber.model.model.Level level =
                        cell.level != null ? cell.level : levelForDate(cell.date);
                String url = thumbnailUrl(level);
                if (url != null) {
                    // Picking a date whose category is not cached leaves several seconds before the
                    // thumbnails arrive. Without a placeholder that reads as an empty grid rather
                    // than a loading one — and the recycled view could still show the last date's
                    // picture under the new date's badge.
                    com.squareup.picasso.Picasso.get()
                            .load(url)
                            .resize(tileSize, tileSize)
                            .centerCrop()
                            .placeholder(R.drawable.cm_cal_cell)
                            .into(image);
                } else {
                    image.setImageDrawable(null);
                    image.setBackgroundColor(colourFor(cell.imageIndex));
                }

                itemView.setOnClickListener(v -> openGame(cell));
            }
        }
    }
}
