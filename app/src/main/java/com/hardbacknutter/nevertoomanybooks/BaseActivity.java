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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

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
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
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
     * tag of fragment to display if an Activity supports multiple.
     * <p>
     * <br>type: {@code String}
     */
    public static final String BKEY_FRAGMENT_TAG = TAG + ":fragment";

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
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(localizedContext);
        mInitialLocaleSpec = AppLocale.getInstance().getPersistedLocaleSpec(prefs);
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
     * Manually load a fragment into the given container using replace.
     * <p>
     * Not added to the BackStack.
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    protected void replaceFragment(@IdRes final int containerViewId,
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
              .replace(containerViewId, fragment, fragmentTag)
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
    protected boolean recreateIfNeeded() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (sActivityRecreateStatus == ACTIVITY_REQUIRES_RECREATE
            || AppLocale.getInstance().isChanged(prefs, mInitialLocaleSpec)
            || NightMode.getInstance().isChanged(prefs, mInitialNightModeId)) {

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
    void setNavigationItemVisibility(@SuppressWarnings("SameParameterValue")
                                     @IdRes final int itemId,
                                     final boolean visible) {
        if (mNavigationView != null) {
            mNavigationView.getMenu().findItem(itemId).setVisible(visible);
        }
    }

    @CallSuper
    boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        final int itemId = item.getItemId();

        if (itemId == R.id.nav_manage_bookshelves) {
            // child classes which have a 'current bookshelf' should override
            final Intent intent = new Intent(this, HostingActivity.class)
                    .putExtra(BKEY_FRAGMENT_TAG, EditBookshelvesFragment.TAG);
            startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES);
            return true;

        } else if (itemId == R.id.nav_settings) {
            final Intent intent = new Intent(this, SettingsActivity.class);
            startActivityForResult(intent, RequestCode.NAV_PANEL_SETTINGS);
            return true;

        } else if (itemId == R.id.nav_about) {
            final Intent intent = new Intent(this, HostingActivity.class)
                    .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, AboutFragment.TAG);
            startActivity(intent);
            return true;
        }
        return false;
    }

    boolean isNavigationDrawerVisible() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerVisible(GravityCompat.START);
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

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES:
                // Nothing to do here, but see BooksOnBookshelf Activity
                // where we override this one
                break;

            case RequestCode.NAV_PANEL_SETTINGS:
                // Handle the generic return flag requiring a recreate of the current Activity
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, "data");
                    if (data.getBooleanExtra(BKEY_PREF_CHANGE_REQUIRES_RECREATE, false)) {
                        sActivityRecreateStatus = ACTIVITY_REQUIRES_RECREATE;
                    }
                }

                // unconditional exit of the app
                if (resultCode == MaintenanceFragment.RESULT_ALL_DATA_DESTROYED) {
                    finish();
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @IntDef({ACTIVITY_IS_RUNNING,
             ACTIVITY_REQUIRES_RECREATE,
             ACTIVITY_IS_RECREATING})
    @Retention(RetentionPolicy.SOURCE)
    @interface ActivityStatus {

    }

}
