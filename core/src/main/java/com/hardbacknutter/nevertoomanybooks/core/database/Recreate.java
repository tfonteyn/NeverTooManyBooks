/*
 * @Copyright 2018-2026 HardBackNutter
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

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

public final class Recreate {

    static final ThreadLocal<Boolean> IS_RUNNING_WITHOUT_CONSTRAINTS =
            ThreadLocal.withInitial(() -> false);

    private Recreate() {
    }

    /**
     * Disable the Foreign Key Constraints, run the given commands,
     * and enable the constraint again.
     * <p>
     * <strong>IMPORTANT: upgrades calling this CANNOT BE ROLLBACKED</strong>
     *
     * @param db        Underlying database
     * @param runInside to run
     */
    public static void runWithoutConstraints(@NonNull final SQLiteDatabase db,
                                             @NonNull final Runnable runInside) {
        // THIS WILL COMMIT ALL PREVIOUS UPDATES
        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(false);
        db.beginTransaction();

        IS_RUNNING_WITHOUT_CONSTRAINTS.set(true);
        try {
            runInside.run();
        } finally {
            IS_RUNNING_WITHOUT_CONSTRAINTS.set(false);
        }

        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(true);
        db.beginTransaction();
    }
}
