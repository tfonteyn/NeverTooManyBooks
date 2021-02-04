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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportViewModel;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentCalibreBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;

/**
 * Starting point for sending and importing books with Calibre.
 */
public class CalibreAdminFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "CalibreAdminFragment";

    /** View Binding. */
    private FragmentCalibreBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentCalibreBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        //noinspection ConstantConditions
        getActivity().setTitle(R.string.site_calibre);

        mVb.btnLibMap.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl(v.getContext());
            if (!url.isEmpty()) {
                final Bundle args = new Bundle();
                args.putString(ImportViewModel.BKEY_URL, url);

                final Fragment fragment = new CalibreLibraryMappingFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .addToBackStack(CalibreLibraryMappingFragment.TAG)
                  .replace(R.id.main_fragment, fragment, CalibreLibraryMappingFragment.TAG)
                  .commit();
            } else {
                openSettings();
            }
        });
        mVb.btnImport.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl(v.getContext());
            if (!url.isEmpty()) {
                final Bundle args = new Bundle();
                args.putString(ImportViewModel.BKEY_URL, url);

                final Fragment fragment = new ImportFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .addToBackStack(ImportFragment.TAG)
                  .replace(R.id.main_fragment, fragment, ImportFragment.TAG)
                  .commit();
            } else {
                openSettings();
            }
        });
        mVb.btnSendUpdatedBooks.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl(v.getContext());
            if (!url.isEmpty()) {
                final Bundle args = new Bundle();
                args.putParcelable(ExportFragment.BKEY_ENCODING, ArchiveEncoding.CalibreCS);

                final Fragment fragment = new ExportFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .addToBackStack(ExportFragment.TAG)
                  .replace(R.id.main_fragment, fragment, ExportFragment.TAG)
                  .commit();
            } else {
                openSettings();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(Menu.NONE, R.id.MENU_SETTINGS, 0, R.string.lbl_settings)
            .setIcon(R.drawable.ic_settings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_SETTINGS) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        final Fragment fragment = new CalibrePreferencesFragment();
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .addToBackStack(CalibrePreferencesFragment.TAG)
          .replace(R.id.main_fragment, fragment, CalibrePreferencesFragment.TAG)
          .commit();
    }

    public static class ResultContract
            extends ActivityResultContract<Void, Bundle> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @Nullable final Void aVoid) {
            return new Intent(context, FragmentHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, CalibreAdminFragment.TAG);
        }

        @Override
        @Nullable
        public Bundle parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return null;
            }
            return intent.getExtras();
        }
    }
}
