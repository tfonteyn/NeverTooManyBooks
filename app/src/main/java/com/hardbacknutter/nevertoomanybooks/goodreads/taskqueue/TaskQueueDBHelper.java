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

    static final String DOM_ID = "_id";
    static final String DOM_CATEGORY = "category";
    static final String DOM_EXCEPTION = "exception";
    static final String DOM_FAILURE_REASON = "failure_reason";
    static final String DOM_NAME = "name";
    static final String DOM_EVENT = "event";
    static final String DOM_EVENT_COUNT = "event_count";
    static final String DOM_EVENT_DATE = "event_date";
    static final String DOM_QUEUE_ID = "queue_id";
    static final String DOM_QUEUED_DATE = "queued_date";
    static final String DOM_PRIORITY = "priority";
    static final String DOM_RETRY_DATE = "retry_date";
    static final String DOM_RETRY_COUNT = "retry_count";
    static final String DOM_STATUS_CODE = "status_code";
    static final String DOM_TASK = "task";
    static final String DOM_TASK_ID = "task_id";

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
            + DOM_ID + " integer primary key autoincrement,"
            + DOM_NAME + " text)",

            "CREATE TABLE " + TBL_TASK + " ("
            + DOM_ID + " integer primary key autoincrement,"
            + DOM_QUEUE_ID + " integer not null references " + TBL_QUEUE + ','
            + DOM_QUEUED_DATE + " datetime default current_timestamp,"
            + DOM_PRIORITY + " integer default 0,"
            + DOM_STATUS_CODE + " text default '" + Task.STATUS_QUEUED + "',"
            + DOM_CATEGORY + " integer default 0 not null,"
            + DOM_RETRY_DATE + " datetime default current_timestamp,"
            + DOM_RETRY_COUNT + " integer default 0,"
            + DOM_FAILURE_REASON + " text,"
            + DOM_EXCEPTION + " blob,"
            + DOM_TASK + " blob not null)",

            "CREATE TABLE " + TBL_EVENT + " (" + DOM_ID + " integer primary key autoincrement,\n"
            + DOM_TASK_ID + " integer references " + TBL_TASK + ','
            + DOM_EVENT + " blob not null,"
            + DOM_EVENT_DATE + " datetime default current_timestamp)",
            };

    private static final String[] INDEXES = new String[]{
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX1 ON " + TBL_QUEUE + " (" + DOM_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_QUEUE + "_IX2 ON " + TBL_QUEUE + " (" + DOM_NAME + ')',

            "CREATE UNIQUE INDEX " + TBL_TASK + "_IX1 ON " + TBL_TASK + " (" + DOM_ID + ')',
            "CREATE INDEX " + TBL_TASK + "_IX2 ON " + TBL_TASK + " ("
            + DOM_STATUS_CODE
            + ',' + DOM_QUEUE_ID
            + ',' + DOM_RETRY_DATE + ')',
            "CREATE INDEX " + TBL_TASK + "_IX3 ON " + TBL_TASK + " ("
            + DOM_STATUS_CODE
            + ',' + DOM_QUEUE_ID
            + ',' + DOM_RETRY_DATE
            + ',' + DOM_PRIORITY + ')',

            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX1 ON " + TBL_EVENT + " (" + DOM_ID + ')',
            "CREATE UNIQUE INDEX " + TBL_EVENT + "_IX2 ON " + TBL_EVENT + " ("
            + DOM_EVENT_DATE
            + ',' + DOM_ID + ')',
            "CREATE INDEX " + TBL_EVENT + "_IX3 ON " + TBL_EVENT + " ("
            + DOM_TASK_ID
            + ',' + DOM_ID + ')',
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
            String sql = "ALTER TABLE " + TBL_TASK + " Add " + DOM_CATEGORY + " integer default 0";
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
