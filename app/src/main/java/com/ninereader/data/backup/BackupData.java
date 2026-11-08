package com.ninereader.data.backup;

import java.util.ArrayList;
import java.util.List;

/** JSON model of a user data backup (BIB-05): bookmarks, collections and statistics. */
public class BackupData {

    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public long createdAt;
    public String app = "NineReader";
    public List<Doc> documents = new ArrayList<>();
    public List<Collection> collections = new ArrayList<>();

    /** A document reference with its reading state and bookmarks. */
    public static class Doc {
        public String uri;
        public String name;
        public String hash;
        public long size;
        public int pageCount;
        public int lastPage;
        public float lastZoom = 1f;
        public String readingMode;
        public long lastOpened;
        public boolean favorite;
        public List<Bookmark> bookmarks = new ArrayList<>();
        public List<Session> sessions = new ArrayList<>();
        public List<String> collections = new ArrayList<>();
    }

    /** A bookmark. */
    public static class Bookmark {
        public int page;
        public String label;
        public long createdAt;
    }

    /** A reading session. */
    public static class Session {
        public long start;
        public long end;
        public int pagesRead;
    }

    /** A collection. */
    public static class Collection {
        public String name;
        public int color;
    }
}
