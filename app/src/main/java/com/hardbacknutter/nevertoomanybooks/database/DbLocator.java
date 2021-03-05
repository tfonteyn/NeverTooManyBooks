/*
 * @Copyright 2018-2021 HardBackNutter
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueDBHelper;

public final class DbLocator {

    /** Singleton. */
    private static DbLocator sInstance;

    @NonNull
    private final Context mAppContext;

    @Nullable
    private DBHelper mDBHelper;

    @Nullable
    private CoversDbHelper mCoversDbHelper;

    /**
     * Private constructor.
     *
     * @param context Current context
     */
    private DbLocator(@NonNull final Context context) {
        mAppContext = context.getApplicationContext();
    }

    /**
     * Public constructor.
     *
     * @param context application or test context.
     */
    public static void init(@NonNull final Context context) {
        synchronized (DbLocator.class) {
            if (sInstance == null) {
                sInstance = new DbLocator(context);
            }
        }
    }

    public static boolean isCollationCaseSensitive() {
        return sInstance.getDBHelper().isCollationCaseSensitive();
    }

    /**
     * Main entry point for clients to get the main database.
     *
     * @return the database instance
     */
    public static SynchronizedDb getDb() {
        return sInstance.getDBHelper().getDb();
    }

    /**
     * Main entry point for clients to get the covers database.
     *
     * @return the database instance
     */
    public static SynchronizedDb getCoversDb() {
        return sInstance.getCoversDbHelper().getDb();
    }

    public static void deleteDatabases(@NonNull final Context context) {
        sInstance.getDBHelper().deleteDatabase(context);
        sInstance.getCoversDbHelper().deleteDatabase(context);

        context.deleteDatabase(QueueDBHelper.DATABASE_NAME);
    }

    @NonNull
    private DBHelper getDBHelper() {
        synchronized (this) {
            if (mDBHelper == null) {
                mDBHelper = new DBHelper(mAppContext);
            }
        }
        return mDBHelper;
    }

    @VisibleForTesting
    public void setDBHelper(@Nullable final DBHelper openHelper) {
        mDBHelper = openHelper;
    }

    @NonNull
    private CoversDbHelper getCoversDbHelper() {
        synchronized (this) {
            if (mCoversDbHelper == null) {
                mCoversDbHelper = new CoversDbHelper(mAppContext);
            }
        }
        return mCoversDbHelper;
    }

    @VisibleForTesting
    public void setCoversDbHelper(@Nullable final CoversDbHelper openHelper) {
        mCoversDbHelper = openHelper;
    }
}
