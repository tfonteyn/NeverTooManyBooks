package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
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
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;
import com.eleybourn.bookcatalogue.settings.SettingsActivity;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.ThemeUtils;
import com.google.android.material.navigation.NavigationView;

/**
 * Base class for all (most) Activity's.
 *
 * @author pjw
 */
public abstract class BaseActivity
        extends AppCompatActivity
        implements
        NavigationView.OnNavigationItemSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    /** The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    @Nullable
    private NavigationView mNavigationView;

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
        setTheme(ThemeUtils.getThemeResId());

        super.onCreate(savedInstanceState);

        int layoutId = getLayoutId();
        if (layoutId != 0) {
            setContentView(layoutId);
        }

        /*
         Using a {@link NavigationView} and matching {@link Toolbar}
         see https://developer.android.com/training/implementing-navigation/nav-drawer
         */
        setDrawerLayout((DrawerLayout) findViewById(R.id.drawer_layout));
        setNavigationView((NavigationView) findViewById(R.id.nav_view));

        initToolbar();
    }

    /**
     * When resuming, recreate activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.info(this, Tracker.State.Enter,
                        "BaseActivity.onResume", LocaleUtils.toString(this));
        }

        if (App.isInNeedOfRecreating()) {
            recreate();
            App.setIsRecreating();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.info(this, Tracker.State.Exit,
                            "BaseActivity.onResume", "Recreate!");
            }

        } else if (App.isRecreating()) {
            App.clearRecreateFlag();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.info(this, Tracker.State.Exit,
                            "BaseActivity.onResume", "isRecreating");
            }

        } else {
            // this is just paranoia... the flag should already have been cleared.
            App.clearRecreateFlag();
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
                Logger.info(this, Tracker.State.Exit,
                            "BaseActivity.onResume", "Resuming");
            }
        }
    }

    /**
     * Setup the application toolbar to show either 'Home/Hamburger' or 'Up' button.
     */
    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        // Normal setup of the action bar now
        ActionBar bar = getSupportActionBar();
        if (bar != null) {

            // default on all activities is to show the "up" (back) button
            bar.setDisplayHomeAsUpEnabled(true);

            //bar.setDisplayShowHomeEnabled(true);

            // but if we are at the top activity
            if (isTaskRoot()) {
                // then we want the hamburger menu.
                bar.setHomeAsUpIndicator(R.drawable.ic_menu);
            }
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public NavigationView getNavigationView() {
        return mNavigationView;
    }

    private void setNavigationView(@Nullable final NavigationView navigationView) {
        mNavigationView = navigationView;
        if (mNavigationView != null) {
            mNavigationView.setNavigationItemSelectedListener(this);
        }
    }

    public void closeNavigationDrawer() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawers();
        }
    }

    @Override
    @CallSuper
    public boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();
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
                                       UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES);
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
     * @param drawerLayout your custom one
     */
    private void setDrawerLayout(@Nullable final DrawerLayout drawerLayout) {
        mDrawerLayout = drawerLayout;
    }

    /**
     * Called when a menu item is selected.
     *
     * @param item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
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
                // otherwise, home is an 'up' event.
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

        // some activities MIGHT support the navigation panel, but are not (always)
        // reacting to results, or need to react. Some debug/reminder logging here
        switch (requestCode) {

            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.info(this, "onActivityResult",
                                "REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;

            case UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.info(this, "onActivityResult",
                                "REQ_NAV_PANEL_EDIT_PREFERRED_STYLES");
                }
                return;

            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.info(this, "onActivityResult",
                                "REQ_NAV_PANEL_ADMIN");
                }
                return;

            case UniqueId.REQ_NAV_PANEL_SETTINGS:
                if (BuildConfig.DEBUG && (DEBUG_SWITCHES.ON_ACTIVITY_RESULT || DEBUG_SWITCHES.RECREATE_ACTIVITY)) {
                    Logger.info(this, "onActivityResult",
                                "REQ_NAV_PANEL_SETTINGS");
                }
                return;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    // lowest level of our Activities, see if we missed anything
                    // that we should not miss.
                    Logger.info(this, "onActivityResult",
                                "NOT HANDLED",
                                "requestCode=" + requestCode + ',',
                                "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
        }

        Tracker.exitOnActivityResult(this);
    }


    /**
     * Check if edits need saving.
     * If they don't, simply finish the activity, otherwise ask the user.
     *
     * @param isDirty if <tt>true</tt> ask the user if it's ok to exit this activity.
     *                Otherwise, just finish.
     */
    public void finishIfClean(final boolean isDirty) {
        if (isDirty) {
            StandardDialogs.showConfirmUnsavedEditsDialog(
                    this,
                    /* only runs if user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            setResult(Activity.RESULT_CANCELED);
                            finish();
                        }
                    });
        } else {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    }

    /**
     * Apply preference changes.
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.info(this, Tracker.State.Enter, "BaseActivity.onSharedPreferenceChanged",
                        "key=" + key);
        }

        // Trigger a recreate of this activity, if the setting has changed.
        switch (key) {
            case Prefs.pk_ui_theme:
                if (ThemeUtils.applyPreferred(this)) {
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
