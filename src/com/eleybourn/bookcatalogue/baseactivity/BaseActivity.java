package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.About;
import com.eleybourn.bookcatalogue.AdminActivity;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.EditBookshelfListActivity;
import com.eleybourn.bookcatalogue.Help;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferredStylesActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.ThemeUtils;

/**
 * Base class for all (most) Activity's
 *
 * @author pjw
 */
abstract public class BaseActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        CanBeDirty {

    /** The side/navigation panel */
    @Nullable
    private DrawerLayout mDrawerLayout;
    @Nullable
    private NavigationView mNavigationView;

    /** when a locale or theme is changed, a restart of the activity is needed */
    private boolean mRestartActivityOnResume = false;

    /** universal flag used to indicate something was changed and not saved (yet) */
    private boolean mIsDirty = false;

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
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
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

    private void setNavigationView(final @Nullable NavigationView navigationView) {
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
    public boolean onNavigationItemSelected(final @NonNull MenuItem menuItem) {
        closeNavigationDrawer();

        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.nav_search: {
                onSearchRequested();
                return true;
            }
            case R.id.nav_manage_bookshelves: {
                intent = new Intent(this, EditBookshelfListActivity.class);
                startActivityForResult(intent, EditBookshelfListActivity.REQUEST_CODE); /* 41e84172-5833-4906-a891-8df302ecc190 */
                break;
            }
            case R.id.nav_edit_list_styles: {
                intent = new Intent(this, BooklistPreferredStylesActivity.class);
                startActivityForResult(intent, BooklistPreferredStylesActivity.REQUEST_CODE); /* 13854efe-e8fd-447a-a195-47678c0d87e7 */
                return true;
            }
            case R.id.nav_admin: {
                intent = new Intent(this, AdminActivity.class);
                startActivityForResult(intent, AdminActivity.REQUEST_CODE); /* 7f46620d-7951-4637-8783-b410730cd460 */
                return true;
            }
            case R.id.nav_about: {
                intent = new Intent(this, About.class);
                startActivity(intent);
                return true;
            }
            case R.id.nav_help: {
                intent = new Intent(this, Help.class);
                startActivity(intent);
                return true;
            }
            case R.id.nav_debug_dump_events: {
                Logger.info(this, Tracker.getEventsInfo());
                return true;
            }
        }

        return false;
    }

    /**
     * @param drawerLayout your custom one
     */
    private void setDrawerLayout(final @Nullable DrawerLayout drawerLayout) {
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
    public boolean onOptionsItemSelected(final @NonNull MenuItem item) {
        switch (item.getItemId()) {
            // Default handler for home icon
            case android.R.id.home: {
                // the home icon is only a hamburger at the top level
                if (isTaskRoot()) {
                    if (mDrawerLayout != null) {
                        mDrawerLayout.openDrawer(GravityCompat.START);
                        return true;
                    }
                }
                // otherwise, home is an 'up' event.
                setResult(Activity.RESULT_CANCELED);
                finishIfClean();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    @CallSuper
    protected void onActivityResult(final int requestCode, final int resultCode, final @Nullable Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        // some activities MIGHT support the navigation panel, but are not (always) reacting to results,
        // or need to react. Some debug/reminder logging here
        switch (requestCode) {
            case EditBookshelfListActivity.REQUEST_CODE:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this, "navigation panel EditBookshelfListActivity.REQUEST_CODE");
                }
                return;
            case BooklistPreferredStylesActivity.REQUEST_CODE:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this, "navigation panel BooklistPreferredStylesActivity.REQUEST_CODE");
                }
                return;
            case AdminActivity.REQUEST_CODE:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    Logger.info(this, "navigation panel AdminActivity.REQUEST_CODE");
                }
                return;

            default:
                if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
                    // lowest level of our Activities, see if we missed anything that we should not miss.
                    Logger.info(this, "BaseActivity|onActivityResult|NOT HANDLED: requestCode=" + requestCode + ", resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);

        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * When the user clicks 'back/up', check if we're clean to leave
     */
    @Override
    @CallSuper
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        finishIfClean();
    }

    /**
     * Check if edits need saving, and finish the activity if not
     */
    public void finishIfClean() {
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this,
                    /* run if user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                        /* if they click 'cancel', the dialog just closes without further actions */
                    });
        } else {
            finish();
        }
    }

    /**
     * When resuming, restart activity if needed
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
            BookCatalogueApp.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        Tracker.exitOnResume(this);
    }

    @Override
    protected void onPause() {
        // stop listening for changes
        BookCatalogueApp.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /**
     * Apply preference changes
     */
    @Override
    @CallSuper
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        switch (key) {
            case ThemeUtils.PREF_APP_THEME: {
                if (ThemeUtils.loadPreferred()) {
                    this.setTheme(ThemeUtils.getThemeResId());
                }
                break;
            }
            case LocaleUtils.PREF_APP_LOCALE: {
                // Trigger a restart of this activity in onResume, if the locale has changed.
                LocaleUtils.loadPreferred();
                if (LocaleUtils.loadPreferred()) {
                    LocaleUtils.apply(getBaseContext().getResources());

                    mRestartActivityOnResume = true;
                }

                break;
            }
        }
    }
}
