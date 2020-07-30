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
package com.hardbacknutter.nevertoomanybooks;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityAdminAboutBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellActivity;
import com.hardbacknutter.nevertoomanybooks.debug.SqliteShellFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;

/**
 * This is the About page, showing some info and giving (semi-hidden) access to debug options.
 */
public class AboutActivity
        extends BaseActivity {

    /**
     * After clicking the icon 3 times, we display the debug options.
     * SQLite shell updates are not allowed.
     */
    private static final int DEBUG_CLICKS = 3;
    /** After clicking the icon 3 more times, the SQLite shell will allow updates. */
    private static final int DEBUG_CLICKS_ALLOW_UPD = 6;

    /** Log tag. */
    private static final String TAG = "AboutActivity";

    private ActivityAdminAboutBinding mVb;

    private int mDebugClicks;
    private boolean mSqLiteAllowUpdates;

    @Override
    protected void onSetContentView() {
        mVb = ActivityAdminAboutBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            final PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);

            final long versionCode;
            if (Build.VERSION.SDK_INT >= 28) {
                versionCode = info.getLongVersionCode();
            } else {
                versionCode = info.versionCode;
            }
            final String version = info.versionName
                                   + " (" + versionCode + '/' + DBHelper.DATABASE_VERSION + ')';

            mVb.version.setText(version);
        } catch (@NonNull final PackageManager.NameNotFoundException ignore) {
            // ignore
        }

        mVb.sourcecode6.setText(LinkifyUtils.fromHtml(
                getString(R.string.url_sourcecode, getString(R.string.lbl_about_sourcecode))));
        mVb.sourcecode6.setMovementMethod(LinkMovementMethod.getInstance());

        mVb.icon.setOnClickListener(v -> {
            mDebugClicks++;
            if (mDebugClicks >= DEBUG_CLICKS) {
                mVb.debugGroup.setVisibility(View.VISIBLE);
            }
            if (mDebugClicks >= DEBUG_CLICKS_ALLOW_UPD) {
                mSqLiteAllowUpdates = true;
            }
        });

        mVb.debugSqShell.setOnClickListener(v -> {
            Intent intent = new Intent(this, SqliteShellActivity.class)
                    .putExtra(SqliteShellFragment.BKEY_ALLOW_UPDATES, mSqLiteAllowUpdates);
            startActivity(intent);
        });

        mVb.debugPrefs.setOnClickListener(v -> Prefs.dumpPreferences(this, null));

        mVb.debugDumpTempTables.setOnClickListener(
                v -> DBHelper.dumpTempTableNames(DAO.getSyncDb()));

//
//        view = findViewById(R.id.website);
//        view.setText(LinkifyUtils.fromHtml(
//                getString(R.string.url_website, getString(R.string.lbl_about_website))));
//        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    /**
     * Prepare a new email.
     *
     * @param emailId resource id for email address
     */
    private void sendContactEmail(@StringRes final int emailId) {
        try {
            final String subject = '[' + getString(R.string.app_name) + "] ";
            final Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, new String[]{getString(emailId)})
                    .putExtra(Intent.EXTRA_SUBJECT, subject);
            startActivity(Intent.createChooser(intent, getString(R.string.lbl_send_mail)));
        } catch (@NonNull final ActivityNotFoundException e) {
            Logger.error(this, TAG, e);
        }
    }
}
