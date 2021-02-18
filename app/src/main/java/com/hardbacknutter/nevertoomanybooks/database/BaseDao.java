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
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public abstract class BaseDao
        implements AutoCloseable {

    /** Log tag. */
    private static final String TAG = "BaseDao";

    /** Collection of statements pre-compiled for this object. */
    final SqlStatementManager mSqlStatementManager;
    /** Reference to the singleton. */
    final SynchronizedDb mSyncedDb;
    @NonNull
    private final String mInstanceName;
    /** DEBUG: Indicates close() has been called. Also see {@link AutoCloseable#close()}. */
    private boolean mCloseWasCalled;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    BaseDao(@NonNull final Context context,
            @NonNull final String logTag) {
        mInstanceName = logTag;

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|Constructor");
        }

        mSyncedDb = DBHelper.getSyncDb(context);

        // statements are instance based/managed
        mSqlStatementManager = new SqlStatementManager(mSyncedDb, TAG + "|" + mInstanceName);
    }

    @NonNull
    public File getDatabaseFile() {
        return mSyncedDb.getDatabaseFile();
    }

    /**
     * Get the local database.
     * This should only be called in test classes, and from the {@link DBCleaner}.
     * <p>
     * Other code should use {@link DBHelper#getSyncDb(Context)} directly to get
     * a lighter weight object.
     *
     * @return Underlying database connection
     */
    @NonNull
    SynchronizedDb getSyncDb() {
        return mSyncedDb;
    }

    /**
     * DEBUG only. Return the instance name of this DAO.
     *
     * @return name
     */
    @NonNull
    public String getName() {
        return mInstanceName;
    }

    /**
     * Wrapper to {@link SynchronizedDb#beginTransaction(boolean)}.
     *
     * @param isUpdate Indicates if updates will be done in TX
     *
     * @return the lock
     */
    @NonNull
    public Synchronizer.SyncLock beginTransaction(final boolean isUpdate) {
        return mSyncedDb.beginTransaction(isUpdate);
    }

    /**
     * Wrapper to {@link SynchronizedDb#endTransaction}.
     *
     * @param txLock Lock returned from BeginTransaction().
     */
    public void endTransaction(@Nullable final Synchronizer.SyncLock txLock) {
        // it's cleaner to have the null detection here
        mSyncedDb.endTransaction(Objects.requireNonNull(txLock));
    }

    /**
     * Wrapper to {@link SynchronizedDb#inTransaction}.
     */
    public boolean inTransaction() {
        return mSyncedDb.inTransaction();
    }

    /**
     * Wrapper to {@link SynchronizedDb#setTransactionSuccessful}.
     */
    public void setTransactionSuccessful() {
        mSyncedDb.setTransactionSuccessful();
    }

    /**
     * Generic function to close the database.
     * It does not 'close' the database in the literal sense, but
     * performs a cleanup by closing all open statements
     * <p>
     * So it should really be called cleanup()
     * But it allows us to use try-with-resources.
     * <p>
     * Consequently, there is no need to 'open' anything before running further operations.
     */
    @Override
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, mInstanceName + "|close");
        }
        if (mSqlStatementManager != null) {
            // the close() will perform a clear, ready to be re-used.
            mSqlStatementManager.close();
        }
        mCloseWasCalled = true;
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            if (BuildConfig.DEBUG /* always */) {
                Logger.w(TAG, "finalize|" + mInstanceName);
            }
            close();
        }
        super.finalize();
    }

    public static class DaoWriteException
            extends Exception {

        private static final long serialVersionUID = -2857466683799399619L;

        public DaoWriteException(@NonNull final String message) {
            super(message);
        }

        DaoWriteException(@NonNull final String message,
                          @NonNull final Throwable cause) {
            super(message, cause);
        }
    }
}
