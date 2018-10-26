package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
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
import com.eleybourn.bookcatalogue.Donate;
import com.eleybourn.bookcatalogue.EditBookshelvesActivity;
import com.eleybourn.bookcatalogue.Help;
import com.eleybourn.bookcatalogue.PreferencesActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

/**
 * Base class for all (most) Activity's
 *
 * @author pjw
 */
abstract public class BaseActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    /** The side/navigation panel */
    @Nullable
    private DrawerLayout mDrawerLayout;
    @Nullable
    private NavigationView mNavigationView;

    /** when a locale or theme is changed, a restart of the activity is needed */
    private boolean mReloadOnResume = false;

    /** universal flag used to indicate something was changed */
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
        // call setTheme before super.onCreate
        setTheme(BookCatalogueApp.getThemeResId());
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
    public boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        closeNavigationDrawer();

        Intent intent;
        switch (menuItem.getItemId()) {
            case R.id.nav_search:
                onSearchRequested();
                return true;
            case R.id.nav_manage_bookshelves:
                intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, EditBookshelvesActivity.REQUEST_CODE); /* 41e84172-5833-4906-a891-8df302ecc190 */
                break;
            case R.id.nav_booklist_prefs:
                intent = new Intent(this, BooklistPreferencesActivity.class);
                startActivityForResult(intent, BooklistPreferencesActivity.REQUEST_CODE); /* 9cdb2cbe-1390-4ed8-a491-87b3b1a1edb9 */
                return true;
            case R.id.nav_other_prefs:
                intent = new Intent(this, PreferencesActivity.class);
                startActivityForResult(intent, PreferencesActivity.REQUEST_CODE); /* 46f41e7b-f49c-465d-bea0-80ec85330d1c */
                return true;
            case R.id.nav_admin:
                intent = new Intent(this, AdminActivity.class);
                startActivityForResult(intent, AdminActivity.REQUEST_CODE); /* 7f46620d-7951-4637-8783-b410730cd460 */
                return true;
            case R.id.nav_about:
                intent = new Intent(this, About.class);
                startActivity(intent);
                return true;
            case R.id.nav_help:
                intent = new Intent(this, Help.class);
                startActivity(intent);
                return true;
            case R.id.nav_donate:
                intent = new Intent(this, Donate.class);
                startActivity(intent);
                break;
        }

        return false;
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
                if (mDrawerLayout != null) {
                    mDrawerLayout.openDrawer(GravityCompat.START);
                    return true;
                }

                if (BuildConfig.DEBUG) {
                    Logger.info(this, " BaseActivity.onOptionsItemSelected handling android.R.id.home as onBackPressed");
                }
                // for all activities that were opened with startActivity()
                // where 'home' is treated as 'up', pretend user pressing the back button
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * When the user clicks 'back/up':
     */
    @Override
    @CallSuper
    public void onBackPressed() {
        if (BuildConfig.DEBUG) {
            Logger.info(this, " BaseActivity.onBackPressed with dirty=" + isDirty());
        }

        // Check if edits need saving, and finish the activity if not
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this,
                    /* run when user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            // set default result
                            Intent data = new Intent();
                            data.putExtra(UniqueId.BKEY_BACK_PRESSED, true);
                            setResult(Activity.RESULT_OK, data);  /* onBackPressed */
                            // but overriding classes can override us
                            setActivityResult();
                            finish();
                        }
                        /* if they click 'cancel', the dialog just closes without further actions */
                    });
        } else {
            // set default result
            Intent data = new Intent();
            data.putExtra(UniqueId.BKEY_BACK_PRESSED, true);
            setResult(Activity.RESULT_OK, data); /* onBackPressed */
            // but overriding classes can (arf) override us
            setActivityResult();
            finish();
        }
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            // keep in mind the base method only does logging. Only overriden methods will actually set the result.
            setActivityResult();
        }
        // call super *after* setting the result, so we can override when needed in a fragment
        super.onPause();
    }

    /**
     * Called by {@link BaseActivity#onBackPressed()}
     * and, when finishing, from {@link BaseActivity#onPause}
     *
     * Override, and do the desired {@link #setResult} call.
     *
     * CallSuper so we get the debug information
     */
    @CallSuper
    protected void setActivityResult() {
        ComponentName caller = getCallingActivity();
        if (BuildConfig.DEBUG && caller != null) {
            Logger.info(this, "setResult goes to: " + caller.flattenToString());
        }
    }

        /**
         * get a key/value pair either from the savedInstanceState or the extras.
         */
    protected long getLongFromBundles(@NonNull final String key, @Nullable final Bundle savedInstanceState, @Nullable final Bundle extras) {
        long value = 0;
        if (savedInstanceState != null) {
            value = savedInstanceState.getLong(key);
        }
        if ((value == 0) && (extras != null)) {
            value = extras.getLong(key);
        }
        return value;
    }

    /** saving on some typing */
    protected SharedPreferences getPrefs() {
        return getSharedPreferences(BookCatalogueApp.APP_SHARED_PREFERENCES, Context.MODE_PRIVATE);
    }

    /**
     * When resuming, check and reload activity
     */
    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();

        updateLocaleIfChanged();
        updateThemeIfChanged();
        restartActivityIfNeeded();
    }


    /**
     * Reload this activity if locale has changed.
     */
    protected void updateLocaleIfChanged() {
        if (BookCatalogueApp.hasLocalChanged(this.getResources())) {
            mReloadOnResume = true;
        }
    }

    /**
     * Reload this activity if theme has changed.
     */
    private void updateThemeIfChanged() {
        if (BookCatalogueApp.hasThemeChanged()) {
            setTheme(BookCatalogueApp.getThemeResId());
            mReloadOnResume = true;
        }
    }

    /**
     * Restart controlled by setting local mReloadOnResume
     */
    protected void restartActivityIfNeeded() {
        if (mReloadOnResume) {
            if (/* always show debug */ BuildConfig.DEBUG) {
                Logger.info(this, "Restarting");
            }
            finish();
            startActivity(getIntent());
        }
    }

    public interface CanBeDirty {
        boolean isDirty();

        void setDirty(boolean isDirty);
    }
}
