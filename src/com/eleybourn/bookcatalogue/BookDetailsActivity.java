/*
 * @copyright 2010 Evan Leybourn
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Book;

public class BookDetailsActivity extends BaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_VIEW_BOOK;
    public static final int RESULT_CHANGES_MADE = UniqueId.ACTIVITY_RESULT_CHANGES_MADE_VIEW_BOOK;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();

        BookFragment frag = new BookFragment();
        frag.setArguments(extras);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.main_fragment, frag, BookFragment.TAG)
                .commit();
    }

    private Book getBook() {
        BookFragment frag = (BookFragment) getSupportFragmentManager().findFragmentByTag(BookFragment.TAG);
        return frag.getBookManager().getBook();
    }


    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

        switch (requestCode) {
            case EditBookActivity.REQUEST_CODE: {
                if (resultCode == EditBookFragment.RESULT_CHANGES_MADE) {
                    setChangesMade(true);
                }
                return;
            }
        }

        /*
         * Dispatch incoming result to the correct fragment.
         */
        if (DEBUG_SWITCHES.ON_ACTIVITY_RESULT && BuildConfig.DEBUG) {
            Logger.info(this, "onActivityResult: forwarding to fragment - requestCode=" + requestCode + ", resultCode=" + resultCode);
        }

        Fragment frag = getSupportFragmentManager().findFragmentByTag(BookFragment.TAG);
        frag.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Set default result
     */
    @Override
    public void setActivityResult() {
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, getBook().getBookId());
        setResult(changesMade() ? RESULT_CHANGES_MADE : Activity.RESULT_CANCELED, data); /* e63944b6-b63a-42b1-897a-a0e8e0dabf8a */
    }
}
