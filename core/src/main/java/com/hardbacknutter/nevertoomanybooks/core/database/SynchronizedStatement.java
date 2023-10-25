/*
 * @Copyright 2018-2023 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.core.database;

import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Wrapper for {@link SQLiteStatement} that ensures locking is used.
 */
@SuppressWarnings({"unused", "WeakerAccess", "MissingJavadoc"})
public class SynchronizedStatement
        extends ExtSQLiteStatement {

    /** Log tag. */
    private static final String TAG = "SynchronizedStatement";

    /** Synchronizer from database. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Synchronizer synchronizer;
    /** Indicates this is a 'read-only' statement. */
    private final boolean readOnly;

    /**
     * Constructor.
     * Always use {@link SynchronizedDb#compileStatement(String)} to get a new instance.
     *
     * @param synchronizer to use
     * @param statement    to wrap
     * @param readOnly     flag; is the statement a read-only operation
     */
    public SynchronizedStatement(@NonNull final Synchronizer synchronizer,
                                 @NonNull final SQLiteStatement statement,
                                 final boolean readOnly) {
        super(statement);
        this.synchronizer = synchronizer;
        this.readOnly = readOnly;
    }

    public long simpleQueryForLong()
            throws SQLiteDoneException {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return super.simpleQueryForLong();
        } finally {
            sharedLock.unlock();
        }
    }

    public long simpleQueryForLongOrZero() {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return super.simpleQueryForLongOrZero();
        } finally {
            sharedLock.unlock();
        }
    }

    @NonNull
    public String simpleQueryForString()
            throws SQLiteDoneException {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return super.simpleQueryForString();
        } finally {
            sharedLock.unlock();
        }
    }

    @Nullable
    public String simpleQueryForStringOrNull() {
        final Synchronizer.SyncLock sharedLock = synchronizer.getSharedLock();
        try {
            return super.simpleQueryForStringOrNull();
        } finally {
            sharedLock.unlock();
        }
    }

    public void execute() {
        final Synchronizer.SyncLock txLock;
        if (readOnly) {
            txLock = synchronizer.getSharedLock();
        } else {
            txLock = synchronizer.getExclusiveLock();
        }
        try {
            super.execute();
        } finally {
            txLock.unlock();
        }
    }

    public int executeUpdateDelete() {
        final Synchronizer.SyncLock exclusiveLock = synchronizer.getExclusiveLock();
        try {
            return super.executeUpdateDelete();
        } finally {
            exclusiveLock.unlock();
        }
    }

    public long executeInsert() {
        final Synchronizer.SyncLock exclusiveLock = synchronizer.getExclusiveLock();
        try {
            return super.executeInsert();
        } finally {
            exclusiveLock.unlock();
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SynchronizedStatement{"
               + super.toString()
               + ", readOnly=" + readOnly
               + '}';
    }
}
