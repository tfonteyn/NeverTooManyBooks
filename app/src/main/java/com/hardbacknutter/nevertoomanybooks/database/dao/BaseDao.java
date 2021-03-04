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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.database.DBCleaner;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;

public interface BaseDao {

    @NonNull
    String getInstanceName();

    @NonNull
    File getDatabaseFile();

    /**
     * Get the local database.
     * This should only be called in test classes, and from the {@link DBCleaner}.
     * <p>
     * Other code should use {@link DBHelper#getDb(Context)} directly to get
     * a lighter weight object.
     *
     * @return Underlying database connection
     */
    @NonNull
    SynchronizedDb getDb();

    /**
     * Wrapper to {@link SynchronizedDb#inTransaction}.
     */
    boolean inTransaction();

    /**
     * Wrapper to {@link SynchronizedDb#beginTransaction(boolean)}.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    Synchronizer.SyncLock beginTransaction(boolean isUpdate);

    /**
     * Wrapper to {@link SynchronizedDb#setTransactionSuccessful}.
     */
    void setTransactionSuccessful();

    /**
     * Wrapper to {@link SynchronizedDb#endTransaction}.
     *
     * @param txLock Lock returned from {@link #beginTransaction(boolean)}
     */
    void endTransaction(@Nullable Synchronizer.SyncLock txLock);
}
