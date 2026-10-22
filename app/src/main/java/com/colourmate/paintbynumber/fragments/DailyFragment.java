package com.colourmate.paintbynumber.fragments;

import android.content.Intent;
import android.graphics.Color;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.Utils.Manager;
import com.colourmate.paintbynumber.Utils.ColorForgeApi;
import com.colourmate.paintbynumber.activity.GameActivity;
import com.colourmate.paintbynumber.activity.RevealActivity;
import com.colourmate.paintbynumber.model.model.Level;
import com.colourmate.paintbynumber.util.DailyChallengeManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Daily Challenge as a Home tab (2026-10-14). A port of {@code DailyCalendarActivity} minus
 * its toolbar and the unused image picker — the shared top bar and bottom nav frame it now.
 *
 * <p>Streak card → month calendar → the selected date's pictures. A date's category and the
 * order of its pictures are pure functions of the date ({@link DailyChallengeManager}), so every
 * device shows the same challenge on the same day. Finishing a picture opened from here marks the
 * day done via {@link GameActivity#EXTRA_DAILY_DATE}.
 */
public class DailyFragment extends Fragment {

    private static final int COLUMNS = 7;
    /** Pictures offered for one date — this grid inflates every tile, so it must stay small. */
    private static final int DAILY_PICK_COUNT = 12;

    private final Calendar shownMonth = Calendar.getInstance();
    private final List<Cell> cells = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<Level> categoryLevels = new ArrayList<>();
    /** Our own published pictures (admin -> Daily feed), each tagged with its publish date. */
    private final List<Level> ourLevels = new ArrayList<>();
    private Calendar selectedDate = Calendar.getInstance();
    private String selectedCategory;

    private View root;
    private DayAdapter adapter;
    private RowAdapter rowAdapter;
    private int tileSize;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.fragment_daily, container, false);

        root.findViewById(R.id.cal_prev).setOnClickListener(v -> stepMonth(-1));
        root.findViewById(R.id.cal_next).setOnClickListener(v -> stepMonth(1));
        root.findViewById(R.id.cal_play_today).setOnClickListener(v -> playToday());

        buildWeekdayHeader();

        RecyclerView grid = root.findViewById(R.id.cal_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        adapter = new DayAdapter();
        grid.setAdapter(adapter);

        tileSize = tileSize();

        RecyclerView rows = root.findViewById(R.id.cal_list);
        rows.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        rowAdapter = new RowAdapter();
        rows.setAdapter(rowAdapter);

        bindStreak();
        rebuildMonth();
        loadCategories();
        loadOurPictures();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    /** MainActivity show/hides tabs rather than replacing them, so this is the "came back" hook. */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) refresh();
    }

    /** After a finished picture: tick, streak and total have all moved. */
    private void refresh() {
        if (root == null) return;
        bindStreak();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void bindStreak() {
        int streak = DailyChallengeManager.getDailyStreak(requireContext());
        TextView streakView = root.findViewById(R.id.cal_streak);
        TextView subView = root.findViewById(R.id.cal_streak_sub);
        TextView countdown = root.findViewById(R.id.cal_countdown);
        TextView total = root.findViewById(R.id.cal_total);

        streakView.setText(getResources().getQuantityString(R.plurals.daily_streak_days, streak, streak));
        subView.setText(DailyChallengeManager.isTodayCompleted(requireContext())
                ? getString(R.string.daily_done_today)
                : getString(R.string.daily_not_done_today));
        countdown.setText(DailyChallengeManager.getCountdownText());
        total.setText(String.valueOf(DailyChallengeManager.getTotalCompleted(requireContext())));
    }

    /** The hero button: today's first picture, straight into the game. */
    private void playToday() {
        selectedDate = Calendar.getInstance();
        if (categoryLevels.isEmpty()) {
            Toast.makeText(requireContext(), R.string.daily_loading_pictures, Toast.LENGTH_SHORT).show();
            applyCategoryForSelectedDate();
            rebuildMonth();
            return;
        }
        applyCategoryForSelectedDate();
        rebuildMonth();
        List<Cell> entries = buildSelectedDateEntries();
        if (entries.isEmpty()) {
            Toast.makeText(requireContext(), R.string.daily_no_library, Toast.LENGTH_SHORT).show();
            return;
        }
        openGame(entries.get(0));
    }

    private void stepMonth(int delta) {
        shownMonth.add(Calendar.MONTH, delta);
        rebuildMonth();
    }

    /** Lays out one month: blanks for the days before the 1st, then every date. */
    private void rebuildMonth() {
        cells.clear();

        Calendar cursor = (Calendar) shownMonth.clone();
        cursor.set(Calendar.DAY_OF_MONTH, 1);

        int firstWeekday = cursor.get(Calendar.DAY_OF_WEEK);
        int leadingBlanks = (firstWeekday - cursor.getFirstDayOfWeek() + 7) % 7;
        for (int i = 0; i < leadingBlanks; i++) cells.add(Cell.blank());

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
            cells.add(cell);
        }

        TextView month = root.findViewById(R.id.cal_month);
        month.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(shownMonth.getTime()));

        TextView footer = root.findViewById(R.id.cal_footer);
        footer.setText(categoryLevels.isEmpty()
                ? getString(R.string.daily_no_library)
                : getString(R.string.daily_footer, categoryLevels.size()));

        TextView listHeader = root.findViewById(R.id.cal_list_header);
        TextView listSub = root.findViewById(R.id.cal_list_sub);
        if (selectedDate != null) {
            String dateLabel = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(selectedDate.getTime());
            listHeader.setText(getString(R.string.daily_pictures_for, dateLabel));
            listSub.setText(categoryLevels.isEmpty()
                    ? getString(R.string.daily_loading_pictures)
                    : getString(R.string.daily_pictures_sub, capitalise(selectedCategory),
                            Math.min(DAILY_PICK_COUNT, categoryLevels.size())));
        }

        adapter.notifyDataSetChanged();
        if (rowAdapter != null) rowAdapter.submit(buildSelectedDateEntries());
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Half the width less gutters and tile margins — computed, because Picasso's fit() waits for layout. */
    private int tileSize() {
        float density = getResources().getDisplayMetrics().density;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int gutters = Math.round(2 * 18 * density);
        int margins = Math.round(4 * 4 * density);
        return Math.max(1, (screen - gutters - margins) / 2);
    }

    /** The selected date's pictures — that date's own seeded arrangement of its category, first N. */
    private List<Cell> buildSelectedDateEntries() {
        List<Cell> entries = new ArrayList<>();
        if (selectedDate == null) return entries;

        Calendar today = Calendar.getInstance();
        boolean isToday = sameDate(selectedDate, today);
        boolean isFuture = selectedDate.after(today) && !isToday;
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        // Owner's rule: the pictures published FOR this date come first.
        String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDate.getTime());
        if (!isFuture) {
            for (Level level : ourLevels) {
                if (!key.equals(level.getTimeStamp())) continue;
                Cell cell = new Cell();
                cell.day = day;
                cell.date = (Calendar) selectedDate.clone();
                cell.isToday = isToday;
                cell.isFuture = false;
                cell.level = level;
                entries.add(cell);
            }
        }
        if (categoryLevels.isEmpty()) return entries;
        int ours = entries.size();

        for (Level level : arrangeForDate(selectedDate)) {
            if (entries.size() - ours >= DAILY_PICK_COUNT) break;
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

    private List<Level> arrangeForDate(Calendar date) {
        List<Level> ordered = new ArrayList<>(categoryLevels);
        Random random = new Random(date.get(Calendar.YEAR) * 10000L
                + date.get(Calendar.MONTH) * 100L + date.get(Calendar.DAY_OF_MONTH));
        for (int i = ordered.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            if (i != j) Collections.swap(ordered, i, j);
        }
        return ordered;
    }

    private static boolean sameDate(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void buildWeekdayHeader() {
        LinearLayout header = root.findViewById(R.id.cal_weekdays);
        header.removeAllViews();
        String[] shortNames = new SimpleDateFormat("EEE", Locale.getDefault())
                .getDateFormatSymbols().getShortWeekdays();
        int first = Calendar.getInstance().getFirstDayOfWeek();
        for (int i = 0; i < COLUMNS; i++) {
            int weekday = ((first - 1 + i) % 7) + 1;
            TextView label = new TextView(requireContext());
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            label.setGravity(Gravity.CENTER);
            label.setText(shortNames[weekday].toUpperCase(Locale.getDefault()));
            label.setTextSize(10f);
            label.setTextColor(ContextCompat.getColor(requireContext(), R.color.cm_ink_3));
            label.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.quicksand_bold));
            header.addView(label);
        }
    }

    // ─── Cell model ───

    private static final class Cell {
        int day;                 // 0 = blank padding
        boolean isToday, isFuture, isSelected;
        Calendar date;
        Level level;             // on the tiles under the calendar

        static Cell blank() { return new Cell(); }
        boolean isBlank() { return day == 0; }
    }

    // ─── Calendar grid ───

    private final class DayAdapter extends RecyclerView.Adapter<DayAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(cells.get(position)); }
        @Override public int getItemCount() { return cells.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            private final View frame, tint;
            private final ImageView image, done;
            private final TextView dayLabel;

            Holder(@NonNull View itemView) {
                super(itemView);
                frame = itemView.findViewById(R.id.cell_frame);
                image = itemView.findViewById(R.id.cell_image);
                tint = itemView.findViewById(R.id.cell_tint);
                dayLabel = itemView.findViewById(R.id.cell_day);
                done = itemView.findViewById(R.id.cell_done);
                itemView.post(() -> {           // square cells
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
                image.setImageDrawable(null);
                tint.setBackgroundColor(Color.TRANSPARENT);

                // State is the only thing that colours a cell: today = solid violet / white
                // number, finished = violet wash + tick, past = white, future = ghost.
                boolean completed = DailyChallengeManager.isCompleted(requireContext(), cell.date);
                int bg, fg;
                if (cell.isToday) {
                    bg = R.drawable.cm_cal_cell_today;  fg = R.color.white;
                } else if (completed) {
                    bg = R.drawable.cm_cal_cell_done;   fg = R.color.cm_violet;
                } else if (cell.isFuture) {
                    bg = R.drawable.cm_cal_cell_future; fg = R.color.cm_ink_3;
                } else {
                    bg = R.drawable.cm_cal_cell;        fg = R.color.cm_ink;
                }
                frame.setBackgroundResource(bg);
                dayLabel.setTextColor(ContextCompat.getColor(requireContext(), fg));
                // Today is already the loudest cell; the ring is for picking some other day.
                frame.setForeground(cell.isSelected && !cell.isToday
                        ? ContextCompat.getDrawable(requireContext(), R.drawable.cm_cal_cell_selected) : null);
                itemView.setAlpha(1f);
                done.setVisibility(completed && !cell.isToday ? View.VISIBLE : View.GONE);

                itemView.setOnClickListener(cell.isFuture ? null : v -> openDay(cell));
                itemView.setClickable(!cell.isFuture);
            }
        }
    }

    private void openDay(Cell cell) {
        if (cell == null || cell.date == null) return;
        selectedDate = (Calendar) cell.date.clone();
        applyCategoryForSelectedDate();
        rebuildMonth();
    }

    /** Straight into the ordinary By Number game, tagged so finishing it marks this date done. */
    private void openGame(Cell cell) {
        Level level = cell.level != null ? cell.level : levelForDate(cell.date);
        if (level == null) {
            Toast.makeText(requireContext(), R.string.daily_no_library, Toast.LENGTH_SHORT).show();
            return;
        }
        if (level.isColorForge()) {
            String base = ColorForgeApi.revealBaseUrl(requireContext(), level.getCategory(), level.getFileName());
            if (base.isEmpty()) return;
            Intent reveal = RevealActivity.fromUrl(requireContext(), base, level.getFileName(),
                    new String[0], new String[0]);
            reveal.putExtra(GameActivity.EXTRA_DAILY_DATE, DailyChallengeManager.dateKey(cell.date));
            startActivity(reveal);
            return;
        }
        Intent intent = GameActivity.newIntent(requireContext(), level);
        intent.putExtra(GameActivity.EXTRA_DAILY_DATE, DailyChallengeManager.dateKey(cell.date));
        startActivity(intent);
    }

    // ─── By Number data (same endpoints as the By Number tab) ───

    /** Our admin's pictures, date-wise. A failure just leaves the Daily tab as it was. */
    private void loadOurPictures() {
        if (!ColorForgeApi.hasExplicitServer(requireContext())) return;
        String root = ColorForgeApi.restRoot(requireContext());
        Manager.getInstance().executeApiRequestWithCall(root, "daily/", json -> {
            if (!isAdded() || json == null || !json.has("data")) return;
            ourLevels.clear();
            com.google.gson.JsonArray data = json.getAsJsonArray("data");
            for (int i = 0; i < data.size(); i++) {
                Level level = Level.fromJson(data.get(i).getAsJsonObject());
                if (level == null || level.getFileName() == null || level.getFileName().isEmpty()) continue;
                level.setColorForge(true);
                ourLevels.add(level);
            }
            rebuildMonth();
        }, call -> { });
    }

    private void loadCategories() {
        Manager.getInstance().executeApiRequest("category/", json -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                categories.clear();
                if (json != null && json.has("categories")) {
                    com.google.gson.JsonArray arr = json.getAsJsonArray("categories");
                    for (int i = 0; i < arr.size(); i++) {
                        String c = arr.get(i).getAsString();
                        if (c != null && !c.isEmpty()) categories.add(c);
                    }
                }
                applyCategoryForSelectedDate();
            });
        });
    }

    /** The selected date picks the category — deterministically, same on every device. */
    private void applyCategoryForSelectedDate() {
        if (categories.isEmpty()) return;
        int index = DailyChallengeManager.getChallengeIndexFor(selectedDate, categories.size());
        String category = categories.get(index);
        if (category.equals(selectedCategory) && !categoryLevels.isEmpty()) {
            rebuildMonth();
            return;
        }
        selectedCategory = category;
        categoryLevels.clear();          // honest empty grid while the new category loads
        loadLevelsFor(category);
    }

    private void loadLevelsFor(final String category) {
        Manager.getInstance().executeApiRequest("gimg/c/" + category, json -> {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || !category.equals(selectedCategory)) return;   // a slower, older fetch
                categoryLevels.clear();
                if (json != null && json.has("data")) {
                    com.google.gson.JsonArray data = json.getAsJsonArray("data");
                    for (int i = 0; i < data.size(); i++) {
                        Level level = Level.fromJson(data.get(i).getAsJsonObject());
                        if (level != null && level.getFileName() != null && !level.getFileName().isEmpty()) {
                            categoryLevels.add(level);
                        }
                    }
                }
                rebuildMonth();
            });
        });
    }

    private Level levelForDate(Calendar date) {
        if (categoryLevels.isEmpty()) return null;
        return categoryLevels.get(DailyChallengeManager.getChallengeIndexFor(date, categoryLevels.size()));
    }

    private String thumbnailUrl(Level level) {
        if (level == null) return null;
        return Manager.BASE_URL + "thumb/" + level.getCategory() + "/t/" + level.getFileName() + ".png";
    }

    // ─── The pictures under the calendar ───

    private final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<Cell> entries = new ArrayList<>();

        void submit(List<Cell> all) {
            entries.clear();
            entries.addAll(all);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_daily_image, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) { holder.bind(entries.get(position)); }
        @Override public int getItemCount() { return entries.size(); }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView date;
            final View frame;

            Holder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.tile_image);
                date = itemView.findViewById(R.id.tile_date);
                frame = itemView.findViewById(R.id.tile_frame);
                ViewGroup.LayoutParams lp = frame.getLayoutParams();
                lp.height = tileSize;
                frame.setLayoutParams(lp);
            }

            void bind(Cell cell) {
                date.setVisibility(View.GONE);      // the heading names the date
                itemView.setAlpha(cell.isFuture ? 0.45f : 1f);
                Level shown = cell.level != null ? cell.level : levelForDate(cell.date);
                boolean ours = shown != null && shown.isColorForge();
                image.setBackgroundColor(ours ? Color.WHITE : Color.TRANSPARENT);   // line art is transparent
                String url = ours
                        ? ColorForgeApi.borderThumbUrl(requireContext(), shown.getCategory(), shown.getFileName())
                        : thumbnailUrl(shown);
                if (url != null) {
                    com.squareup.picasso.Picasso.get().load(url)
                            .resize(tileSize, tileSize).centerCrop()
                            .placeholder(R.drawable.cm_cal_cell).into(image);
                } else {
                    image.setImageDrawable(null);
                }
                itemView.setOnClickListener(v -> openGame(cell));
            }
        }
    }
}
