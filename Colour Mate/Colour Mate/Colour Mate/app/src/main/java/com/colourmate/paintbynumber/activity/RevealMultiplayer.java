package com.colourmate.paintbynumber.activity;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.colourmate.paintbynumber.R;
import com.colourmate.paintbynumber.model.reveal.RevealLevel;
import com.colourmate.paintbynumber.model.reveal.RevealView;
import com.colourmate.paintbynumber.util.Multiplayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The multiplayer half of the reveal screen, so {@link RevealActivity} keeps reading as the
 * solo game it mostly is.
 *
 * <ul>
 *   <li><b>challenge</b>: a clock runs; on finishing, the time goes to the server and the
 *       sheet says whether the friend's time was beaten.</li>
 *   <li><b>race</b>: a lobby until the host starts it (the board pans but does not fill), a
 *       3-2-1 countdown on the server's clock, everyone's percent in the status pill.</li>
 *   <li><b>coop</b>: every fill is sent to the room and every friend's fill arrives here.</li>
 * </ul>
 */
final class RevealMultiplayer implements Multiplayer.RoomListener {

    private static final long TICK_MS = 500;

    private final RevealActivity activity;
    private final RevealView board;
    private final String mode;
    private final String code;
    private final TextView status;
    private final View lobby;
    private final TextView lobbyTitle, lobbyCode, lobbyPlayers;
    private final Button lobbyStart;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Multiplayer.Room room;
    private RevealLevel level;

    // Challenge: the time to beat.
    private final long targetMs;
    private final String targetBy;

    // Live rooms.
    private final Map<String, JSONObject> players = new LinkedHashMap<>();
    private String you, host;
    private long startAtServer = -1;      // race start, server epoch ms
    private long clockOffset;             // server ms - local wall ms
    private boolean connected, lobbyDismissed;
    private int lastSentPct = -1;
    private boolean finishedHere;
    private final List<String> finishLines = new ArrayList<>();

