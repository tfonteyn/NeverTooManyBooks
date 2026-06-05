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

package com.hardbacknutter.nevertoomanybooks.database.updates;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalStyle;
import com.hardbacknutter.nevertoomanybooks.core.storage.FileUtils;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.impl.StyleDaoImpl;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreCustomField;
import com.hardbacknutter.util.logger.LoggerFactory;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;

public class Upgrade {

    private static final String TAG = "Upgrade";

    @NonNull
    private final Context context;
    @NonNull
    private final SQLiteDatabase db;

    @NonNull
    private final IdentifierMigration identifierMigration;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param db      Underlying database
     */
    public Upgrade(@NonNull final Context context,
                   @NonNull final SQLiteDatabase db) {
        this.context = context;
        this.db = db;

        identifierMigration = new IdentifierMigration(context, db);
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
    static void runWithoutConstraints(@NonNull final SQLiteDatabase db,
                                      @NonNull final Runnable runInside) {
        // THIS WILL COMMIT ALL PREVIOUS UPDATES
        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(false);
        db.beginTransaction();

        runInside.run();

        db.setTransactionSuccessful();
        db.endTransaction();
        // This method must not be called while a transaction is in progress.
        db.setForeignKeyConstraintsEnabled(true);
        db.beginTransaction();
    }

    public void backup(@NonNull final File destDir,
                       @NonNull final String fileName) {
        try {
            final File destFile = new File(destDir, fileName);
            // rename the existing file if there is one
            if (destFile.exists()) {
                final File destination = new File(destFile.getPath() + ".bak");
                try {
                    FileUtils.rename(destFile, destination);
                } catch (@NonNull final IOException e) {
                    LoggerFactory.getLogger()
                                 .e(TAG, e, "failed to rename source=" + destFile
                                            + " TO destination=" + destination, e);
                }
            }
            // and create a new copy
            FileUtils.copy(new File(db.getPath()), destFile);
        } catch (@NonNull final IOException e) {
            LoggerFactory.getLogger().e(TAG, e);
        }
    }

    public void upgrade(final int oldVersion) {
        if (oldVersion < 25) {
            new V4(context, db).update(oldVersion);
        }

        if (oldVersion < 34) {
            new V5(db).update(oldVersion);
        }

        if (oldVersion < 51) {
            new V7(context, db).update(oldVersion);
        }

        if (oldVersion < 52) {
            new V8(context, db, identifierMigration).update(oldVersion);
        }

        cleanup();
    }

    private void cleanup() {
        // We have to do this at this point as we're always inserting all columns,
        // which may be created at various points in the updates.
        // Any identifier already existing will simply be skipped.
        // See GitHub #185
        // db52 update REMOVED
        // addNewIdentifiers();

        // Same as above, but for Calibre.
        addNewCalibreCustomFields();

        // We have to do this at this point due to some users skipping updates (see GitHub #30)
        // The issue is that this only works OK if the TBL_BOOKLIST_STYLES contains
        // ALL columns at the time we're executing it.
        ensureGlobalStyle();

        // Migrate any FieldVisibility keys + remove all obsolete keys
        PreferenceKeyMigration.migrate(context);
    }

    // db52 update REMOVED
    //    /**
    //     * Add a set of {@link Identifier}s which were added after the initial app release.
    //     */
    //    private void addNewIdentifiers() {
    //        identifierMigration.add(Set.of(
    //                Identifier.SID_DATABAZE_KNIH,
    //                Identifier.SID_ISNI,
    //                Identifier.SID_STORYGRAPH,
    //                Identifier.SID_URN,
    //                Identifier.SID_VIAF,
    //                Identifier.SID_BIBLIOTECE_PL));
    //    }

    /**
     * Adds {@link CalibreCustomField}s which were added after the initial app release.
     * <p>
     * FIXME: handle calibre custom fields the same as we do for new Identifiers
     */
    private void addNewCalibreCustomFields() {
        final CalibreMigration calibreMigration = new CalibreMigration(db);

        // NEWTHINGS: adding a Calibre custom field
        calibreMigration.add("#rating",
                             CalibreCustomField.TYPE_RATING,
                             DBKey.RATING);
        calibreMigration.add(CalibreCustomField.FIELD_READ_PROGRESS,
                             CalibreCustomField.TYPE_COMPOSITE,
                             DBKey.READ_PROGRESS);
    }

    /**
     * Depending on the upgrade path of some users,
     * add the global style if it does not already exist.
     */
    private void ensureGlobalStyle() {
        final boolean isPresent;
        try (SQLiteStatement stmt = db.compileStatement(
                "SELECT COUNT(" + DBKey.STYLE.TYPE + ") FROM " + TBL_BOOKLIST_STYLES.getName()
                + " WHERE " + DBKey.STYLE.TYPE + "=2")) {
            isPresent = stmt.simpleQueryForLong() > 0;
        }

        if (isPresent) {
            return;
        }

        final SharedPreferences prefs = ServiceLocator.getInstance().getSharedPreferences();
        final GlobalStyle style = GlobalStyle.createDefault();
        style.setSortAuthorByGivenName(
                prefs.getBoolean("sort.author.name.given_first", false));
        style.setShowAuthorByGivenName(
                prefs.getBoolean("show.author.name.given_first", false));

        StyleDaoImpl.insertGlobalDefaults(db, style);
    }
}
