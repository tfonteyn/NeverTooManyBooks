/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/**
 * Standard Android class to handle database open/creation.upgrade.
 */
class TaskQueueDBHelper
        extends SQLiteOpenHelper {

    static final String CKEY_PK_ID = "_id";
    static final String CKEY_CATEGORY = "category";
    static final String CKEY_EXCEPTION = "exception";
    static final String CKEY_FAILURE_REASON = "failure_reason";
    static final String CKEY_NAME = "name";
    static final String CKEY_EVENT = "event";
    static final String CKEY_EVENT_COUNT = "event_count";
    static final String CKEY_EVENT_DATE = "event_date";
    static final String CKEY_QUEUE_ID = "queue_id";
    static final String CKEY_QUEUED_DATE = "queued_date";
    static final String CKEY_PRIORITY = "priority";
    static final String CKEY_RETRY_DATE = "retry_date";
    static final String CKEY_RETRY_COUNT = "retry_count";
    static final String CKEY_STATUS_CODE = "status_code";
    static final String CKEY_TASK = "task";
    static final String CKEY_TASK_ID = "task_id";

    /** Queue definition. */
    static final String TBL_QUEUE = "queue";
    /** Scheduled task definition. */
    static final String TBL_TASK = "task";
    /** Event table definition. */
    static final String TBL_EVENT = "event";
    /** File name for database. */
    private static final String DATABASE_NAME = "net.philipwarner.taskqueue.database.db";
    private static final int DATABASE_VERSION = 2;

    /** Collection of all table definitions. */
    private static final String[] TABLES = new String[]{
            "CREATE TABLE " + TBL_QUEUE + " ("
            + CKEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + CKEY_NAME + " text)",

            "CREATE TABLE " + TBL_TASK + " ("
            + CKEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + CKEY_QUEUE_ID + " integer NOT NULL REFERENCES " + TBL_QUEUE + ','
            + CKEY_QUEUED_DATE + " datetime DEFAULT current_timestamp,"
            + CKEY_PRIORITY + " integer DEFAULT 0,"
            + CKEY_STATUS_CODE + " text DEFAULT '" + Task.STATUS_QUEUED + "',"
            + CKEY_CATEGORY + " integer DEFAULT 0 NOT NULL,"
            + CKEY_RETRY_DATE + " datetime DEFAULT current_timestamp,"
            + CKEY_RETRY_COUNT + " integer DEFAULT 0,"
            + CKEY_FAILURE_REASON + " text,"
            + CKEY_EXCEPTION + " blob,"
            + CKEY_TASK + " blob NOT NULL)",

            "CREATE TABLE " + TBL_EVENT + " ("
            + CKEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,\n"
            + CKEY_TASK_ID + " integer REFERENCES " + TBL_TASK + ','
            + CKEY_EVENT + " blob NOT NULL,"
            + CKEY_EVENT_DATE + " datetime DEFAULT current_timestamp)",
            };

    private static final String[] INDEXES = new String[]{
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX1 ON " + TBL_QUEUE + " (" + CKEY_PK_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX2 ON " + TBL_QUEUE + " (" + CKEY_NAME + ')',

            "CREATE UNIQUE INDEX " + TBL_TASK + "_IX1 ON " + TBL_TASK + " (" + CKEY_PK_ID + ')',
            "CREATE INDEX " + TBL_TASK + "_IX2 ON " + TBL_TASK + " ("
            + CKEY_STATUS_CODE
            + ',' + CKEY_QUEUE_ID
            + ',' + CKEY_RETRY_DATE + ')',
            "CREATE INDEX " + TBL_TASK + "_IX3 ON " + TBL_TASK + " ("
            + CKEY_STATUS_CODE
            + ',' + CKEY_QUEUE_ID
            + ',' + CKEY_RETRY_DATE
            + ',' + CKEY_PRIORITY + ')',

            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX1 ON " + TBL_EVENT + " (" + CKEY_PK_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX2 ON " + TBL_EVENT + " ("
            + CKEY_EVENT_DATE
            + ',' + CKEY_PK_ID + ')',
            "CREATE INDEX " + TBL_EVENT + "_IX3 ON " + TBL_EVENT + " ("
            + CKEY_TASK_ID
            + ',' + CKEY_PK_ID + ')',
            };

    /**
     * Constructor.
     *
     * @param context Current context
     */
    TaskQueueDBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        for (String table : TABLES) {
            db.execSQL(table);
        }

        // Turn on foreign key support so that CASCADE works.
        db.execSQL("PRAGMA foreign_keys = ON");

        for (String index : INDEXES) {
            db.execSQL(index);
        }
    }

    /**
     * Called to upgrade DB.
     */
    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {
        int currVersion = oldVersion;

        if (currVersion == 1) {
            //noinspection UnusedAssignment
            currVersion++;
            String sql = "ALTER TABLE " + TBL_TASK + " Add " + CKEY_CATEGORY + " integer default 0";
            db.execSQL(sql);
        }

        // Turn on foreign key support so that CASCADE works.
        db.execSQL("PRAGMA foreign_keys = ON");
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        // Turn on foreign key support so that CASCADE works.
        db.execSQL("PRAGMA foreign_keys = ON");
    }

}
