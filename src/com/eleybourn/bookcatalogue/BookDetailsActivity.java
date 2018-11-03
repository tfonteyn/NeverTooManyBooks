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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Tracker;

/**
 * @author Evan Leybourn
 */
public class BookDetailsActivity extends BookBaseActivity {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_VIEW_BOOK;

    public static final String REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION = "FBLP";
    public static final String REQUEST_BKEY_FLATTENED_BOOKLIST = "FBL";

    @Nullable
    private FlattenedBooklist mList = null;
    @Nullable
    private GestureDetector mGestureDetector;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_book_base;
    }

    @Override
    @CallSuper
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);

        mDb = new CatalogueDBAdapter(this)
                .open();

        Bundle extras = getIntent().getExtras();

        long bookId = getLongFromBundles(UniqueId.KEY_ID, savedInstanceState, extras);
        mBook = loadBook(bookId, savedInstanceState == null ? extras : savedInstanceState);

        BookDetailsFragment frag = new BookDetailsFragment();
        frag.setArguments(extras);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, frag)
                .commit();

        initBooklist(extras, savedInstanceState);

        Tracker.exitOnCreate(this);
    }

    /**
     * If we are passed a flat book list, get it and validate it
     */
    private void initBooklist(final @Nullable Bundle extras,
                              final @Nullable Bundle savedInstanceState) {
        if (extras != null) {
            String list = extras.getString(REQUEST_BKEY_FLATTENED_BOOKLIST);
            if (list != null && !list.isEmpty()) {
                mList = new FlattenedBooklist(mDb, list);
                // Check to see it really exists. The underlying table disappeared once in testing
                // which is hard to explain; it theoretically should only happen if the app closes
                // the database or if the activity pauses with 'isFinishing()' returning true.
                if (mList.exists()) {
                    int pos;
                    if (savedInstanceState != null && savedInstanceState.containsKey(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION)) {
                        pos = savedInstanceState.getInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION);
                    } else if (extras.containsKey(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION)) {
                        pos = extras.getInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION);
                    } else {
                        pos = 0;
                    }
                    mList.moveTo(pos);

                    while (mList.getBookId() != mBook.getBookId()) {
                        if (!mList.moveNext()) {
                            break;
                        }
                    }

                    if (mList.getBookId() != mBook.getBookId()) {
                        mList.close();
                        mList = null;
                    } else {
                        initGestureDetector();
                    }

                } else {
                    mList.close();
                    mList = null;
                }
            }
        }
    }

    /**
     * Listener to handle 'fling' events; we could handle others but need to be
     * careful about possible clicks and scrolling.
     */
    private void initGestureDetector() {
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (mList == null) {
                    return false;
                }

                // Make sure we have considerably more X-velocity than Y-velocity;
                // otherwise it might be a scroll.
                if (Math.abs(velocityX / velocityY) > 2) {
                    boolean moved;
                    // Work out which way to move, and do it.
                    if (velocityX > 0) {
                        moved = mList.movePrev();
                    } else {
                        moved = mList.moveNext();
                    }

                    if (moved) {
                        long bookId = mList.getBookId();
                        // only reload if it's a new book
                        if (bookId != mBook.getBookId()) {
                            reload(bookId);
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    /**
     * We override the dispatcher because the ScrollView will consume all events otherwise.
     */
    @Override
    @CallSuper
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mGestureDetector != null && mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        super.dispatchTouchEvent(event);
        // Always return true; we want the events.
        return true;
    }

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     *
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereference'd, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mList != null) {
            mList.close();
            if (isFinishing()) {
                mList.deleteData();
            }
        }
        super.onPause();
    }

    /**
     * Set default result
     */
    @Override
    protected void setActivityResult() {
        Intent data = new Intent();
        data.putExtra(UniqueId.KEY_ID, mBook.getBookId());
        setResult(Activity.RESULT_OK, data); /* e63944b6-b63a-42b1-897a-a0e8e0dabf8a */
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(final @NonNull Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);

        outState.putLong(UniqueId.KEY_ID, mBook.getBookId());
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, mBook.getRawData());
        if (mList != null) {
            outState.putInt(REQUEST_BKEY_FLATTENED_BOOKLIST_POSITION, (int) mList.getPosition());
        }

        super.onSaveInstanceState(outState);
        Tracker.exitOnSaveInstanceState(this);
    }
}
