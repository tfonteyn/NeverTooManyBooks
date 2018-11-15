package com.eleybourn.bookcatalogue.baseactivity;

import android.app.Activity;
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
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.EditBookshelfListActivity;
import com.eleybourn.bookcatalogue.FieldVisibilityActivity;
import com.eleybourn.bookcatalogue.Help;
import com.eleybourn.bookcatalogue.PreferencesActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferredStylesActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;

/**
 * Base class for all (most) Activity's
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

    /** universal flag used to indicate something was changed and not saved (yet) */
    private boolean mIsDirty = false;
    /** we're not (or no longer) dirty, but we did potentially make (local/global/preferences) changes */
    private boolean mChangesMadeAndSaved = false;

    public boolean isDirty() {
        return mIsDirty;
    }

    public void setDirty(final boolean isDirty) {
        this.mIsDirty = isDirty;
        // if we *are* dirty, then we certainly made changes.
        if (isDirty) {
            setChangesMade(true);
        }
    }

    public boolean changesMade() {
        return mChangesMadeAndSaved;
    }


    /**
     * If you are not dirty (e.g. nothing needs saving on exit) but have made (saved) changes,
     * set this to 'true'. Always set to 'true' if at any time we have been dirty.
     *
     * TOMF ENHANCE: start using this as a sure way of detecting committed changes in setResult
     */
    public void setChangesMade(final boolean changesMade) {
        this.mChangesMadeAndSaved = changesMade;
    }

    protected int getLayoutId() {
        return 0;
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
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

        initToolbar();
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
                finishIfClean();
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            // these are not errors; but just a way to see if we missed catching them in one Activity or another
            // 2018-11-14: all caught in BooksOnBookshelf, silently ignored in others
            switch (requestCode) {
                case EditBookshelfListActivity.REQUEST_CODE: /* 41e84172-5833-4906-a891-8df302ecc190 */
                    Logger.info(this, "onActivityResult unhandled EditBookshelfListActivity");
                    return;

                case BooklistPreferredStylesActivity.REQUEST_CODE: /* 13854efe-e8fd-447a-a195-47678c0d87e7 */
                    Logger.info(this, "onActivityResult unhandled BooklistPreferredStylesActivity");
                    return;

                case AdminActivity.REQUEST_CODE: /* 7f46620d-7951-4637-8783-b410730cd460 */
                    Logger.info(this, "onActivityResult unhandled AdminActivity");
                    return;
                case FieldVisibilityActivity.REQUEST_CODE: /* 2f885b11-27f2-40d7-8c8b-fcb4d95a4151 */
                    Logger.info(this, "onActivityResult unhandled FieldVisibilityActivity");
                    return;
                case BooklistPreferencesActivity.REQUEST_CODE: /* 9cdb2cbe-1390-4ed8-a491-87b3b1a1edb9 */
                    Logger.info(this, "onActivityResult unhandled BooklistPreferencesActivity");
                    return;
                case PreferencesActivity.REQUEST_CODE: /* 46f41e7b-f49c-465d-bea0-80ec85330d1c */
                    Logger.info(this, "onActivityResult unhandled PreferencesActivity");
                    return;
            }

            // lowest level of our Activities, see if we missed anything
            Logger.info(this, "onActivityResult: NOT HANDLED: requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * When the user clicks 'back/up':
     */
    @Override
    @CallSuper
    public void onBackPressed() {
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
                            setActivityResult();
                            finish();
                        }
                        /* if they click 'cancel', the dialog just closes without further actions */
                    });
        } else {
            setActivityResult();
            finish();
        }
    }

    /**
     * Always called by {@link BaseActivity#finishIfClean()}
     *
     * If your activity needs to send a specific result, override this call.
     * If your activity does an actual finish() call it *must* take care of the result itself
     * Of course it can still implement and call this method for the sake of uniformity.
     *
     * returns:
     * RESULT_OK if the caller should take some action;
     * RESULT_CANCELED when the caller should do nothing
     */
    public void setActivityResult() {
        setResult(changesMade() ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
    }

//    @Override
//    protected void onPause() {
//        if (isFinishing()) {
//            // keep in mind the base method only does logging. Only overridden methods will actually set the result.
//            setActivityResult();
//        }
//        // call super *after* setting the result, so we can override when needed in a fragment
//        super.onPause();
//    }


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

}