    // Clock: a race is timed from its start, a challenge from the moment the picture is shown.
    private long clockStart = -1;         // SystemClock.elapsedRealtime
    private long finishedMs = -1;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            main.postDelayed(this, TICK_MS);
        }
    };

    RevealMultiplayer(RevealActivity activity, RevealView board, String mode, String code,
                      long targetMs, String targetBy) {
        this.activity = activity;
        this.board = board;
        this.mode = mode;
        this.code = code;
        this.targetMs = targetMs;
        this.targetBy = targetBy;
        status = activity.findViewById(R.id.tv_mp_status);
        lobby = activity.findViewById(R.id.fl_mp_wait);
        lobbyTitle = activity.findViewById(R.id.tv_mp_wait_title);
        lobbyCode = activity.findViewById(R.id.tv_mp_code);
        lobbyPlayers = activity.findViewById(R.id.tv_mp_players);
        lobbyStart = activity.findViewById(R.id.btn_mp_start);

        status.setVisibility(View.VISIBLE);
        status.setOnClickListener(v -> {
            if (!Multiplayer.CHALLENGE.equals(mode)) Multiplayer.share(activity, code, mode);
        });
        activity.findViewById(R.id.btn_mp_invite)
                .setOnClickListener(v -> Multiplayer.share(activity, code, mode));
        lobbyStart.setOnClickListener(v -> onStartPressed());
    }

    boolean isChallenge() { return Multiplayer.CHALLENGE.equals(mode); }
    boolean isCoop() { return Multiplayer.COOP.equals(mode); }
    boolean isRace() { return Multiplayer.RACE.equals(mode); }

    /** The picture is on screen. */
    void start(RevealLevel level) {
        this.level = level;
        if (isChallenge()) {
            clockStart = SystemClock.elapsedRealtime();
        } else {
            board.setFillEnabled(isCoop());
            lobbyCode.setText(code);
            lobby.setVisibility(View.VISIBLE);
            room = new Multiplayer.Room(activity, code, this);
        }
        main.post(tick);
    }

    void stop() {
        main.removeCallbacks(tick);
        if (room != null) room.close();
    }

    // ─── From the board ───

    void onFilled(RevealLevel.Region region, boolean byFriend) {
        if (isCoop() && !byFriend && room != null) room.send("fill", "id", region.id);
    }

    void onProgress(int pct) {
        if (room == null || pct == lastSentPct) return;
        lastSentPct = pct;
        room.send("progress", "pct", pct);
    }

    /** The picture was completed here. Fills the finish sheet's text as answers arrive. */
    void onFinished() {
        if (finishedHere) return;
        finishedHere = true;
        finishedMs = elapsed();
        if (isChallenge()) {
            activity.setDoneText(activity.getString(R.string.reveal_done_title),
                    "Your time: " + Multiplayer.formatTime(finishedMs) + " · sending…");
            Multiplayer.postResult(activity, code, Math.max(5000, finishedMs), r -> {
                if (r == null) {
                    activity.setDoneText("Time: " + Multiplayer.formatTime(finishedMs),
                            activity.getString(R.string.mp_no_server));
                    return;
                }
                boolean beat = r.optBoolean("beat");
                activity.setDoneText(beat ? "You beat " + targetBy + "! 🏆" : targetBy + " was faster 💪",
                        "Your time " + Multiplayer.formatTime(finishedMs) + " · #" + r.optInt("rank")
                                + "\n" + board(r.optJSONArray("board")));
            });
        } else {
            if (room != null) room.send("done", null, null);
            activity.setDoneText(isCoop() ? "Finished together! 🤝" : "Finished! 🏁",
                    isRace() ? "Time " + Multiplayer.formatTime(finishedMs) : finishSummary());
        }
        render();
    }

    // ─── From the room ───

    @Override
    public void onMessage(JSONObject m) {
        switch (m.optString("t")) {
            case "state":
                connected = true;
                you = m.optString("you");
                host = m.optString("host");
                clockOffset = m.optLong("now") - System.currentTimeMillis();
                setPlayers(m.optJSONArray("players"));
                if (isCoop()) {
                    JSONArray filled = m.optJSONArray("filled");
                    if (filled != null && filled.length() > 0) {
                        int[] ids = new int[filled.length()];
                        for (int i = 0; i < ids.length; i++) ids[i] = filled.optInt(i);
                        board.revealSilently(ids);
                    }
                    // A friend joining an already-busy picture goes straight to colouring.
                    if (!you.equals(host) || (filled != null && filled.length() > 0)) dismissLobby();
                }
                if (!m.isNull("startAt")) onRaceStart(m.optLong("startAt"));
                break;
            case "players":
                setPlayers(m.optJSONArray("players"));
                break;
            case "fill":
                board.revealByFriend(m.optInt("id"), colourOf(m.optString("by")));
                break;
            case "progress": {
                JSONObject p = players.get(m.optString("id"));
                if (p != null) try { p.put("pct", m.optInt("pct")); } catch (Exception ignored) { }
                break;
            }
            case "start":
                clockOffset = m.optLong("now") - System.currentTimeMillis();
                onRaceStart(m.optLong("at"));
                break;
            case "done": {
                String who = m.optString("id");
                JSONObject p = players.get(who);
                if (p != null) try { p.put("pct", 100); } catch (Exception ignored) { }
                String line = "#" + m.optInt("rank") + " " + (who.equals(you) ? "You" : m.optString("name"))
                        + (isRace() ? " · " + Multiplayer.formatTime(m.optLong("ms")) : "");
                finishLines.add(line);
                if (!who.equals(you) && isRace()) {
                    Toast.makeText(activity, m.optString("name") + " finished #" + m.optInt("rank"),
                            Toast.LENGTH_SHORT).show();
                }
                if (finishedHere && isRace()) {
                    activity.setDoneText(rankTitle(), "Time " + Multiplayer.formatTime(finishedMs)
                            + "\n" + finishSummary());
                }
                break;
            }
            default:
                break;
        }
        render();
    }

    @Override
    public void onDisconnected(boolean permanent, int closeCode) {
        connected = false;
        if (permanent) {
            int msg = closeCode == 4409 ? R.string.mp_room_full
                    : closeCode == 4404 ? R.string.mp_bad_code : R.string.mp_no_server;
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            if (closeCode == 4404 || closeCode == 4409) activity.finish();
        }
        render();
    }

    // ─── Race ───

    private void onStartPressed() {
        if (isCoop()) {
            dismissLobby();
            return;
        }
        if (room != null && you != null && you.equals(host) && onlineCount() >= 2) {
            room.send("start", null, null);
        }
    }

    private void onRaceStart(long atServer) {
        if (!isRace() || startAtServer > 0) return;
        startAtServer = atServer;
        long wait = atServer - serverNow();
        main.postDelayed(() -> {
            board.setFillEnabled(true);
            clockStart = SystemClock.elapsedRealtime();
            dismissLobby();
            render();
        }, Math.max(0, wait));
    }

    private void dismissLobby() {
        lobbyDismissed = true;
        lobby.setVisibility(View.GONE);
    }

    // ─── Rendering ───

    private void render() {
        if (isChallenge()) {
            long t = finishedMs >= 0 ? finishedMs : elapsed();
            status.setText("⏱ " + Multiplayer.formatTime(t) + "   Beat " + targetBy + " "
                    + Multiplayer.formatTime(targetMs));
            return;
        }

        StringBuilder line = new StringBuilder();
        if (!connected) line.append("Reconnecting… ");
        for (JSONObject p : players.values()) {
            if (line.length() > 0 && line.charAt(line.length() - 1) != ' ') line.append("  ");
            String id = p.optString("id");
            line.append(id.equals(you) ? "You" : p.optString("name"));
            if (isRace()) line.append(' ').append(p.optInt("pct")).append('%');
            if (!p.optBoolean("online")) line.append(" (away)");
        }
        if (isRace() && clockStart >= 0) {
            line.append("\n⏱ ").append(Multiplayer.formatTime(finishedMs >= 0 ? finishedMs : elapsed()));
        }
        status.setText(line.length() == 0 ? "Code " + code : line.toString());

        if (lobby.getVisibility() == View.VISIBLE) renderLobby();
    }

    private void renderLobby() {
        boolean isHost = you != null && you.equals(host);
        StringBuilder names = new StringBuilder();
        for (JSONObject p : players.values()) {
            if (!p.optBoolean("online")) continue;
            if (names.length() > 0) names.append('\n');
            names.append("● ").append(p.optString("id").equals(you) ? "You" : p.optString("name"));
            if (p.optString("id").equals(host)) names.append("  (host)");
        }
        lobbyPlayers.setText(names.length() == 0 ? "Connecting…" : names);

        if (isCoop()) {
            lobbyTitle.setText(R.string.mp_coop);
            lobbyStart.setText(R.string.mp_start_coop);
            lobbyStart.setEnabled(true);
            lobbyStart.setAlpha(1f);
            return;
        }
        lobbyTitle.setText(R.string.mp_race);
        if (startAtServer > 0) {
            long left = startAtServer - serverNow();
            lobbyStart.setText(left > 0 ? "Starting in " + (left / 1000 + 1) + "…" : "Go!");
            lobbyStart.setEnabled(false);
            lobbyStart.setAlpha(1f);
        } else if (isHost) {
            boolean ready = onlineCount() >= 2;
            lobbyStart.setText(ready ? activity.getString(R.string.mp_start_race) : "Waiting for a friend…");
            lobbyStart.setEnabled(ready);
            lobbyStart.setAlpha(ready ? 1f : 0.5f);
        } else {
            lobbyStart.setText(R.string.mp_waiting_host);
            lobbyStart.setEnabled(false);
            lobbyStart.setAlpha(0.5f);
        }
    }

    // ─── Helpers ───

    private void setPlayers(JSONArray list) {
        if (list == null) return;
        players.clear();
        for (int i = 0; i < list.length(); i++) {
            JSONObject p = list.optJSONObject(i);
            if (p != null) players.put(p.optString("id"), p);
        }
    }

    private int onlineCount() {
        int n = 0;
        for (JSONObject p : players.values()) if (p.optBoolean("online")) n++;
        return n;
    }

    private int colourOf(String id) {
        JSONObject p = players.get(id);
        try {
            return Color.parseColor(p == null ? "#7A5CFF" : p.optString("colour", "#7A5CFF"));
        } catch (IllegalArgumentException e) {
            return 0xFF7A5CFF;
        }
    }

    private long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    private long elapsed() {
        return clockStart < 0 ? 0 : SystemClock.elapsedRealtime() - clockStart;
    }

    private String rankTitle() {
        for (String l : finishLines) {
            if (l.contains(" You")) return l.startsWith("#1 ") ? "You won the race! 🏆" : "You finished " + l.split(" ")[0];
        }
        return "Finished! 🏁";
    }

    private String finishSummary() {
        StringBuilder s = new StringBuilder();
        for (String l : finishLines) {
            if (s.length() > 0) s.append('\n');
            s.append(l);
        }
        return s.toString();
    }

    private static String board(JSONArray rows) {
        if (rows == null) return "";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < Math.min(5, rows.length()); i++) {
            JSONObject r = rows.optJSONObject(i);
            if (r == null) continue;
            if (s.length() > 0) s.append('\n');
            s.append(i + 1).append(". ").append(r.optString("name")).append("  ")
                    .append(Multiplayer.formatTime(r.optLong("ms")));
        }
        return s.toString();
    }
}
