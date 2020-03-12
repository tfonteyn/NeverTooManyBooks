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
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
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

import java.util.Objects;

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


    /** Log tag. */
    private static final String TAG = "BaseActivity";
    /**
     * Something changed (or not) that warrants a recreation of the caller to be needed.
     * <p>
     * <br>type: {@code boolean}
     * setResult
     */
    public static final String BKEY_RECREATE = TAG + ":recreate";

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
    @App.ThemeId
    protected int mInitialThemeId;

    /** Optional - The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    /** Optional - The side/navigation menu. */
    @Nullable
    private NavigationView mNavigationView;
    private boolean mHomeIsRootMenu;

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
        mInitialThemeId = App.applyTheme(this);

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

    public void updateActionBar(final boolean isRoot) {
        mHomeIsRootMenu = isRoot;
        final ActionBar bar = getSupportActionBar();
        if (bar != null) {
            // default on all activities is to show the "up" (back) button
            bar.setDisplayHomeAsUpEnabled(true);

            if (mHomeIsRootMenu) {
                bar.setHomeAsUpIndicator(R.drawable.ic_menu);
            } else {
                bar.setHomeAsUpIndicator(null);
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

        maybeRecreate();
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
            || App.isThemeChanged(this, mInitialThemeId) || localeChanged) {
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
                               @Nullable final String fragmentTag) {
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
                                   @Nullable final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, false);
    }

    /**
     * Manually load a fragment into the given container.
     * <p>
     * Not added to the BackStack.
     * <strong>The activity extras bundle will be set as arguments.</strong>
     * <p>
     * TODO: look into {@link androidx.fragment.app.FragmentFactory}
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     * @param isAdd           whether to use add or replace
     */
    private void loadFragment(@IdRes final int containerViewId,
                              @NonNull final Class fragmentClass,
                              @Nullable final String fragmentTag,
                              final boolean isAdd) {
        String tag;
        if (fragmentTag == null) {
            tag = fragmentClass.getName();
        } else {
            tag = fragmentTag;
        }

        final FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag;
            try {
                frag = (Fragment) fragmentClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("not a fragment class: " + fragmentClass.getName());
            }
            frag.setArguments(getIntent().getExtras());
            final FragmentTransaction ft =
                    fm.beginTransaction().setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            if (isAdd) {
                ft.add(containerViewId, frag, tag);
            } else {
                ft.replace(containerViewId, frag, tag);
            }
            ft.commit();
        }
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
            case R.id.nav_manage_bookshelves: {
                Intent intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES);
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
            case R.id.nav_import_export: {
                Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, ImportExportFragment.TAG);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_IMP_EXP);
                return true;
            }
            case R.id.nav_goodreads: {
                Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(UniqueId.BKEY_FRAGMENT_TAG, GoodreadsAdminFragment.TAG);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_GOODREADS);
                return true;
            }
            case R.id.nav_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_SETTINGS);
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
    public boolean onSearchRequested() {
        if (Prefs.isAdvancedSearch(this)) {
            Intent searchIntent = new Intent(this, FTSSearchActivity.class);
            if (onAdvancedSearchRequested(searchIntent)) {
                startActivityForResult(searchIntent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
                return true;
            } else {
                return false;
            }
        } else {
            // standard system call.
            return super.onSearchRequested();
        }
    }

    /**
     * Override to set extra parameters on the passed intent.
     *
     * @return {@code true} if search can be launched, false if activity refuses (blocks)
     */
    protected boolean onAdvancedSearchRequested(@NonNull final Intent searchIntent) {
        return true;
    }

    /**
     * Is the home button/icon representing the root menu (or is it the 'up' action).
     *
     * @return {@code true} if it's the real home (hamburger) menu
     */
    public boolean homeIsRootMenu() {
        return isTaskRoot() && mHomeIsRootMenu;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
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

//            case R.id.MENU_SEARCH: {
//                // Not using SearchView as an action view,
//                // as there simply is not enough space in our toolbar.
//                onSearchRequested();
//                return true;
//            }

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
            case UniqueId.REQ_NAV_PANEL_SETTINGS:
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
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_EDIT_STYLES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_IMP_EXP:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "BaseActivity.onActivityResult|REQ_NAV_PANEL_IMP_EXP");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_GOODREADS:
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
}
