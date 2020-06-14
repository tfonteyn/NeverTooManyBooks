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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.NightModeUtils;

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
    @NightModeUtils.NightModeId
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
        final Context localizedContext = LocaleUtils.applyLocale(base);
        super.attachBaseContext(localizedContext);
        // preserve, so we can check for changes in onResume.
        mInitialLocaleSpec = LocaleUtils.getPersistedLocaleSpec(localizedContext);
    }

    /**
     * Wrapper for {@link #setContentView}. Called from onCreate after the theme was set.
     */
    protected void onSetContentView() {
        // no UI by default.
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        // We preserve it, so we can check for changes in onResume.
        mInitialNightModeId = NightModeUtils.applyNightMode(this);

        super.onCreate(savedInstanceState);
        onSetContentView();

        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mNavigationView = findViewById(R.id.nav_view);
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
     * Manually load a fragment into the given container using add.
     * <p>
     * Not added to the BackStack.
     * <strong>The activity extras bundle will be set as arguments.</strong>
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    protected void addFragment(@IdRes final int containerViewId,
                               @NonNull final Class<? extends Fragment> fragmentClass,
                               @NonNull final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, true);
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
        loadFragment(containerViewId, fragmentClass, fragmentTag, false);
    }

    /**
     * Manually load a fragment into the given container.
     * If the fragment is already present, this method does nothing.
     *
     * <p>
     * Not added to the BackStack.
     * <strong>The activity extras bundle will be set as arguments.</strong>
     * <p>
     * TODO: look into {@link androidx.fragment.app.FragmentFactory}
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param tag             tag for the fragment
     * @param isAdd           whether to use add or replace
     */
    private void loadFragment(@IdRes final int containerViewId,
                              @NonNull final Class<? extends Fragment> fragmentClass,
                              @NonNull final String tag,
                              final boolean isAdd) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            final Fragment fragment;
            try {
                fragment = fragmentClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("Not a fragment: " + fragmentClass.getName());
            }
            fragment.setArguments(getIntent().getExtras());
            final FragmentTransaction ft =
                    fm.beginTransaction()
                      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            if (isAdd) {
                ft.add(containerViewId, fragment, tag);
            } else {
                ft.replace(containerViewId, fragment, tag);
            }
            ft.commit();
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
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "onResume|sActivityRecreateStatus=" + sActivityRecreateStatus);
            final Configuration configuration = getResources().getConfiguration();
            Log.d(TAG,
                  "config.smallestScreenWidthDp=" + configuration.smallestScreenWidthDp
                  + "|config.screenWidthDp=" + configuration.screenWidthDp
                  + "|config.screenHeightDp=" + configuration.screenHeightDp);
        }
        recreateIfNeeded();
    }

    /**
     * Trigger a recreate() on the Activity if needed.
     *
     * @return {@code true} if a recreate was triggered.
     */
    @SuppressWarnings("UnusedReturnValue")
    protected boolean recreateIfNeeded() {
        if (sActivityRecreateStatus == ACTIVITY_REQUIRES_RECREATE
            || LocaleUtils.isChanged(this, mInitialLocaleSpec)
            || NightModeUtils.isChanged(this, mInitialNightModeId)) {

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
        new Handler().postDelayed(() -> view.setError(null), ERROR_DELAY_MS);
    }

    /**
     * Set the visibility of a NavigationView menu item.
     *
     * @param itemId  menu item resource id
     * @param visible flag
     */
    void setNavigationItemVisibility(@IdRes final int itemId,
                                     final boolean visible) {
        if (mNavigationView != null) {
            mNavigationView.getMenu().findItem(itemId).setVisible(visible);
        }
    }

    @CallSuper
    boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        switch (item.getItemId()) {
            case R.id.nav_advanced_search: {
                final Intent searchIntent = new Intent(this, FTSSearchActivity.class);
                startActivityForResult(searchIntent, RequestCode.ADVANCED_LOCAL_SEARCH);
                return true;
            }
            case R.id.nav_manage_bookshelves: {
                final Intent intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES);
                return true;
            }
            // case R.id.nav_manage_list_styles: {
            //     // not reachable right now as we don't show the menu option unless
            //     // we're on the main BooksOnBookshelf activity.
            //     // Enabling it elsewhere means we'd need to get a DAO to pass in.
            //     final Intent intent = new Intent(this, PreferredStylesActivity.class)
            //         .putExtra(BooklistStyle.BKEY_STYLE_ID,
            //                   BooklistStyle.getDefaultStyle(this, mDb));
            //     startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
            //     return true;
            // }

            case R.id.nav_goodreads: {
                final Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BKEY_FRAGMENT_TAG, GoodreadsAdminFragment.TAG);
                startActivityForResult(intent, RequestCode.NAV_PANEL_GOODREADS);
                return true;
            }
            case R.id.nav_settings: {
                final Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, RequestCode.NAV_PANEL_SETTINGS);
                return true;
            }
            case R.id.nav_about: {
                startActivity(new Intent(this, About.class));
                return true;
            }

            default:
                return false;
        }
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
        MenuHandler.setupSearch(this, menu);
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

        // generic actions & logging. Anything specific should be done in a child class.
        switch (requestCode) {
            case RequestCode.NAV_PANEL_SETTINGS:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_SETTINGS");
                }

                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    if (data.getBooleanExtra(BKEY_PREF_CHANGE_REQUIRES_RECREATE, false)) {
                        sActivityRecreateStatus = ACTIVITY_REQUIRES_RECREATE;
                    }
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_MANAGE_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_STYLES");
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_IMPORT:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_IMPORT");
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_EXPORT:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EXPORT");
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_GOODREADS:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_GOODREADS");
                }
                return;

            // logging only
            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    // codes for fragments have upper 16 bits in use, don't log those.
                    // the super call will redirect those.
                    if ((requestCode & 0xFF) != 0) {
                        Logger.warn(this, TAG,
                                    "BaseActivity.onActivityResult"
                                    + "|NOT HANDLED"
                                    + "|requestCode=" + requestCode
                                    + "|resultCode=" + resultCode);
                    }
                }
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
