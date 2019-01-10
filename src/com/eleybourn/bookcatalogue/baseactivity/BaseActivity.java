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
import com.eleybourn.bookcatalogue.BookCatalogueApp;
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
        SharedPreferences.OnSharedPreferenceChangeListener,
        CanBeDirty {

    /** The side/navigation panel. */
    @Nullable
    private DrawerLayout mDrawerLayout;
    @Nullable
    private NavigationView mNavigationView;

    /** when a locale or theme is changed, a restart of the activity is needed. */
    private boolean mRestartActivityOnResume;

    /** universal flag used to indicate something was changed and not saved (yet). */
    private boolean mIsDirty;

    public boolean isDirty() {
        return mIsDirty;
    }

    public void setDirty(final boolean isDirty) {
        this.mIsDirty = isDirty;
    }

    protected int getLayoutId() {
        return 0;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        // call setTheme before super.onCreate
        setTheme(ThemeUtils.getThemeResId());

        super.onCreate(savedInstanceState);

        int layoutId = 0;

        Bundle extras = this.getIntent().getExtras();
        if (extras != null) {
            layoutId = extras.getInt(UniqueId.BKEY_LAYOUT, 0);
        }
        if (layoutId == 0) {
            layoutId = getLayoutId();
        }

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

        Tracker.exitOnCreate(this);
    }

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

        Intent intent;
        switch (item.getItemId()) {
            case R.id.nav_search:
                onSearchRequested();
                return true;

            case R.id.nav_manage_bookshelves:
                intent = new Intent(this, EditBookshelfListActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES);
                return true;

            case R.id.nav_edit_list_styles:
                intent = new Intent(this, PreferredStylesActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES);
                return true;

            case R.id.nav_admin:
                intent = new Intent(this, AdminActivity.class);
                startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_ADMIN);
                return true;

            case R.id.nav_about:
                intent = new Intent(this, About.class);
                startActivity(intent);
                return true;

            case R.id.nav_help:
                intent = new Intent(this, Help.class);
                startActivity(intent);
                return true;

            default:
                return false;
        }
    }

    /**
     * @param drawerLayout your custom one
     */
    private void setDrawerLayout(@Nullable final DrawerLayout drawerLayout) {
        this.mDrawerLayout = drawerLayout;
    }

    /**
     * This will be called when a menu item is selected.
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
                // the home icon is only a hamburger at the top level
                if (isTaskRoot()) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                        return true;
                    }
                }
                // otherwise, home is an 'up' event.
                finishIfClean();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        // some activities MIGHT support the navigation panel, but are not (always)
        // reacting to results, or need to react. Some debug/reminder logging here
        switch (requestCode) {
            case UniqueId.REQ_NAV_PANEL_EDIT_BOOKSHELVES:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "navigation panel REQ_NAV_PANEL_EDIT_BOOKSHELVES");
                }
                return;
            case UniqueId.REQ_NAV_PANEL_EDIT_PREFERRED_STYLES:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "navigation panel REQ_NAV_PANEL_EDIT_PREFERRED_STYLES");
                }
                return;
            case UniqueId.REQ_NAV_PANEL_ADMIN:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this,
                                "navigation panel REQ_NAV_PANEL_ADMIN");
                }
                return;

            default:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    // lowest level of our Activities, see if we missed anything
                    // that we should not miss.
                    Logger.info(this, "onActivityResult|NOT HANDLED:" +
                            " requestCode=" + requestCode + ',' +
                            " resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);

        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave.
     */
    @Override
    @CallSuper
    public void onBackPressed() {
        finishIfClean();
    }

    /**
     * Check if edits need saving, and finish the activity if not.
     */
    public void finishIfClean() {
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(
                    this,
                    /* run if user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
        } else {
            finish();
        }
    }

    /**
     * When resuming, restart activity if needed.
     */
    @Override
    @CallSuper
    protected void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        if (mRestartActivityOnResume) {
            if (/* always show debug */ BuildConfig.DEBUG) {
                Logger.info(this, "Restarting");
            }
            finish();
            startActivity(getIntent());
        } else {
            // listen for changes
            Prefs.getPrefs().registerOnSharedPreferenceChangeListener(this);
        }

        Tracker.exitOnResume(this);
    }

    @Override
    protected void onPause() {
        // stop listening for changes
        Prefs.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * Apply preference changes.
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(@NonNull final SharedPreferences sharedPreferences,
                                          @NonNull final String key) {

        if (key.equals(BookCatalogueApp.getResourceString(R.string.pk_ui_theme))) {
            if (ThemeUtils.loadPreferred()) {
                this.setTheme(ThemeUtils.getThemeResId());
            }

        } else if (key.equals(BookCatalogueApp.getResourceString(R.string.pk_ui_language))) {
            // Trigger a restart of this activity in onResume, if the locale has changed.
            LocaleUtils.loadPreferred();
            if (LocaleUtils.loadPreferred()) {
                LocaleUtils.apply(getBaseContext().getResources());
                mRestartActivityOnResume = true;
            }
        }
    }
}
