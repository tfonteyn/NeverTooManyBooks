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
package com.hardbacknutter.nevertoomanybooks.searches.librarything;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityLibrarythingRegisterBinding;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;

/**
 * Contains details about LibraryThing links and how to register for a developer key.
 * At a later data we could also include the user key for maintaining user-specific data.
 */
public class LibraryThingRegistrationActivity
        extends BaseActivity {

    /** View Binding. */
    private ActivityLibrarythingRegisterBinding mVb;

    private ValidateKeyTask mValidateKeyTask;

    @Override
    protected void onSetContentView() {
        mVb = ActivityLibrarythingRegisterBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mValidateKeyTask = new ViewModelProvider(this).get(ValidateKeyTask.class);
        mValidateKeyTask.onFailure().observe(this, message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), getString(R.string.error_site_access_failed,
                                                       getString(R.string.site_library_thing)),
                              Snackbar.LENGTH_LONG).show();
            }
        });
        mValidateKeyTask.onCancelled().observe(this, message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mValidateKeyTask.onFinished().observe(this, message -> {
            if (message.isNewEvent()) {
                final String msg = message.result != null
                                   ? getString(message.result)
                                   : getString(R.string.error_site_access_failed,
                                               getString(R.string.site_library_thing));
                Snackbar.make(mVb.getRoot(), msg, Snackbar.LENGTH_LONG).show();
            }
        });

        // for the purist: we should call SearchEngine#getSiteUrl()
        // but it's extremely unlikely that LibraryThing would ever get a configurable url
        //noinspection ConstantConditions
        final String siteUrl = SearchEngineRegistry.getByEngineId(SearchSites.LIBRARY_THING)
                                                   .getSiteUrl();

        mVb.registerUrl.setOnClickListener(v -> {
            final Uri uri = Uri.parse(siteUrl + '/');
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        mVb.devKeyUrl.setOnClickListener(v -> {
            final Uri uri = Uri.parse(siteUrl + "/services/keys.php");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        final String key = PreferenceManager.getDefaultSharedPreferences(this)
                                            .getString(LibraryThingSearchEngine.PREFS_DEV_KEY, "");
        mVb.devKey.setText(key);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_validate, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_ACTION_CONFIRM:
                validateKey();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void validateKey() {
        //noinspection ConstantConditions
        final String devKey = mVb.devKey.getText().toString().trim();
        PreferenceManager.getDefaultSharedPreferences(this)
                         .edit()
                         .putString(LibraryThingSearchEngine.PREFS_DEV_KEY, devKey)
                         .apply();

        if (!devKey.isEmpty()) {
            Snackbar.make(mVb.devKey, R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mValidateKeyTask.startTask();
        } else {
            showError(mVb.lblDevKey, getString(R.string.vldt_non_blank_required));
        }
    }
}
