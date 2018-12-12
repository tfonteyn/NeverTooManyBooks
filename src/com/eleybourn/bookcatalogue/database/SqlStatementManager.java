/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedDb;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedStatement;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

/**
 * Utility class to manage the construction and closure of persisted SQLiteStatement objects.
 *
 * @author Philip Warner
 */
public class SqlStatementManager implements AutoCloseable {
    @NonNull
    private final Hashtable<String, SynchronizedStatement> mStatements;
    @Nullable
    private final SynchronizedDb mSyncedDb;

    SqlStatementManager() {
        this(null);
    }

    public SqlStatementManager(final @Nullable SynchronizedDb db) {
        mSyncedDb = db;
        mStatements = new Hashtable<>();
    }

    public SynchronizedStatement get(final @NonNull String name) {
        return mStatements.get(name);
    }

    @NonNull
    public SynchronizedStatement add(final @NonNull String name, final @NonNull String sql) {
        Objects.requireNonNull(mSyncedDb,"Database not set when SqlStatementManager created");
        return add(mSyncedDb, name, sql);
    }

    @NonNull
    public SynchronizedStatement add(final @NonNull SynchronizedDb db,
                                     final @NonNull String name,
                                     final @NonNull String sql) {
        SynchronizedStatement stmt = db.compileStatement(sql);
        SynchronizedStatement old = mStatements.get(name);
        mStatements.put(name, stmt);
        if (old != null)
            old.close();
        return stmt;
    }

//    @NonNull
//    public SynchronizedStatement addOrGet(final @NonNull String name, final @NonNull String sql) {
//        Objects.requireNonNull(mSyncedDb, "Database not set when SqlStatementManager created");
//        return addOrGet(mSyncedDb, name, sql);
//    }
//
//    @NonNull
//    private SynchronizedStatement addOrGet(final @NonNull  SynchronizedDb db, final @NonNull  String name, final @NonNull  String sql) {
//        SynchronizedStatement stmt = mStatements.get(name);
//        if (stmt == null) {
//            stmt = add(db, name, sql);
//        }
//        return stmt;
//    }

    /**
     * DEBUG help
     *
     * @return list of all the names of the managed statements
     */
    @NonNull
    public List<String> getNames(){
        List<String> list = new ArrayList<>();
        Enumeration<String> all = mStatements.keys();
        while (all.hasMoreElements()) {
            list.add(all.nextElement());
        }
        return list;
    }

    /**
     * GoodreadsExceptions are caught and fully ignored.
     */
    @Override
    public void close() {
        synchronized (mStatements) {
            for (SynchronizedStatement s : mStatements.values()) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
            mStatements.clear();
        }
    }

    public int size() {
        return mStatements.size();
    }
}
