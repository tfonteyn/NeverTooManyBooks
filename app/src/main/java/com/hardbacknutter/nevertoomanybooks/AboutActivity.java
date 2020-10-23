/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDAO;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityAdminAboutBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellActivity;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellFragment;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;

/**
 * This is the About page, showing some info and giving (semi-hidden) access to debug options.
 */
public class AboutActivity
        extends BaseActivity {

    public static final int RESULT_ALL_DATA_DESTROYED = 2;
    /**
     * After clicking the icon 3 times, we display the debug options.
     * SQLite shell updates are not allowed.
     */
    private static final int DEBUG_CLICKS = 3;
    /** After clicking the icon 3 more times, the SQLite shell will allow updates. */
    private static final int DEBUG_CLICKS_ALLOW_SQL_UPDATES = 6;

    /** Log tag. */
    private static final String TAG = "AboutActivity";
    /** After clicking the icon another 3 times, the button to delete all data becomes visible. */
    private static final int DEBUG_CLICKS_ALLOW_DELETE_ALL = 9;

    private ActivityAdminAboutBinding mVb;

    private int mDebugClicks;
    private boolean mSqLiteAllowUpdates;

    private PackageInfoWrapper mPackageInfoWrapper;

    @Override
    protected void onSetContentView() {
        mVb = ActivityAdminAboutBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageInfoWrapper = PackageInfoWrapper.create(this);

        mVb.version.setText(mPackageInfoWrapper.getVersionName());

        mVb.btnSourcecodeUrl.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sourcecode_url)))));

        mVb.appIcon.setOnClickListener(v -> {
            mDebugClicks++;
            if (mDebugClicks >= DEBUG_CLICKS) {
                // show the entire group
                mVb.debugGroup.setVisibility(View.VISIBLE);
                // show the full version + build date
                final String code = "a" + mPackageInfoWrapper.getVersionCode()
                                    + " d" + DBHelper.DATABASE_VERSION
                                    + " b" + BuildConfig.TIMESTAMP;
                mVb.debugVersion.setText(code);
            }
            if (mDebugClicks >= DEBUG_CLICKS_ALLOW_SQL_UPDATES) {
                ((MaterialButton) (mVb.debugSqShell)).setIconResource(R.drawable.ic_warning);
                mSqLiteAllowUpdates = true;
            }
            if (mDebugClicks >= DEBUG_CLICKS_ALLOW_DELETE_ALL) {
                // show the button, it's red...
                //URGENT: re-enable once the #onDeleteAll functionality is complete
                //mVb.debugClearDb.setVisibility(View.VISIBLE);
            }
        });

        mVb.debugSqShell.setOnClickListener(v -> {
            final Intent intent = new Intent(this, SqliteShellActivity.class)
                    .putExtra(SqliteShellFragment.BKEY_ALLOW_UPDATES, mSqLiteAllowUpdates);
            startActivity(intent);
        });

        mVb.debugClearDb.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_clear_all_data)
                .setIcon(R.drawable.ic_delete)
                .setMessage(R.string.confirm_clear_all_data)
                .setNegativeButton(R.string.no, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_delete, this::onDeleteAll)
                .create()
                .show());

        mVb.debugPrefs.setOnClickListener(v -> Prefs.dumpPreferences(this, null));
    }


    private void onDeleteAll(@NonNull final DialogInterface d,
                             final int w) {
        try (DAO db = new DAO(TAG)) {
            //FIXME: we should stop any active tasks + the qm itself
            final QueueManager qm = QueueManager.getInstance();
            qm.deleteTasksOlderThan(0);
            qm.deleteEventsOlderThan(0);

            StyleDAO.clearCache();

            if (db.getDBHelper().deleteAllContent(this, db.getSyncDb())) {
                AppDir.deleteAllContent(this);
                SearchEngineRegistry.create(this);
                //FIXME: restore all preferences.

                setResult(RESULT_ALL_DATA_DESTROYED);
            }
        }

        finish();
    }
}
