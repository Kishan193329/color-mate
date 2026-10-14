package com.colourmate.paintbynumber.model;

public class SongModel {
    String songName;
    String songPath;

    public SongModel(String songName, String songPath) {
        this.songName = songName;
        this.songPath = songPath;
    }

    public String getName() {
        return this.songName;
    }

    public void setName(String name) {
        this.songName = name;
    }

    public String getPath() {
        return this.songPath;
    }

    public void setPath(String path) {
        this.songPath = path;
    }
}
