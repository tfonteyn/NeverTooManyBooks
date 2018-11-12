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
import com.eleybourn.bookcatalogue.EditBookshelfListActivity;
import com.eleybourn.bookcatalogue.Help;
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

    /** we're not (or no longer) dirty, but we did potentially make (local/global/preferences) changes */
    private boolean mGlobalChangesMade = false;

    public boolean changesMade() {
        return mGlobalChangesMade;
    }

    //TOMF ENHANCE: start using this as a sure way of detecting committed changes in setResult
    public void setChangesMade(final boolean changesMade) {
        this.mGlobalChangesMade = changesMade;
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
            case R.id.nav_search:
                onSearchRequested();
                return true;
            case R.id.nav_manage_bookshelves:
                intent = new Intent(this, EditBookshelfListActivity.class);
                startActivityForResult(intent, EditBookshelfListActivity.REQUEST_CODE); /* 41e84172-5833-4906-a891-8df302ecc190 */
                break;
            case R.id.nav_booklist_prefs:
                intent = new Intent(this, BooklistPreferencesActivity.class);
                startActivityForResult(intent, BooklistPreferencesActivity.REQUEST_CODE); /* 9cdb2cbe-1390-4ed8-a491-87b3b1a1edb9 */
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
                // otherwise, home is an 'up' event. So pretend the back button was pressed.
                onBackPressed();
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
        if (BuildConfig.DEBUG) {
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
     *  Check if edits need saving, and finish the activity if not
     */
    private void finishIfClean() {
        if (isDirty()) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this,
                    /* run when user clicks 'exit' */
                    new Runnable() {
                        @Override
                        public void run() {
                            // set default result
                            setResult(Activity.RESULT_OK);
                            // but allow overriding
                            setActivityResult();
                            finish();
                        }
                        /* if they click 'cancel', the dialog just closes without further actions */
                    });
        } else {
            // set default result
            setResult(Activity.RESULT_OK);
            // but allow overriding
            setActivityResult();
            finish();
        }
    }

    /**
     * Always called by {@link BaseActivity#finishIfClean()}
     *
     * If your activity needs to send a result, override this call.
     * If your activity does an actual finish() call it *must* take care of the result itself
     * (of course it can still implement and call this method for the sake of uniformity
     */
    public void setActivityResult() {
        // do nothing
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
