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
import android.os.Build;
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
import androidx.appcompat.app.AppCompatDelegate;
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

import com.hardbacknutter.nevertoomanybooks.booklist.prefs.PIntString;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAdminFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

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
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_RECREATE = TAG + ":recreate";

    /**
     * We're not using the actual {@link AppCompatDelegate} mode constants
     * due to 'day-night' depending on the OS version.
     */
    private static final int THEME_INVALID = -1;
    private static final int THEME_DAY_NIGHT = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;
    /** The default theme to use. */
    @ThemeId
    private static final int DEFAULT_THEME = THEME_DAY_NIGHT;

    /** Cache the User-specified theme currently in use. '-1' to force an update at App startup. */
    @ThemeId
    private static int sCurrentNightMode = THEME_INVALID;

    /**
     * internal; Stage of Activity  doing/needing setIsRecreating() action.
     * See {@link #onResume()}.
     * <p>
     * Note this is a static!
     */
    private static ActivityStatus sActivityRecreateStatus;

    /** Locale at {@link #onCreate} time. */
    protected String mInitialLocaleSpec;
    /** Theme at {@link #onCreate} time. */
    @ThemeId
    protected int mInitialThemeId;

    /** Optional - The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView mNavigationView;
    private boolean mHomeIsRootMenu;

    /**
     * Apply the user's preferred NightMode.
     * <p>
     * The one and only place where this should get called is in {@code Activity.onCreate}
     * <pre>
     * {@code
     *          public void onCreate(@Nullable final Bundle savedInstanceState) {
     *              // apply the user-preferred Theme before super.onCreate is called.
     *              applyNightMode(this);
     *
     *              super.onCreate(savedInstanceState);
     *          }
     * }
     * </pre>
     *
     * @param context Current context
     *
     * @return the applied mode index.
     */
    @ThemeId
    public static int applyNightMode(@NonNull final Context context) {
        // Always read from prefs.
        sCurrentNightMode = PIntString.getListPreference(context, Prefs.pk_ui_theme, DEFAULT_THEME);

        final int dnMode;
        switch (sCurrentNightMode) {
            case THEME_INVALID:
            case THEME_DAY_NIGHT:
                if (Build.VERSION.SDK_INT >= 29) {
                    dnMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                } else {
                    dnMode = AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY;
                }
                break;

            case THEME_DARK:
                dnMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;

            case THEME_LIGHT:
            default:
                dnMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(dnMode);
        return sCurrentNightMode;
    }

    /**
     * Hide the keyboard.
     */
    public static void hideKeyboard(@NonNull final View view) {
        final InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    protected void showError(@NonNull final TextInputLayout til,
                             @NonNull final CharSequence error) {
        til.setError(error);
        new Handler().postDelayed(() -> til.setError(null), ERROR_DELAY_MS);
    }

    /**
     * Test if the NightMode has changed.
     *
     * @param context Current context
     * @param mode    to check
     *
     * @return {@code true} if the theme was changed
     */
    public boolean isNightModeChanged(@NonNull final Context context,
                                      @ThemeId final int mode) {
        // always reload from prefs.
        sCurrentNightMode = PIntString.getListPreference(context, Prefs.pk_ui_theme, DEFAULT_THEME);
        return mode != sCurrentNightMode;
    }

    protected void setIsRecreating() {
        sActivityRecreateStatus = ActivityStatus.isRecreating;
    }

    boolean isRecreating() {
        final boolean isRecreating = sActivityRecreateStatus == ActivityStatus.isRecreating;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Log.d(TAG, "EXIT"
                       + "|isRecreating=" + isRecreating
                       + "|LanguageUtils=" + LanguageUtils.toDebugString(this));
        }
        return isRecreating;
    }

    private void setNeedsRecreating() {
        sActivityRecreateStatus = ActivityStatus.NeedsRecreating;
    }

    /**
     * Check if the Locale/Theme was changed, which will trigger the Activity to be recreated.
     *
     * @return {@code true} if a recreate was triggered.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean maybeRecreate() {
        final boolean localeChanged = LocaleUtils.isChanged(this, mInitialLocaleSpec);
        if (localeChanged) {
            LocaleUtils.onLocaleChanged();
        }

        if (sActivityRecreateStatus == ActivityStatus.NeedsRecreating
            || isNightModeChanged(this, mInitialThemeId) || localeChanged) {
            setIsRecreating();
            recreate();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Log.d(TAG, "EXIT|BaseActivity.maybeRecreate|Recreate!");
            }

            return true;

        } else {
            // this is the second time we got here, so we have been re-created.
            sActivityRecreateStatus = ActivityStatus.Running;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Log.d(TAG, "EXIT|BaseActivity.maybeRecreate|Resuming");
            }
        }

        return false;
    }

    /**
     * apply the user-preferred Locale before onCreate is called.
     */
    protected void attachBaseContext(@NonNull final Context base) {
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
        mInitialThemeId = applyNightMode(this);

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
                               @NonNull final Class fragmentClass,
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
                                   @NonNull final Class fragmentClass,
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
                              @NonNull final Class fragmentClass,
                              @NonNull final String tag,
                              final boolean isAdd) {

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment fragment;
            try {
                fragment = (Fragment) fragmentClass.newInstance();
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
            Configuration configuration = getResources().getConfiguration();
            Log.d(TAG,
                  "config.smallestScreenWidthDp=" + configuration.smallestScreenWidthDp
                  + "|config.screenWidthDp=" + configuration.screenWidthDp
                  + "|config.screenHeightDp=" + configuration.screenHeightDp);
        }
        maybeRecreate();
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
                Intent searchIntent = new Intent(this, FTSSearchActivity.class);
                startActivityForResult(searchIntent, RequestCode.ADVANCED_LOCAL_SEARCH);
                return true;
            }
            case R.id.nav_manage_bookshelves: {
                Intent intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, RequestCode.NAV_PANEL_EDIT_BOOKSHELVES);
                return true;
            }
