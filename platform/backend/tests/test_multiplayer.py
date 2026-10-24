"""Multiplayer end to end through the real routes (TestClient, temp DB, one published picture).

    python -m pytest tests/test_multiplayer.py      (or just: python tests/test_multiplayer.py)
"""
import os
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import db  # noqa: E402
import multiplayer  # noqa: E402

FILE = "20261024_test_picture"


def _client(monkeypatch=None):
    from fastapi.testclient import TestClient
    import app
    # Pretend one picture is published; the rest of the DB is untouched.
    multiplayer.db.iid_for_file_name = lambda f: "abc" if f == FILE else None
    return TestClient(app.app)


def test_challenge():
    c = _client()
    r = c.post("/index.php/App/mp/challenge",
               json={"fileName": FILE, "category": "animals", "device": "deviceA1", "name": "Riya",
                     "ms": 240000})
    assert r.status_code == 200, r.text
    code = r.json()["code"]
    assert len(code) == 6

    info = c.get(f"/index.php/App/mp/{code.lower()}").json()   # codes are case-insensitive
    assert info["kind"] == "challenge" and info["fileName"] == FILE and info["ms"] == 240000

    r = c.post(f"/index.php/App/mp/challenge/{code}/result",
               json={"device": "deviceB2", "name": "Kishan", "ms": 200000}).json()
    assert r["rank"] == 1 and r["beat"] is True
    # A slower second try keeps the best time.
    r = c.post(f"/index.php/App/mp/challenge/{code}/result",
               json={"device": "deviceB2", "name": "Kishan", "ms": 300000}).json()
    assert [p["ms"] for p in r["board"]] == [200000, 240000]

    assert c.post("/index.php/App/mp/challenge",
                  json={"fileName": "nope", "category": "x", "device": "deviceA1",
                        "ms": 240000}).status_code == 400
    assert c.post("/index.php/App/mp/challenge",
                  json={"fileName": FILE, "category": "x", "device": "deviceA1",
                        "ms": 10}).status_code == 400
    assert c.get("/index.php/App/mp/ZZZZZZ").status_code == 404
    assert c.post("/index.php/App/mp/challenge/ZZZZZZ/result",
                  json={"device": "deviceB2", "ms": 200000}).status_code == 404


def _room(c, mode):
    r = c.post("/index.php/App/mp/room",
               json={"mode": mode, "fileName": FILE, "category": "animals", "device": "deviceA1"})
    assert r.status_code == 200, r.text
    return r.json()["code"]


def test_coop():
    c = _client()
    code = _room(c, "coop")
    assert c.get(f"/index.php/App/mp/{code}").json()["kind"] == "coop"
    with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceA1&name=Riya") as a:
        sa = a.receive_json()
        assert sa["t"] == "state" and sa["mode"] == "coop" and sa["filled"] == []
        assert a.receive_json()["t"] == "players"
        with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceB2&name=Kishan") as b:
            assert b.receive_json()["t"] == "state"
            assert b.receive_json()["t"] == "players"
            joined = a.receive_json()
            assert joined["t"] == "players" and len(joined["players"]) == 2
            assert joined["players"][0]["colour"] != joined["players"][1]["colour"]

            a.send_json({"t": "fill", "id": 40})
            got = b.receive_json()
            assert got == {"t": "fill", "id": 40, "by": sa["you"]}
            a.send_json({"t": "fill", "id": 40})      # duplicate: nobody hears it
            b.send_json({"t": "fill", "id": 7})
            assert a.receive_json()["id"] == 7

        # B left; A hears the roster change. A late joiner gets everything filled so far.
        assert a.receive_json()["t"] == "players"
        with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceC3&name=Dev") as d:
            assert d.receive_json()["filled"] == [7, 40]


def test_race():
    c = _client()
    code = _room(c, "race")
    with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceA1&name=Riya") as a:
        a.receive_json(); a.receive_json()
        a.send_json({"t": "start"})                     # alone: refused, nothing comes back
        with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceB2&name=Kishan") as b:
            b.receive_json(); b.receive_json(); a.receive_json()
            b.send_json({"t": "start"})                 # not the host: refused
            a.send_json({"t": "start"})
            sa, sb = a.receive_json(), b.receive_json()
            assert sa["t"] == sb["t"] == "start" and sa["at"] > sa["now"]
            assert c.get(f"/index.php/App/mp/{code}").json()["started"] is True

            b.send_json({"t": "progress", "pct": 55})
            assert a.receive_json() == {"t": "progress", "id": multiplayer._pid("deviceB2"), "pct": 55}
            b.send_json({"t": "done"})
            da, db_ = a.receive_json(), b.receive_json()
            assert da["t"] == "done" and da["rank"] == 1 and da["name"] == "Kishan"
            a.send_json({"t": "done"})
            assert b.receive_json()["rank"] == 2


def test_room_limits():
    c = _client()
    code = _room(c, "coop")
    socks = []
    for i in range(multiplayer.MAX_PLAYERS):
        ws = c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=device{i}xx&name=P{i}")
        socks.append(ws.__enter__())
    from starlette.websockets import WebSocketDisconnect
    try:
        with c.websocket_connect(f"/index.php/App/mp/{code}/ws?device=deviceFULL&name=X") as extra:
            extra.receive_json()
        assert False, "a fifth player got in"
    except WebSocketDisconnect as e:
        assert e.code == 4409
    for ws in socks:
        ws.__exit__(None, None, None)
    try:
        with c.websocket_connect("/index.php/App/mp/QQQQQQ/ws?device=deviceA1") as w:
            w.receive_json()
        assert False
    except WebSocketDisconnect as e:
        assert e.code == 4404
    assert c.post("/index.php/App/mp/room", json={"mode": "battle", "fileName": FILE,
                                                   "device": "deviceA1"}).status_code == 400


def test_join_page():
    c = _client()
    html = c.get("/j/ab-c2d3").text
    assert "ABC2D3" in html and "scheme=colourmate" in html


if __name__ == "__main__":
    tmp = tempfile.mkdtemp()
    os.chdir(tmp)
    for fn in [test_challenge, test_coop, test_race, test_room_limits, test_join_page]:
        fn()
        print("ok", fn.__name__)
