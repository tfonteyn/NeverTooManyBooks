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

import com.hardbacknutter.nevertoomanybooks.App;

/**
 * Standard Android class to handle database open/creation/upgrade.
 */
class QueueDBHelper
        extends SQLiteOpenHelper {

    public static final String KEY_PK_ID = "_id";

    public static final String KEY_TASK = "task";
    public static final String KEY_TASK_CATEGORY = "category";
    public static final String KEY_TASK_EXCEPTION = "exception";
    public static final String KEY_TASK_FAILURE_REASON = "failure_reason";
    public static final String KEY_TASK_QUEUED_UTC_DATETIME = "queued_date";
    public static final String KEY_TASK_RETRY_UTC_DATETIME = "retry_date";
    public static final String KEY_TASK_STATUS_CODE = "status_code";

    public static final String KEY_EVENT = "event";
    public static final String KEY_EVENT_COUNT = "event_count";
    public static final String KEY_EVENT_UTC_DATETIME = "event_date";

    static final String KEY_QUEUE_NAME = "name";

    static final String KEY_TASK_PRIORITY = "priority";
    static final String KEY_TASK_RETRY_COUNT = "retry_count";

    static final String KEY_TASK_ID = "task_id";
    static final String KEY_QUEUE_ID = "queue_id";

    /** Queue definitions. */
    static final String TBL_QUEUE = "queue";
    /** Scheduled task definitions. */
    static final String TBL_TASK = "task";
    /** Event table definitions. */
    static final String TBL_EVENT = "event";

    /** File name for the database. */
    private static final String DATABASE_NAME = "taskqueue.db";
    /** Current version of the database. */
    private static final int DATABASE_VERSION = 1;

    /** Collection of all table definitions. */
    private static final String[] TABLES = new String[]{
            "CREATE TABLE " + TBL_QUEUE + " ("
            + KEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + KEY_QUEUE_NAME + " text)",

            "CREATE TABLE " + TBL_TASK + " ("
            + KEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + KEY_QUEUE_ID + " integer NOT NULL REFERENCES " + TBL_QUEUE + ','
            + KEY_TASK_QUEUED_UTC_DATETIME + " datetime DEFAULT current_timestamp,"
            + KEY_TASK_PRIORITY + " integer DEFAULT 0,"
            + KEY_TASK_STATUS_CODE + " text DEFAULT '" + TQTask.QUEUED + "',"
            + KEY_TASK_CATEGORY + " integer DEFAULT 0 NOT NULL,"
            + KEY_TASK_RETRY_UTC_DATETIME + " datetime DEFAULT current_timestamp,"
            + KEY_TASK_RETRY_COUNT + " integer DEFAULT 0,"
            + KEY_TASK_FAILURE_REASON + " text,"
            + KEY_TASK_EXCEPTION + " blob,"
            + KEY_TASK + " blob NOT NULL)",

            "CREATE TABLE " + TBL_EVENT + " ("
            + KEY_PK_ID + " integer PRIMARY KEY AUTOINCREMENT,"
            + KEY_TASK_ID + " integer REFERENCES " + TBL_TASK + ','
            + KEY_EVENT + " blob NOT NULL,"
            + KEY_EVENT_UTC_DATETIME + " datetime DEFAULT current_timestamp)",
            };

    private static final String[] INDEXES = new String[]{
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX1 ON " + TBL_QUEUE + " (" + KEY_PK_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX2 ON " + TBL_QUEUE + " (" + KEY_QUEUE_NAME
            + ')',

            "CREATE UNIQUE INDEX " + TBL_TASK + "_IX1 ON " + TBL_TASK + " (" + KEY_PK_ID + ')',
            "CREATE INDEX " + TBL_TASK + "_IX2 ON " + TBL_TASK
            + " (" + KEY_TASK_STATUS_CODE + ',' + KEY_QUEUE_ID + ',' + KEY_TASK_RETRY_UTC_DATETIME
            + ')',
            "CREATE INDEX " + TBL_TASK + "_IX3 ON " + TBL_TASK
            + " (" + KEY_TASK_STATUS_CODE + ',' + KEY_QUEUE_ID + ',' + KEY_TASK_RETRY_UTC_DATETIME
            + ',' + KEY_TASK_PRIORITY + ')',

            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX1 ON " + TBL_EVENT + " (" + KEY_PK_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX2 ON " + TBL_EVENT
            + " (" + KEY_EVENT_UTC_DATETIME + ',' + KEY_PK_ID + ')',
            "CREATE INDEX " + TBL_EVENT + "_IX3 ON " + TBL_EVENT
            + " (" + KEY_TASK_ID + ',' + KEY_PK_ID + ')',
            };

    /**
     * Constructor.
     *
     * @param context Current context
     */
    QueueDBHelper(@NonNull final Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {

        // URGENT: remove before next beta
        App.getAppContext().deleteDatabase("net.philipwarner.taskqueue.database.db");

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
//        int currVersion = oldVersion;
//
//        if (currVersion == 1) {
//            //noinspection UnusedAssignment
//            currVersion++;
//
//        }
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        // Turn on foreign key support so that CASCADE works.
        db.execSQL("PRAGMA foreign_keys = ON");
    }

}
