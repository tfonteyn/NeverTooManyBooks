package com.eleybourn.bookcatalogue.baseactivity;

import android.content.Intent;
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
import com.eleybourn.bookcatalogue.BooksOnBookshelf;
import com.eleybourn.bookcatalogue.Donate;
import com.eleybourn.bookcatalogue.AdministrationFunctions;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.EditBookshelfListActivity;
import com.eleybourn.bookcatalogue.Help;
import com.eleybourn.bookcatalogue.PreferencesActivity;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistPreferencesActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
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

    protected boolean isDirty() {
        return mIsDirty;
    }

    protected void setDirty(final boolean isDirty) {
        this.mIsDirty = isDirty;
    }

    protected int getLayoutId(){
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
        setDrawerLayout((DrawerLayout)findViewById(R.id.drawer_layout));

        setNavigationView((NavigationView)findViewById(R.id.nav_view));

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

    private void setNavigationView(@Nullable final NavigationView navigationView) {
        mNavigationView = navigationView;
        if (mNavigationView != null) {
            mNavigationView.setNavigationItemSelectedListener(this);
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public NavigationView getNavigationView() {
        return mNavigationView;
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
                startActivity(intent);
                return true;
            case R.id.nav_manage_bookshelves:
                intent =new Intent(this, EditBookshelfListActivity.class);
                startActivityForResult(intent, EditBookshelfListActivity.REQUEST_CODE);
                break;
            case R.id.nav_booklist_prefs:
                intent = new Intent(this, BooklistPreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.nav_other_prefs:
                intent = new Intent(this, PreferencesActivity.class);
                startActivityForResult(intent, PreferencesActivity.REQUEST_CODE);
                return true;
            case R.id.nav_admin:
                intent =new Intent(this, AdministrationFunctions.class);
                startActivityForResult(intent, AdministrationFunctions.REQUEST_CODE);
                return true;
            case R.id.nav_about:
                intent =new Intent(this, About.class);
                startActivity(intent);
                return true;
            case R.id.nav_help:
                intent =new Intent(this, Help.class);
                startActivity(intent);
                return true;
            case R.id.nav_donate:
                intent =new Intent(this, Donate.class);
                startActivity(intent);
                break;
        }

        return false;
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
           case AdministrationFunctions.REQUEST_CODE:
           case PreferencesActivity.REQUEST_CODE:
           case EditBookshelfListActivity.REQUEST_CODE:
                if (resultCode == RESULT_OK) {
                    // code is here for reference only
                    // for now, nothing to do as these are handled on a higher level
                }
                break;

        }
    }

    /**
     * @param drawerLayout  your custom one
     */
    private void setDrawerLayout(@Nullable final DrawerLayout drawerLayout) {
        this.mDrawerLayout = drawerLayout;
    }

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

                // for all activities that were opened with startActivity()
                // where 'home' is treated as 'up', simply finish TOMF
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
