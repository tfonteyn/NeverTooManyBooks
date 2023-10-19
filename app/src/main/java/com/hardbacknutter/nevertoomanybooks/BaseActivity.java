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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;
import com.hardbacknutter.nevertoomanybooks.widgets.NavDrawer;

/**
 * Base class for all Activity's (except the startup and the crop activity).
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    private final ActivityResultLauncher<Long> manageBookshelvesBaseLauncher =
            registerForActivityResult(new EditBookshelvesContract(),
                                      bookshelfId -> {
                                      });

    /** Handles Activity recreation. */
    private RecreateViewModel recreateVm;

    private final ActivityResultLauncher<String> editSettingsLauncher =
            registerForActivityResult(new SettingsContract(), o -> o.ifPresent(
                    this::onSettingsChanged));

    /**
     * Called when we return from editing the Settings.
     * Override as needed.
     *
     * @param result from the {@link SettingsContract}.
     */
    @CallSuper
    public void onSettingsChanged(@NonNull final SettingsContract.Output result) {
        if (result.isRecreateActivity()) {
            recreateVm.setRecreationRequired();
        }
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        recreateVm = new ViewModelProvider(this).get(RecreateViewModel.class);
        recreateVm.onCreate();

        super.onCreate(savedInstanceState);
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (recreateVm.isRecreationRequired()) {
            recreate();
        }
    }

    protected boolean isRecreating() {
        return recreateVm.isRecreating();
    }

    /**
     * Overridable/extendable handler for the {@link NavigationView} menu.
     *
     * @param navDrawer the caller
     * @param menuItem  the menu item that was clicked
     *
     * @return {@code true} if the menuItem was handled.
     */
    @CallSuper
    boolean onNavigationItemSelected(@NonNull final NavDrawer navDrawer,
                                     @NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        navDrawer.close();

        if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // child classes which have a 'current bookshelf' should
            // override and pass the current bookshelf id instead of 0L
            manageBookshelvesBaseLauncher.launch(0L);
            return true;

        } else if (itemId == R.id.MENU_SETTINGS) {
            editSettingsLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_HELP) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                                     Uri.parse(getString(R.string.help_url))));
            return true;

        } else if (itemId == R.id.MENU_ABOUT) {
            final Intent intent = FragmentHostActivity.createIntent(this, AboutFragment.class);
            intent.putExtra(FragmentHostActivity.BKEY_TOOLBAR_SCROLL_FLAGS,
                            AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL);
            startActivity(intent);
            return true;
        }
        return false;
    }
}
