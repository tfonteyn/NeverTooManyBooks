/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.baseactivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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

import com.hardbacknutter.nevertoomanybooks.About;
import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FTSSearchActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Base class for all (most?) Activity's.
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
 *                  if (activity.isGoingToRecreate()) {
 *                      return;
 *                  }
 *              }
 *          }
 *     }
 * </pre>
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /** Locale at {@link #onCreate} time. */
    protected String mInitialLocaleSpec;
    /** Theme at {@link #onCreate} time. */
    @App.ThemeId
    protected int mInitialThemeId;

    /** Optional - The side/navigation panel. */
    @Nullable
    protected DrawerLayout mDrawerLayout;
    /** Optional - The side/navigation menu. */
    @Nullable
    protected NavigationView mNavigationView;

    /**
     * Override this and return the id you need.
     *
     * @return the layout id for this activity, or 0 for none (i.e. no UI View).
     */
    protected int getLayoutId() {
        return 0;
    }

    /**
     * apply the user-preferred Locale before onCreate is called.
     */
    protected void attachBaseContext(@NonNull final Context base) {
        Context localizedContext = LocaleUtils.applyLocale(base);
        super.attachBaseContext(localizedContext);
        // preserve, so we can check for changes in onResume.
        mInitialLocaleSpec = LocaleUtils.getPersistedLocaleSpec(localizedContext);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Theme before super.onCreate is called.
        // We preserve it, so we can check for changes in onResume.
        mInitialThemeId = App.applyTheme(this);

        super.onCreate(savedInstanceState);

        int layoutId = getLayoutId();
        if (layoutId != 0) {
            setContentView(layoutId);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
        // Normal setup of the action bar now
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            // default on all activities is to show the "up" (back) button
            bar.setDisplayHomeAsUpEnabled(true);
            // but if we are at the top activity
            if (isTaskRoot()) {
                // then we want the hamburger menu.
                bar.setHomeAsUpIndicator(R.drawable.ic_menu);
            }
        }

        mDrawerLayout = findViewById(R.id.drawer_layout);
        if (mDrawerLayout != null) {
            mNavigationView = findViewById(R.id.nav_view);
            mNavigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();

        isGoingToRecreate();
    }

    /**
     * Manually load a fragment into the given container using add.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    public void addFragment(@IdRes final int containerViewId,
                            @NonNull final Class fragmentClass,
                            @Nullable final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, true);
    }

    /**
     * Manually load a fragment into the given container using replace.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
     *
     * @param containerViewId to receive the fragment
     * @param fragmentClass   the fragment; must be loadable with the current class loader.
     * @param fragmentTag     tag for the fragment
     */
    public void replaceFragment(@IdRes final int containerViewId,
                                @NonNull final Class fragmentClass,
                                @Nullable final String fragmentTag) {
        loadFragment(containerViewId, fragmentClass, fragmentTag, false);
    }

    /**
     * Manually load a fragment into the given container.
     * <p>
     * Not added to the BackStack.
     * The activity extras bundle will be set as arguments.
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

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(tag) == null) {
            Fragment frag;
            try {
                frag = (Fragment) fragmentClass.newInstance();
            } catch (IllegalAccessException | InstantiationException e) {
                throw new IllegalStateException("not a fragment class: " + fragmentClass.getName());
            }
            frag.setArguments(getIntent().getExtras());
            FragmentTransaction ft = fm.beginTransaction()
                                       .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);

            //URGENT: addToBackStack ?
//            ft.addToBackStack(null);

            if (isAdd) {
                ft.add(containerViewId, frag, tag);
            } else {
                ft.replace(containerViewId, frag, tag);
            }
            ft.commit();
        }
    }

    /**
     * Check if the Locale/Theme was changed, which will trigger the Activity to be recreated.
     *
     * @return {@code true} if a recreate was triggered.
     */
    public boolean isGoingToRecreate() {
        boolean localeChanged = LocaleUtils.isChanged(this, mInitialLocaleSpec);
        if (localeChanged) {
            LocaleUtils.onLocaleChanged();
        }

        if (App.isInNeedOfRecreating() || App.isThemeChanged(mInitialThemeId) || localeChanged) {
            App.setIsRecreating();
            recreate();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "BaseActivity.onResume", "Recreate!");
            }

            return true;

        } else {
            // this is the second time we got here in onResume, so we have been re-created.
            App.clearRecreateFlag();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "BaseActivity.onResume", "Resuming");
            }
        }

        return false;
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
    @SuppressWarnings("SameParameterValue")
    protected void setNavigationItemVisibility(@IdRes final int itemId,
                                               final boolean visible) {
        if (mNavigationView != null) {
            mNavigationView.getMenu().findItem(itemId).setVisible(visible);
        }
    }

    @CallSuper
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();

        switch (item.getItemId()) {
            case R.id.nav_search: {
                if (App.getPrefBoolean(Prefs.pk_search_form_advanced, false)) {
                    return onAdvancedSearchRequested();
                } else {
                    // standard system call.
                    return onSearchRequested();
                }
            }
            case R.id.nav_settings: {
                startActivityForResult(new Intent(this, SettingsActivity.class),
                                       UniqueId.REQ_NAV_PANEL_SETTINGS);
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

    protected void closeNavigationDrawer() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    /**
     * There was a search requested by the user; bring up the advanced form (activity).
     */
    protected boolean onAdvancedSearchRequested() {
        Intent intent = new Intent(this, FTSSearchActivity.class);
        startActivityForResult(intent, UniqueId.REQ_ADVANCED_LOCAL_SEARCH);
        return true;
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
            case android.R.id.home:
                // the home icon is only == hamburger menu, at the top level
                if (isTaskRoot()) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                        return true;
                    }
                }
                // otherwise, home is an 'up' event. Simulate the user pressing the 'back' key.
                onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Logger.enterOnActivityResult(this, requestCode, resultCode, data);

        // generic actions & logging. Anything specific should be done in a child class.
        switch (requestCode) {

            case UniqueId.REQ_NAV_PANEL_SETTINGS:
                if (BuildConfig.DEBUG && (DEBUG_SWITCHES.ON_ACTIVITY_RESULT
                                          || DEBUG_SWITCHES.RECREATE_ACTIVITY)) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_SETTINGS");
                }
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    if (data.getBooleanExtra(UniqueId.BKEY_RECREATE_ACTIVITY, false)) {
                        App.setNeedsRecreating();
                    }
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_EDIT_STYLES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_IMP_EXP:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_IMP_EXP");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_GOODREADS:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_GOODREADS");
                }
                return;

            // logging only
            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    // codes for fragments have upper 16 bits in use, don't log those.
                    // the super call will redirect those.
                    if ((requestCode & 0xFF) != 0) {
                        Logger.warn(this, this, "BaseActivity.onActivityResult",
                                    "NOT HANDLED",
                                    "requestCode=" + requestCode,
                                    "resultCode=" + resultCode);
                    }
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

}
