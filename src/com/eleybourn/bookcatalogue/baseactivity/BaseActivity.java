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

            bar.setHomeButtonEnabled(true);
            bar.setDisplayHomeAsUpEnabled(true);

            if (isTaskRoot()) {
                bar.setDisplayShowHomeEnabled(true);
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

        switch (menuItem.getItemId()) {
            case R.id.nav_search:
                startActivity(new Intent(this, SearchCatalogue.class));
                return true;
            case R.id.nav_manage_bookshelves:
                startActivity(new Intent(this, EditBookshelfListActivity.class));
                break;
            case R.id.nav_booklist_prefs:
                startActivity(new Intent(this, BooklistPreferencesActivity.class));
                return true;
            case R.id.nav_other_prefs:
                startActivity(new Intent(this, PreferencesActivity.class));
                return true;
            case R.id.nav_admin:
                startActivity(new Intent(this, AdministrationFunctions.class));
                return true;
            case R.id.nav_about:
                startActivity(new Intent(this, About.class));
                return true;
            case R.id.nav_help:
                startActivity(new Intent(this, Help.class));
                return true;
            case R.id.nav_donate:
                startActivity(new Intent(this, Donate.class));
                break;
        }

        return false;
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
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }

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
