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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.hardbacknutter.nevertoomanybooks.backup.calibre.CalibreContentServer;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsHostActivity;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.NightMode;

/**
 * Base class for all Activity's (except the startup activity).
 * <p>
 * Fragments should implement:
 * <pre>
 *     {@code
 *          @Override
 *          @CallSuper
 *          public void onResume() {
 *              super.onResume();
 *              if (getActivity() instanceof BaseActivity) {
 *                  BaseActivity activity = (BaseActivity) getActivity();
 *                  if (activity.maybeRecreate()) {
 *                      return;
 *                  }
 *              }
 *
 *              // do stuff here
 *          }
 *     }
 * </pre>
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /** Used by {@link #showError} Snackbar.LENGTH_LONG is 2750 ms. */
    public static final int ERROR_DELAY_MS = 3000;

    /** Log tag. */
    private static final String TAG = "BaseActivity";

    /**
     * Something changed (or not) that warrants a recreation of the caller to be needed.
     * This is normally/always set by one of the settings components if they decide the
     * use changed some setting that required the caller to be recreated.
     *
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_PREF_CHANGE_REQUIRES_RECREATE = TAG + ":recreate";

    /** Situation normal. */
    private static final int ACTIVITY_IS_RUNNING = 0;
    /** Activity is in need of recreating. */
    private static final int ACTIVITY_REQUIRES_RECREATE = 1;
    /** A {@link #recreate()} action has been triggered. */
    private static final int ACTIVITY_IS_RECREATING = 2;

    /**
     * internal; Stage of Activity  doing/needing setIsRecreating() action.
     * See {@link #onResume()}.
     * <p>
     * Note this is a static!
     */
    @ActivityStatus
    private static int sActivityRecreateStatus = ACTIVITY_IS_RUNNING;
    private final ActivityResultLauncher<Long> mManageBookshelvesBaseLauncher =
            registerForActivityResult(new EditBookshelvesFragment.ResultContract(), bookshelfId -> {
                // Nothing to do here, but see BooksOnBookshelf Activity
                // where we override this one
            });
    /** Locale at {@link #onCreate} time. */
    private String mInitialLocaleSpec;
    /** Night-mode at {@link #onCreate} time. */
    @NightMode.NightModeId
    private int mInitialNightModeId;
    /** Optional - The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView mNavigationView;

    private final ActivityResultLauncher<Bundle> mSettingsLauncher = registerForActivityResult(
            new SettingsHostActivity.ResultContract(SettingsFragment.TAG), data -> {

                updateNavigationMenuVisibility();

                if (data != null) {
                    // Handle the generic return flag requiring a recreate of the current Activity
                    if (data.getBoolean(BKEY_PREF_CHANGE_REQUIRES_RECREATE, false)) {
                        sActivityRecreateStatus = ACTIVITY_REQUIRES_RECREATE;
                    }

                    // unconditional exit of the app
                    if (data.getBoolean(MaintenanceFragment.BKEY_RESTART_APP, false)) {
                        finish();
                    }
                }

            });
    /** Flag indicating this Activity is a self-proclaimed root Activity. */
    private boolean mHomeIsRootMenu;

    /**
     * Hide the keyboard.
     *
     * @param view a View that can be used to get the context and the window token
     */
    public static void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        // apply the user-preferred Locale before onCreate is called.
        final Context localizedContext = AppLocale.getInstance().apply(base);
        super.attachBaseContext(localizedContext);
        // preserve, so we can check for changes in onResume.
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(localizedContext);
        mInitialLocaleSpec = AppLocale.getInstance().getPersistedLocaleSpec(global);
    }

    /**
     * Wrapper for {@link #setContentView}.
     * Called from {@link #onCreate} after the theme was set and
     * before the DrawerLayout and Toolbar is setup.
     */
    protected void onSetContentView() {
        // no UI by default.
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        // We preserve it, so we can check for changes in onResume.
        mInitialNightModeId = NightMode.getInstance().apply(this);

        super.onCreate(savedInstanceState);
        onSetContentView();

        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mNavigationView = mDrawerLayout.findViewById(R.id.nav_view);
            mNavigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        // Normal setup of the action bar now
        updateActionBar(isTaskRoot());
    }

    /**
     * Manually add the first fragment for the given container. Not added to the BackStack.
     * <p>
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    protected void addFirstFragment(@SuppressWarnings("SameParameterValue")
                                    @IdRes final int containerViewId,
                                    @NonNull final Class<? extends Fragment> fragmentClass,
                                    @NonNull final String fragmentTag) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(fragmentTag) == null) {
            final Fragment fragment;
            try {
                fragment = fragmentClass.newInstance();
            } catch (final IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Not a fragment: " + fragmentClass.getName());
            }
            fragment.setArguments(getIntent().getExtras());

            fm.beginTransaction()
              // FIXME: https://issuetracker.google.com/issues/169874632
              //   .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
              .add(containerViewId, fragment, fragmentTag)
              .commit();
        }
    }

    void updateActionBar(final boolean isRoot) {
        mHomeIsRootMenu = isRoot;
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // default on all activities is to show the "up" (back) button
            actionBar.setDisplayHomeAsUpEnabled(true);

            if (mHomeIsRootMenu) {
                actionBar.setHomeAsUpIndicator(R.drawable.ic_toolbar_home);
            } else {
                actionBar.setHomeAsUpIndicator(null);
            }
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
     *
     * @return {@code true} if a recreate was triggered.
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean recreateIfNeeded() {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        if (sActivityRecreateStatus == ACTIVITY_REQUIRES_RECREATE
            || AppLocale.getInstance().isChanged(global, mInitialLocaleSpec)
            || NightMode.getInstance().isChanged(global, mInitialNightModeId)) {

            sActivityRecreateStatus = ACTIVITY_IS_RECREATING;
            recreate();
            return true;

        } else {
            sActivityRecreateStatus = ACTIVITY_IS_RUNNING;
        }

        return false;
    }

    boolean isRecreating() {
        return sActivityRecreateStatus == ACTIVITY_IS_RECREATING;
    }

    /**
     * If the drawer is open and the user click the back-button, close the drawer
     * and ignore the back-press.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);

        } else {
            // Will call any (enabled) registered OnBackPressedCallback handlers.
            // If there are none registered, the system will call finish().
            // However, if we do have an enabled/registered callback of our own,
            // it is responsible to call finish()
            super.onBackPressed();
        }
    }

    /**
     * Show an error text on the given view.
     * It will automatically be removed after {@link #ERROR_DELAY_MS}.
     *
     * @param view  on which to set the error
     * @param error text to set
     */
    protected void showError(@NonNull final TextInputLayout view,
                             @NonNull final CharSequence error) {
        view.setError(error);
        view.postDelayed(() -> view.setError(null), ERROR_DELAY_MS);
    }

    /**
     * Set the visibility of a NavigationView menu item.
     *
     * @param itemId  menu item resource id
     * @param visible flag
     */
    private void setNavigationItemVisibility(@IdRes final int itemId,
                                             final boolean visible) {
        if (mNavigationView != null) {
            final MenuItem item = mNavigationView.getMenu().findItem(itemId);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    }

    public void updateNavigationMenuVisibility() {
        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(this);

        setNavigationItemVisibility(R.id.nav_goodreads, GoodreadsManager.isShowSyncMenus(global));

        final boolean calibre = CalibreContentServer.isShowSyncMenus(global);
        setNavigationItemVisibility(R.id.nav_calibre_import, calibre);
        setNavigationItemVisibility(R.id.nav_calibre_export, calibre);
    }

    @CallSuper
    boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        final int itemId = item.getItemId();

        if (itemId == R.id.nav_manage_bookshelves) {
            // child classes which have a 'current bookshelf' should
            // override and pass the current bookshelf id
            mManageBookshelvesBaseLauncher.launch(0L);
            return true;

        } else if (itemId == R.id.nav_settings) {
            mSettingsLauncher.launch(null);
            return true;

        } else if (itemId == R.id.nav_about) {
            final Intent intent = new Intent(this, FragmentHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, AboutFragment.TAG);
            startActivity(intent);
            return true;
        }
        return false;
    }

    void closeNavigationDrawer() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        MenuHelper.setupSearchActionView(this, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * TODO:  https://developer.android.com/training/appbar/up-action
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            // Default handler for home icon
            case android.R.id.home: {
                // Is this the real (or self-proclaimed) root activity?
                if (isTaskRoot() && mHomeIsRootMenu) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                        return true;
                    }
                }
                // otherwise, home is an 'up' event. Simulate the user pressing the 'back' key.
                onBackPressed();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @IntDef({ACTIVITY_IS_RUNNING,
             ACTIVITY_REQUIRES_RECREATE,
             ACTIVITY_IS_RECREATING})
    @Retention(RetentionPolicy.SOURCE)
    @interface ActivityStatus {

    }

}
