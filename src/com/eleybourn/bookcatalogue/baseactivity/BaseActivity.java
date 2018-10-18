package com.eleybourn.bookcatalogue.baseactivity;

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
import com.eleybourn.bookcatalogue.AdministrationFunctions;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
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
import com.eleybourn.bookcatalogue.searches.SearchCatalogue;

/**
 * Base class for all (most) Activity's
 *
 * ENHANCE: handle the home/up button better
 * right now, the only activity with a DrawerLayout == {@link BooksOnBookshelf}
 * -> click 'home' and {@link #onOptionsItemSelected} will open the drawer.
 *
 * Activities started with 'startActivityForResult' override {@link #onOptionsItemSelected}
 * and handle the 'home' button to provide their results.
 *
 * Lastly, all other activities are handled here with a plain 'finish'
 * in {@link #onOptionsItemSelected}
 *
 * @author pjw
 */
abstract public class BaseActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, CanBeDirty {

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
                intent = new Intent(this, SearchCatalogue.class);
                startActivityForResult(intent, SearchCatalogue.REQUEST_CODE);
                return true;
            case R.id.nav_manage_bookshelves:
                intent = new Intent(this, EditBookshelvesActivity.class);
                startActivityForResult(intent, EditBookshelvesActivity.REQUEST_CODE);
                break;
            case R.id.nav_booklist_prefs:
                intent = new Intent(this, BooklistPreferencesActivity.class);
                startActivityForResult(intent, BooklistPreferencesActivity.REQUEST_CODE);
                return true;
            case R.id.nav_other_prefs:
                intent = new Intent(this, PreferencesActivity.class);
                startActivityForResult(intent, PreferencesActivity.REQUEST_CODE);
                return true;
            case R.id.nav_admin:
                intent = new Intent(this, AdministrationFunctions.class);
                startActivityForResult(intent, AdministrationFunctions.REQUEST_CODE);
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
     * This will be called when a menu item is selected. A large switch
     * statement to call the appropriate functions (or other activities)
     *
     * @param item The item selected
     *
     * @return <tt>true</tt> if handled
     */
    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            // Default handler for home icon
            case android.R.id.home:
                if (mDrawerLayout != null) {
                    mDrawerLayout.openDrawer(GravityCompat.START);
                    return true;
                }

                if (BuildConfig.DEBUG) {
                    Logger.info("BaseActivity.onOptionsItemSelected with android.R.id.home");
                }
                // for all activities that were opened with startActivity()
                // where 'home' is treated as 'up', pretend/mimic
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * When the user clicks 'back/up', prepare our result.
     */
    @Override
    @CallSuper
    public void onBackPressed() {
        if (BuildConfig.DEBUG) {
            Logger.info("BaseActivity.onBackPressed with dirty=" + isDirty());
        }
        // Check if edits need saving, and finish the activity if not
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this,
                    new Runnable() {
                        @Override
                        public void run() {
                            setResultAndFinish();
                        }
                    });
        } else {
            setResultAndFinish();
        }
    }

    /**
     * override if you want more/different results.
     */
    protected void setResultAndFinish() {
        if (BuildConfig.DEBUG) {
            Logger.info("BaseActivity.setResultAndFinish with dirty=" + isDirty());
        }
        if (isDirty()) {
            setResult(RESULT_OK);
        }
        finish();
    }

    /**
     * get the {@link UniqueId#KEY_ID} either from the savedInstanceState or the extras.
     */
    protected long getId(final @Nullable Bundle savedInstanceState, final @Nullable Bundle extras) {
        long id = 0;
        if (savedInstanceState != null) {
            id = savedInstanceState.getLong(UniqueId.KEY_ID);
        }
        if ((id == 0) && (extras != null)) {
            id = extras.getLong(UniqueId.KEY_ID);
        }
        return id;
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
        updateLocaleIfChanged();
        updateThemeIfChanged();
        restartActivityIfNeeded();

        super.onResume();
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
            if (BuildConfig.DEBUG) {
                Logger.info("Restarting " + this.getClass().getCanonicalName());
            }
            finish();
            startActivity(getIntent());
        }
    }
}
