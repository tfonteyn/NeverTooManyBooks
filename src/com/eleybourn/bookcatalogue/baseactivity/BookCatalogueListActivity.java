package com.eleybourn.bookcatalogue.baseactivity;

import android.app.ActionBar;
import android.app.ListActivity;
import android.os.Bundle;
import android.view.MenuItem;

import com.eleybourn.bookcatalogue.BookCatalogueApp;

/**
 * It is very tempting to take the code from 'ListActivity' and base this class off of
 * BookCatalogueActivity, but currently there is little value in doing go.
 *
 * FIXME: ListActivity seems to cause several issues when mixing with android support libs; online advice favours "get rid of it" ... TODO....
 *
 * @author pjw
 */
abstract public class BookCatalogueListActivity extends ListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar bar = getActionBar();
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
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
			}
	}
}
