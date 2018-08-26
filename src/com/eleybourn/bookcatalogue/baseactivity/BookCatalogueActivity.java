package com.eleybourn.bookcatalogue.baseactivity;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookCataloguePreferences;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;

import java.util.Locale;

/**
 * Base class for all (most) Activity's
 *
 * @author pjw
 */
abstract public class BookCatalogueActivity extends AppCompatActivity {
    /**
     * NEWKIND: add new supported themes here and in R.array.supported_themes,
     * the string-array order must match the THEMES order
     * The preferences choice will be build according to the string-array list/order.
     */
    protected static final int DEFAULT_THEME = 0;

    protected static final int[] THEMES = {
            R.style.ThemeDark,
            R.style.ThemeLight
    };

    /** Last locale used so; cached so we can check if it has genuinely changed */
    private Locale mLastLocale = BookCatalogueApp.getPreferredLocale();
    /** same for Theme */
    private int mLastTheme = BookCatalogueApp.getPrefs().getInt(BookCataloguePreferences.PREF_APP_THEME, DEFAULT_THEME);

    /** when a locale or theme is changed, a restart of the activity is needed */
    private boolean mReloadOnResume = false;

    @Override
	protected void onCreate(Bundle savedInstanceState) {

        setTheme(THEMES[mLastTheme]);
        super.onCreate(savedInstanceState);

        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setIcon(BookCatalogueApp.getAppContext().getApplicationInfo().icon);
            //bar.setDisplayUseLogoEnabled(true);

            bar.setDisplayShowTitleEnabled(true);
            bar.setDisplayShowHomeEnabled(true);
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
        Locale current = BookCatalogueApp.getPreferredLocale();
        if ((current != null && !current.equals(mLastLocale)) || (current == null && mLastLocale != null)) {
            mLastLocale = current;
            BookCatalogueApp.applyPreferredLocaleIfNecessary(this.getResources());
            mReloadOnResume = true;
        }
    }

    /**
     * Reload this activity if theme has changed.
     */
    protected void updateThemeIfChanged() {
        int current = BookCatalogueApp.getPrefs().getInt(BookCataloguePreferences.PREF_APP_THEME, DEFAULT_THEME);
        if (mLastTheme != current) {
            mLastTheme = current;
            setTheme(THEMES[mLastTheme]);
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
