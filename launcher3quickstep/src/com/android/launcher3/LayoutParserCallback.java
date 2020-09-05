package com.android.launcher3;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

public interface LayoutParserCallback {
    int generateNewItemId();

    int insertAndCheck(SQLiteDatabase db, ContentValues values);
}
