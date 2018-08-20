package com.eleybourn.bookcatalogue.compat;

import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;

import java.util.Locale;

/**
 * Class introduced to reduce the future pain when we remove sherlock (once we no longer 
 * support Android 2.x), and potentially to make it easier to support two versions.
 * 
 * This activity inherits from SherlockActivity which is just a subclass of
 * the compatibility library Activity which should be fairly compatible with
 * Activity in API 11+.
 * 
 * @author pjw
 */
public class BookCatalogueActivity extends Activity {
    /** Last locale used so; cached so we can check if it has genuinely changed */
    private Locale mLastLocale = BookCatalogueApp.getPreferredLocale();

    /** the order has to match the theme display name as defined in R.array.supported_themes
     * NEWKIND: add new supported themes here
     */
    protected static final int THEME_MATERIAL = 0;
    protected static final int THEME_MATERIAL_LIGHT = 1;

    private int mLastTheme = BookCatalogueApp.getAppPreferences().getInt(BookCataloguePreferences.PREF_APP_THEME, THEME_MATERIAL);

    private boolean mReloadOnResume = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

	    setCustomTheme();
        super.onCreate(savedInstanceState);

        ActionBar bar = getActionBar();
        if (bar != null) {
        	// Show home, use logo (bigger) and show title
        	bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_USE_LOGO | ActionBar.DISPLAY_SHOW_TITLE);
        	// Don't display the 'back' decoration if we are not at the top
    		bar.setDisplayHomeAsUpEnabled(! (this.isTaskRoot() || getIntent().getBooleanExtra("willBeTaskRoot", false) ) );
        }
    }
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		// Default handler for home icon
        case android.R.id.home:
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
        Locale old = mLastLocale;
        Locale curr = BookCatalogueApp.getPreferredLocale();
        if ((curr != null && !curr.equals(old)) || (curr == null && old != null)) {
            mLastLocale = curr;
            BookCatalogueApp.applyPreferredLocaleIfNecessary(this.getResources());
            mReloadOnResume = true;
        }
    }

    /**
     * Reload this activity if theme has changed.
     */
    protected void updateThemeIfChanged() {
        int curr = BookCatalogueApp.getAppPreferences().getInt(BookCataloguePreferences.PREF_APP_THEME, THEME_MATERIAL);
        System.out.println("updateThemeIfChanged current: " + curr + ", last: " + mLastTheme);

        if (mLastTheme != curr) {
            mLastTheme = curr;
            setCustomTheme();
            mReloadOnResume = true;
        }
    }

    /**
     * NEWKIND: add new supported themes here
     */
    private void setCustomTheme() {
        switch (mLastTheme) {
            case THEME_MATERIAL_LIGHT:
                setTheme(R.style.AppThemeLight);
                break;
            default:
                setTheme(R.style.AppThemeDark);
                break;
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
