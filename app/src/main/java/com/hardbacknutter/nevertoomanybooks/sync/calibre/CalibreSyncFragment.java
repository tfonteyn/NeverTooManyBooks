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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.ImportFragment;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncCalibreBinding;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;

/**
 * Starting point for sending and importing books with Calibre.
 * <p>
 * Synchronization is done using
 * <ul>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.backup.RecordReader}</li>
 *     <li>{@link com.hardbacknutter.nevertoomanybooks.backup.RecordWriter}</li>
 * </ul>
 * <p>
 * The user can specify the usual import/export options for new and/or updated books.
 * We do not support updating single books, nor a list of book ids.
 */
@Keep
public class CalibreSyncFragment
        extends BaseFragment {

    public static final String TAG = "CalibreSyncFragment";

    /** View Binding. */
    private FragmentSyncCalibreBinding mVb;

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
        mVb = FragmentSyncCalibreBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.action_synchronize);

        mVb.btnLibMap.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl();
            if (url.isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putString(ArchiveEncoding.BKEY_URL, url);

                final Fragment fragment = new CalibreLibraryMappingFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(CalibreLibraryMappingFragment.TAG)
                  .replace(R.id.main_fragment, fragment, CalibreLibraryMappingFragment.TAG)
                  .commit();
            }
        });
        mVb.btnImport.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl();
            if (url.isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putString(ArchiveEncoding.BKEY_URL, url);
                args.putParcelable(ArchiveEncoding.BKEY_ENCODING, ArchiveEncoding.CalibreCS);

                final Fragment fragment = new ImportFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(ImportFragment.TAG)
                  .replace(R.id.main_fragment, fragment, ImportFragment.TAG)
                  .commit();
            }
        });
        mVb.btnSendUpdatedBooks.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl();
            if (url.isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putParcelable(ArchiveEncoding.BKEY_ENCODING, ArchiveEncoding.CalibreCS);

                final Fragment fragment = new ExportFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(ExportFragment.TAG)
                  .replace(R.id.main_fragment, fragment, ExportFragment.TAG)
                  .commit();
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(R.id.MENU_GROUP_CALIBRE, R.id.MENU_CALIBRE_SETTINGS, 0, R.string.lbl_settings)
            .setIcon(R.drawable.ic_baseline_settings_24);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            openSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openSettings() {
        final Fragment fragment = new CalibrePreferencesFragment();
        final FragmentManager fm = getParentFragmentManager();
        fm.beginTransaction()
          .setReorderingAllowed(true)
          .addToBackStack(CalibrePreferencesFragment.TAG)
          .replace(R.id.main_fragment, fragment, CalibrePreferencesFragment.TAG)
          .commit();
    }
}
