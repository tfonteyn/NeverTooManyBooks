package com.eleybourn.bookcatalogue.baseactivity;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.StartupActivity;

/**
 * Base class for all (most) Activity's
 *
 * @author pjw
 */
abstract public class BookCatalogueActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    /**
     * The side/navigation panel
     */
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;

    /**
     * when a locale or theme is changed, a restart of the activity is needed
     */
    private boolean mReloadOnResume = false;

    protected int getLayoutId(){
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(BookCatalogueApp.getThemeResId());
        super.onCreate(savedInstanceState);

        int layoutId = getLayoutId();
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

            // Only display the 'back' decoration if we are at the top
            boolean isTaskRoot = isTaskRoot() || getIntent().getBooleanExtra(StartupActivity.BKEY_IS_TASK_ROOT, false);
            if (isTaskRoot) {
                bar.setDisplayShowHomeEnabled(true);
                //FIXME: find out why Vector icons don't work.... for now abusing the collapse icon
                bar.setHomeAsUpIndicator(R.drawable.ic_menu_collapse);
            } //else {
            // we get the default 'arrow back'
            //}
        }
    }

    protected NavigationView getNavigationView() {
        return mNavigationView;
    }

    protected void setNavigationView(NavigationView navigationView) {
        this.mNavigationView = navigationView;
        if (mNavigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        return false;
    }

    /**
     * @param drawerLayout  your custom one
     */
    protected void setDrawerLayout(DrawerLayout drawerLayout) {
        this.mDrawerLayout = drawerLayout;
    }

    /**
     *
     * @return  the drawer layout in use
     */
    protected DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            // Default handler for home icon
            case android.R.id.home:
                DrawerLayout drawerLayout = getDrawerLayout();
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
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
    protected void updateThemeIfChanged() {
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
                System.out.println("Restarting " + this.getClass().getSimpleName());
            }
            finish();
            startActivity(getIntent());
        }
    }
}
