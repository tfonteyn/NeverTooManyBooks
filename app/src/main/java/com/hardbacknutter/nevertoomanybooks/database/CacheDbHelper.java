/*
 * @Copyright 2018-2022 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
package com.hardbacknutter.nevertoomanybooks.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.SqLiteDataType;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;

/**
 * {@link SQLiteOpenHelper} for the cache database.
 * Uses the application context.
 */
public class CacheDbHelper
        extends SQLiteOpenHelper {

    /* Domain definitions. */
    public static final String PK_ID = "_id";
    public static final String IMAGE_ID = "key";
    public static final String IMAGE_BLOB = "image";
    public static final String IMAGE_LAST_UPDATED__UTC = "last_update_date";

    /** The pen-name or real name. */
    public static final String BDT_AUTHOR_NAME = "name";
    public static final String BDT_AUTHOR_NAME_OB = "name_ob";
    /** The url for the author page. */
    public static final String BDT_AUTHOR_URL = "url";
    public static final String BDT_AUTHOR_IS_RESOLVED = "res";
    /** The resolved name if any. */
    public static final String BDT_AUTHOR_RESOLVED_NAME = "res_name";
    public static final String BDT_AUTHOR_RESOLVED_NAME_OB = "res_name_ob";

    /** DB name. */
    private static final String DATABASE_NAME = "cache.db";

    private static final int DATABASE_VERSION = 1;

    private static final Domain DOM_PK_ID;

    /** {@link #TBL_IMAGE}. */
    private static final Domain DOM_IMAGE_ID;

    /** {@link #TBL_IMAGE}. */
    private static final Domain DOM_IMAGE_BLOB;

    /** {@link #TBL_IMAGE}. */
    private static final Domain DOM_IMAGE_LAST_UPDATED__UTC;
    /** pre-scaled images. */
    public static final TableDefinition TBL_IMAGE;
    /** author page urls from Bedetheque. */
    public static final TableDefinition TBL_BDT_AUTHORS;
    /** {@link #TBL_BDT_AUTHORS}. */
    private static final Domain DOM_BDT_AUTHOR_NAME;
    /** {@link #TBL_BDT_AUTHORS}. */
    private static final Domain DOM_BDT_AUTHOR_NAME_OB;
    /** {@link #TBL_BDT_AUTHORS}. The url to the author page on Bedetheque. */
    private static final Domain DOM_BDT_AUTHOR_URL;
    /** {@link #TBL_BDT_AUTHORS}. */
    private static final Domain DOM_BDT_AUTHOR_IS_RESOLVED;
    /** {@link #TBL_BDT_AUTHORS}. */
    private static final Domain DOM_BDT_AUTHOR_RESOLVED_NAME;
    /** {@link #TBL_BDT_AUTHORS}. */
    private static final Domain DOM_BDT_AUTHOR_RESOLVED_NAME_OB;

    /** Readers/Writer lock for <strong>this</strong> database. */
    private static final Synchronizer SYNCHRONIZER = new Synchronizer();

    /** Static Factory object to create the custom cursor. */
    private static final SQLiteDatabase.CursorFactory CURSOR_FACTORY =
            (db, d, et, q) -> new SynchronizedCursor(d, et, q, SYNCHRONIZER);

    static {
        DOM_PK_ID =
                new Domain.Builder(PK_ID, SqLiteDataType.Integer)
                        .primaryKey()
                        .build();

        DOM_IMAGE_ID =
                new Domain.Builder(IMAGE_ID, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_IMAGE_BLOB =
                new Domain.Builder(IMAGE_BLOB, SqLiteDataType.Blob)
                        .notNull()
                        .build();

        DOM_IMAGE_LAST_UPDATED__UTC =
                new Domain.Builder(IMAGE_LAST_UPDATED__UTC, SqLiteDataType.DateTime)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .build();

        DOM_BDT_AUTHOR_NAME =
                new Domain.Builder(BDT_AUTHOR_NAME, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_BDT_AUTHOR_NAME_OB =
                new Domain.Builder(BDT_AUTHOR_NAME_OB, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_BDT_AUTHOR_RESOLVED_NAME =
                new Domain.Builder(BDT_AUTHOR_RESOLVED_NAME, SqLiteDataType.Text)
                        .build();

        DOM_BDT_AUTHOR_RESOLVED_NAME_OB =
                new Domain.Builder(BDT_AUTHOR_RESOLVED_NAME_OB, SqLiteDataType.Text)
                        .build();

        DOM_BDT_AUTHOR_URL =
                new Domain.Builder(BDT_AUTHOR_URL, SqLiteDataType.Text)
                        .notNull()
                        .build();

        DOM_BDT_AUTHOR_IS_RESOLVED =
                new Domain.Builder(BDT_AUTHOR_IS_RESOLVED, SqLiteDataType.Boolean)
                        .notNull()
                        .withDefault(false)
                        .build();

        TBL_IMAGE =
                new TableDefinition("image", "image")
                        .addDomains(DOM_PK_ID,
                                    DOM_IMAGE_BLOB,
                                    DOM_IMAGE_LAST_UPDATED__UTC,
                                    DOM_IMAGE_ID)
                        .setPrimaryKey(DOM_PK_ID)
                        .addIndex("id", true, DOM_PK_ID)
                        .addIndex(IMAGE_ID, true, DOM_IMAGE_ID)
                        .addIndex(IMAGE_ID + "_" + IMAGE_LAST_UPDATED__UTC,
                                  true, DOM_IMAGE_ID, DOM_IMAGE_LAST_UPDATED__UTC);

        TBL_BDT_AUTHORS =
                new TableDefinition("bdt_authors", "bdt_a")
                        .addDomains(DOM_PK_ID,
                                    DOM_BDT_AUTHOR_NAME,
                                    DOM_BDT_AUTHOR_NAME_OB,
                                    DOM_BDT_AUTHOR_IS_RESOLVED,
                                    DOM_BDT_AUTHOR_RESOLVED_NAME,
                                    DOM_BDT_AUTHOR_RESOLVED_NAME_OB,
                                    DOM_BDT_AUTHOR_URL)
                        .setPrimaryKey(DOM_PK_ID)
                        .addIndex(BDT_AUTHOR_NAME_OB, true, DOM_BDT_AUTHOR_NAME_OB)
                        .addIndex(BDT_AUTHOR_RESOLVED_NAME_OB, false,
                                  DOM_BDT_AUTHOR_RESOLVED_NAME_OB);
    }

    private final boolean collationCaseSensitive;

    /** DO NOT USE INSIDE THIS CLASS! ONLY FOR USE BY CLIENTS VIA {@link #getDb()}. */
    @Nullable
    private SynchronizedDb db;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public CacheDbHelper(@NonNull final Context context,
                         final boolean collationCaseSensitive) {
        super(context.getApplicationContext(), DATABASE_NAME, CURSOR_FACTORY, DATABASE_VERSION);
        this.collationCaseSensitive = collationCaseSensitive;
    }

    /**
     * Get/create the Synchronized database.
     *
     * @return database connection
     */
    @NonNull
    public SynchronizedDb getDb() {
        synchronized (this) {
            if (db == null) {
                // Dev note: don't move this to the constructor, "this" must
                // be fully constructed before we can pass it to the SynchronizedDb constructor
                db = new SynchronizedDb(SYNCHRONIZER, this, collationCaseSensitive);
            }
            return db;
        }
    }

    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
        super.close();
    }

    @Override
    public void onCreate(@NonNull final SQLiteDatabase db) {
        TableDefinition.onCreate(db, collationCaseSensitive,
                                 List.of(TBL_IMAGE, TBL_BDT_AUTHORS));
    }

    @Override
    public void onUpgrade(@NonNull final SQLiteDatabase db,
                          final int oldVersion,
                          final int newVersion) {
        // This is a cache, so no data needs preserving. Drop & recreate.
        db.execSQL("DROP TABLE IF EXISTS " + TBL_IMAGE.getName());
        db.execSQL("DROP TABLE IF EXISTS " + TBL_BDT_AUTHORS.getName());
        onCreate(db);
    }

    @Override
    public void onOpen(@NonNull final SQLiteDatabase db) {
        // Turn ON foreign key support so that CASCADE etc. works.
        // This is the same as db.execSQL("PRAGMA foreign_keys = ON");
        db.setForeignKeyConstraintsEnabled(true);
    }
}
