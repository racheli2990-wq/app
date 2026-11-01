package com.racheli.offlinenotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class NotesDb extends SQLiteOpenHelper {
    NotesDb(Context context) { super(context, "offline_notes.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, image_path TEXT, audio_path TEXT, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX notes_updated_at_idx ON notes(updated_at DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    List<Note> all() {
        List<Note> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("notes",null,null,null,null,null,"updated_at DESC")) {
            while(c.moveToNext()) result.add(read(c));
        }
        return result;
    }
    Note get(long id) {
        try(Cursor c=getReadableDatabase().query("notes",null,"id=?",new String[]{String.valueOf(id)},null,null,null)) {
            return c.moveToFirst()?read(c):null;
        }
    }
    long save(Note n) {
        ContentValues v=new ContentValues();
        v.put("title",n.title); v.put("body",n.body); v.put("image_path",n.imagePath);
        v.put("audio_path",n.audioPath); v.put("updated_at",System.currentTimeMillis());
        if(n.id>0) { getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(n.id)}); return n.id; }
        return getWritableDatabase().insertOrThrow("notes",null,v);
    }
    void delete(long id) { getWritableDatabase().delete("notes","id=?",new String[]{String.valueOf(id)}); }
    private Note read(Cursor c) {
        return new Note(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("body")),
                c.getString(c.getColumnIndexOrThrow("image_path")),
                c.getString(c.getColumnIndexOrThrow("audio_path")),
                c.getLong(c.getColumnIndexOrThrow("updated_at")));
    }
}