//            case R.id.nav_manage_list_styles: {
//                // not reachable right now as we don't show the menu option unless
//                // we're on the main BooksOnBookshelf activity.
//                // Enabling it elsewhere means we'd need to get a DAO to pass in.
//                Intent intent = new Intent(this, PreferredStylesActivity.class)
//                        .putExtra(UniqueId.BKEY_STYLE_ID,
//                                  BooklistStyle.getDefaultStyle(this, mDb));
//                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
//                return true;
//            }

            case R.id.nav_goodreads: {
                Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BKEY_FRAGMENT_TAG, GoodreadsAdminFragment.TAG);
                startActivityForResult(intent, RequestCode.NAV_PANEL_GOODREADS);
                return true;
            }
            case R.id.nav_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
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

    /**
     * Is the home button/icon representing the root menu (or is it the 'up' action).
     *
     * @return {@code true} if it's the real home (hamburger) menu
     */
    private boolean homeIsRootMenu() {
        return isTaskRoot() && mHomeIsRootMenu;
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
                if (homeIsRootMenu()) {
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
                if (BuildConfig.DEBUG && (DEBUG_SWITCHES.ON_ACTIVITY_RESULT
                                          || DEBUG_SWITCHES.RECREATE_ACTIVITY)) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_SETTINGS");
                }
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    if (data.getBooleanExtra(BKEY_RECREATE, false)) {
                        setNeedsRecreating();
                    }
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case RequestCode.NAV_PANEL_EDIT_STYLES:
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

    private enum ActivityStatus {
        /** Situation normal. */
        Running,
        /** Activity is in need of recreating. */
        NeedsRecreating,
        /** A {@link #recreate()} action has been triggered. */
        isRecreating
    }

    @IntDef({THEME_INVALID, THEME_DAY_NIGHT, THEME_DARK, THEME_LIGHT})
    @Retention(RetentionPolicy.SOURCE)
    @interface ThemeId {

    }
}
