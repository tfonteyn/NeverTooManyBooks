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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.About;
import com.hardbacknutter.nevertoomanybooks.AdminActivity;
import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.EditBookshelfListActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.debug.Tracker;
import com.hardbacknutter.nevertoomanybooks.settings.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsActivity;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Base class for all (most?) Activity's.
 */
public abstract class BaseActivity
        extends AppCompatActivity {

    /** Locale at {@link #onCreate} time. */
    protected String mInitialLocaleSpec;
    /** Theme at {@link #onCreate} time. */
    protected int mInitialThemeId;

    /** The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;

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

        NavigationView navigationView = findViewById(R.id.nav_view);
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();

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
        } else {
            // this is the second time we got here in onResume, so we have been re-created.
            App.clearRecreateFlag();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "BaseActivity.onResume", "Resuming");
            }
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

    @CallSuper
    private boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }

        switch (item.getItemId()) {
            case R.id.nav_search:
                //FIXME: onSearchRequested only functional when current Activity is BoB
                // i.o.w. it works because BoB overrides it... and not 'thanks' to the search API.
                // https://developer.android.com/guide/topics/search/search-dialog
                onSearchRequested();
                return true;

            case R.id.nav_manage_bookshelves:
                startActivityForResult(new Intent(this, EditBookshelfListActivity.class),
                                       UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES);
                return true;

            case R.id.nav_edit_list_styles:
                startActivityForResult(new Intent(this, PreferredStylesActivity.class),
                                       UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                return true;

            case R.id.nav_settings:
                startActivityForResult(new Intent(this, SettingsActivity.class),
                                       UniqueId.REQ_NAV_PANEL_SETTINGS);
                return true;

            case R.id.nav_admin:
                startActivityForResult(new Intent(this, AdminActivity.class),
                                       UniqueId.REQ_NAV_PANEL_ADMIN);
                return true;

            case R.id.nav_about:
                startActivity(new Intent(this, About.class));
                return true;

            default:
                return false;
        }
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
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

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
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "BaseActivity.onActivityResult",
                                 "REQ_NAV_PANEL_ADMIN");
                }
                return;

            // logging only
            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    // codes for fragments have upper 16 bits in use, don't log those.
                    // the super call will redirect those.
                    if ((requestCode & 0xFF) != 0) {
                        Logger.warn(this, "BaseActivity.onActivityResult",
                                    "NOT HANDLED",
                                    "requestCode=" + requestCode,
                                    "resultCode=" + resultCode);
                    }
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

}
