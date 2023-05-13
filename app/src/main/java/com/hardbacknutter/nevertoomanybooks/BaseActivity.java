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
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SettingsContract;

/**
 * Base class for all Activity's (except the startup and the crop activity).
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /**
     * Used to create a delay when leaving an Activity, showing a Message, ...
     * {@link Snackbar#LENGTH_SHORT} is 1500 ms.
     * {@link Snackbar#LENGTH_LONG} is 2750 ms.
     */
    public static final int DELAY_SHORT_MS = 1500;
    public static final int DELAY_LONG_MS = 2800;

    private final ActivityResultLauncher<Long> manageBookshelvesBaseLauncher =
            registerForActivityResult(new EditBookshelvesContract(),
                                      bookshelfId -> {
                                      });
    /** Optional - The side/navigation panel. */
    @Nullable
    DrawerLayout drawerLayout;
    private RecreateViewModel recreateVm;
    private final ActivityResultLauncher<String> editSettingsLauncher =
            registerForActivityResult(new SettingsContract(), recreateActivity -> {
                if (recreateActivity) {
                    recreateVm.setRecreationRequired();
                }
            });
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView navigationView;

    private Toolbar toolbar;

    /**
     * Hide the keyboard.
     *
     * @param view a View that can be used to get the context and the window token
     */
    public static void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    @NonNull
    public Toolbar getToolbar() {
        if (toolbar == null) {
            toolbar = findViewById(R.id.toolbar);
            Objects.requireNonNull(toolbar, "R.id.toolbar");
        }
        return toolbar;
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

    @CallSuper
    void initNavDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            navigationView = drawerLayout.findViewById(R.id.nav_view);
            navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    /**
     * Manually add the first fragment for the given container. Not added to the BackStack.
     * <p>
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     *
     * @throws IllegalStateException if the Fragment cannot be instantiated
     */
    void addFirstFragment(@SuppressWarnings("SameParameterValue")
                          @IdRes final int containerViewId,
                          @NonNull final Class<? extends Fragment> fragmentClass,
                          @NonNull final String fragmentTag) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(fragmentTag) == null) {
            final Fragment fragment;
            try {
                fragment = fragmentClass.getConstructor().newInstance();
            } catch (@NonNull final IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Not a fragment: " + fragmentClass.getName());
            } catch (final NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Other failure: " + fragmentClass.getName());
            }
            fragment.setArguments(getIntent().getExtras());

            fm.beginTransaction()
              .setReorderingAllowed(true)
              .add(containerViewId, fragment, fragmentTag)
              .commit();
        }
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        recreateIfNeeded();
    }

    /**
     * Trigger a recreate() on the Activity if needed.
     */
    protected void recreateIfNeeded() {
        if (recreateVm.isRecreationRequired()) {
            recreate();
        }
    }

    protected boolean isRecreating() {
        return recreateVm.isRecreating();
    }

    /**
     * If the drawer is open and the user click the back-button, close the drawer
     * and ignore the back-press.
     */
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);

        } else {
            // Will call any (enabled) registered OnBackPressedCallback handlers.
            // If there are none registered, the system will call finish().
            // However, if we do have an enabled/registered callback of our own,
            // it is responsible to call finish()
            super.onBackPressed();
        }
    }

    @Nullable
    protected MenuItem getNavigationMenuItem(@SuppressWarnings("SameParameterValue")
                                             @IdRes final int itemId) {
        return navigationView != null ? navigationView.getMenu().findItem(itemId) : null;
    }

    @NonNull
    View getNavigationMenuItemView(final int itemId) {
        //noinspection ConstantConditions
        final View anchor = navigationView.findViewById(itemId);
        // Not 100% we are using a legal way of getting the View...
        Objects.requireNonNull(anchor, () -> "navigationView.findViewById(" + itemId + ")");
        return anchor;
    }

    @CallSuper
    boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        closeNavigationDrawer();

        if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // child classes which have a 'current bookshelf' should
            // override and pass the current bookshelf id
            manageBookshelvesBaseLauncher.launch(0L);
            return true;

        } else if (itemId == R.id.MENU_SETTINGS) {
            editSettingsLauncher.launch(null);
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

    void closeNavigationDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
}
