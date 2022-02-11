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
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncCalibreBinding;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment;

/**
 * Starting point for sending and importing books with Calibre.
 * <p>
 * The user can specify the usual import/export options for new and/or updated books.
 * We do not yet support updating single books, nor a list of book ids.
 * <p>
 * The sync-date is set on the <strong>Library</strong> and NOT on individual books.
 */
@Keep
public class CalibreSyncFragment
        extends BaseFragment {

    public static final String TAG = "CalibreSyncFragment";

    @NonNull
    private final MenuProvider mToolbarMenuProvider = new ToolbarMenuProvider();

    /** View Binding. */
    private FragmentSyncCalibreBinding mVb;

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

        final Toolbar toolbar = getToolbar();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.action_synchronize);

        mVb.btnLibMap.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl();
            if (url.isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putParcelable(SyncServer.BKEY_SITE, SyncServer.CalibreCS);

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
            if (CalibreContentServer.getHostUrl().isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putParcelable(SyncServer.BKEY_SITE, SyncServer.CalibreCS);

                final Fragment fragment = new SyncReaderFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(SyncReaderFragment.TAG)
                  .replace(R.id.main_fragment, fragment, SyncReaderFragment.TAG)
                  .commit();
            }
        });
        mVb.btnExport.setOnClickListener(v -> {
            if (CalibreContentServer.getHostUrl().isEmpty()) {
                openSettings();
            } else {
                final Bundle args = new Bundle();
                args.putParcelable(SyncServer.BKEY_SITE, SyncServer.CalibreCS);

                final Fragment fragment = new SyncWriterFragment();
                fragment.setArguments(args);
                final FragmentManager fm = getParentFragmentManager();
                fm.beginTransaction()
                  .setReorderingAllowed(true)
                  .addToBackStack(SyncWriterFragment.TAG)
                  .replace(R.id.main_fragment, fragment, SyncWriterFragment.TAG)
                  .commit();
            }
        });
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

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(R.id.MENU_GROUP_CALIBRE, R.id.MENU_CALIBRE_SETTINGS, 0, R.string.lbl_settings)
                .setIcon(R.drawable.ic_baseline_settings_24);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_CALIBRE_SETTINGS) {
                openSettings();
                return true;
            }
            return false;
        }
    }
}
