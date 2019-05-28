package com.eleybourn.bookcatalogue.baseactivity;

import android.content.Intent;
import android.content.SharedPreferences;
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

import com.eleybourn.bookcatalogue.About;
import com.eleybourn.bookcatalogue.AdminActivity;
import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.EditBookshelfListActivity;
import com.eleybourn.bookcatalogue.Help;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.settings.SettingsActivity;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

/**
 * Base class for all (most?) Activity's.
 */
public abstract class BaseActivity
        extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Nullable
    FloatingActionButton mFloatingActionButton;
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

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // apply the user-preferred Locale to the configuration before super.onCreate
        LocaleUtils.applyPreferred(this);
        // apply the Theme before super.onCreate
        setTheme(App.getThemeResId());

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

//        mFloatingActionButton = findViewById(R.id.fab);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this::onNavigationItemSelected);
        }
    }

    /**
     * When resuming, recreate activity if needed.
     *
     * The current (2nd) incarnation of restart-logic is still rather broken
     * as it does not deal with back-stack.
     * TOMF:    if (getThemeFromThisContext != App.getTheme) then recreate() ...
     *
     * https://www.hidroh.com/2015/02/25/support-multiple-themes-android-app-part-2/
     *
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(this, "BaseActivity.onResume", LocaleUtils.toDebugString(this));
        }

        if (App.isInNeedOfRecreating()) {
            recreate();
            App.setIsRecreating();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "BaseActivity.onResume", "Recreate!");
            }

        } else if (App.isRecreating()) {
            App.clearRecreateFlag();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.debugExit(this, "BaseActivity.onResume", "isRecreating");
            }

        } else {
            // this is just paranoia... the flag should already have been cleared.
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
    @CallSuper
    public void onBackPressed() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @CallSuper
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }

        switch (item.getItemId()) {
            case R.id.nav_search:
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

            case R.id.nav_help:
                startActivity(new Intent(this, Help.class));
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
                if (BuildConfig.DEBUG && (DEBUG_SWITCHES.ON_ACTIVITY_RESULT || DEBUG_SWITCHES.RECREATE_ACTIVITY)) {
                    Logger.debug(this, "onActivityResult",
                                 "REQ_NAV_PANEL_SETTINGS");
                }
                //noinspection SwitchStatementWithTooFewBranches
                switch (resultCode) {
                    case UniqueId.ACTIVITY_RESULT_RECREATE_NEEDED:
                        App.setNeedsRecreating();
                        break;
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "onActivityResult",
                                 "REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_EDIT_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "onActivityResult",
                                 "REQ_NAV_PANEL_EDIT_STYLES");
                }
                return;

            // logging only
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.debug(this, "onActivityResult",
                                 "REQ_NAV_PANEL_ADMIN");
                }
                return;


            // logging only
            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.warn(this, "onActivityResult",
                                "NOT HANDLED",
                                "requestCode=" + requestCode,
                                "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Apply preference changes.
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(this, "BaseActivity.onSharedPreferenceChanged",
                              "key=" + key);
        }

        // Trigger a recreate of this activity, if the setting has changed.
        switch (key) {
            case Prefs.pk_ui_theme:
                if (App.isThemeChanged(this)) {
                    recreate();
                    App.setIsRecreating();
                }
                break;

            case Prefs.pk_ui_language:
                if (LocaleUtils.isChanged(this)) {
                    recreate();
                    App.setIsRecreating();
                }
                break;
        }
    }
}
