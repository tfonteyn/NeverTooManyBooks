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
import android.support.v7.app.ActionBar;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.baseactivity.HasBook;
import com.eleybourn.bookcatalogue.booklist.FlattenedBooklist;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.entities.Book;

/**
 * @author Evan Leybourn
 */
public class BookDetailsActivity extends BaseActivity
        implements HasBook {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_VIEW_BOOK;

    public static final String REQUEST_KEY_FLATTENED_BOOKLIST_POSITION = "FlattenedBooklistPosition";
    public static final String REQUEST_KEY_FLATTENED_BOOKLIST = "FlattenedBooklist";

    private final CatalogueDBAdapter mDb = new CatalogueDBAdapter(this);
    @Nullable
    private FlattenedBooklist mList = null;
    @Nullable
    private GestureDetector mGestureDetector;

    private long mBookId;
    private Book mBook;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_book_base;
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        Tracker.enterOnCreate(this);
        super.onCreate(savedInstanceState);
        mDb.open();

        Bundle extras = getIntent().getExtras();

        mBookId = getId(savedInstanceState, extras);
        mBook = getBook(mBookId, savedInstanceState == null ? extras : savedInstanceState);

        BookDetailsFragment details = new BookDetailsFragment();
        details.setArguments(extras);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, details)
                .commit();

        initBooklist(extras, savedInstanceState);

        // Must come after book data and list retrieved.
        initActivityTitle();

        Tracker.exitOnCreate(this);
    }

    @NonNull
    @Override
    public Book getBook() {
        return mBook;
    }


    @NonNull
    private Book getBook(final long bookId, @Nullable final Bundle bundle) {
        if (bundle != null && bundle.containsKey(UniqueId.BKEY_BOOK_DATA)) {
            // If we have saved book data, use it
            return new Book(bookId, bundle.getBundle(UniqueId.BKEY_BOOK_DATA));
        } else {
            // Just load based on rowId
            return new Book(bookId);
        }
    }


    /**
     * If we are passed a flat book list, get it and validate it
     */
    private void initBooklist(@Nullable final Bundle extras,
                              @Nullable final Bundle savedInstanceState) {
        if (extras != null) {
            String list = extras.getString(REQUEST_KEY_FLATTENED_BOOKLIST);
            if (list != null && !list.isEmpty()) {
                mList = new FlattenedBooklist(mDb, list);
                // Check to see it really exists. The underlying table disappeared once in testing
                // which is hard to explain; it theoretically should only happen if the app closes
                // the database or if the activity pauses with 'isFinishing()' returning true.
                if (mList.exists()) {
                    int pos;
                    if (savedInstanceState != null && savedInstanceState.containsKey(REQUEST_KEY_FLATTENED_BOOKLIST_POSITION)) {
                        pos = savedInstanceState.getInt(REQUEST_KEY_FLATTENED_BOOKLIST_POSITION);
                    } else if (extras.containsKey(REQUEST_KEY_FLATTENED_BOOKLIST_POSITION)) {
                        pos = extras.getInt(REQUEST_KEY_FLATTENED_BOOKLIST_POSITION);
                    } else {
                        pos = 0;
                    }
                    mList.moveTo(pos);
                    while (mList.getBookId() != mBookId) {
                        if (!mList.moveNext()) {
                            break;
                        }
                    }
                    if (mList.getBookId() != mBookId) {
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
                        if (mBookId != bookId) {
                            mBookId = bookId;
                            mBook = new Book(bookId);
//                            Fragment frag = getSupportFragmentManager().findFragmentById(R.id.fragment);
//                            if (frag instanceof DataEditor) {
//                                ((DataEditor) frag).reloadData(mBook);
//                            }
                            initActivityTitle();
                        }
                    }
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    private void initActivityTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (mList != null) {
                // display a book
                actionBar.setTitle(R.string.book_details);
                actionBar.setSubtitle(null);
            }
        }
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

    @Override
    @CallSuper
    protected void onDestroy() {
        Tracker.enterOnDestroy(this);
        super.onDestroy();
        mDb.close();
        Tracker.exitOnDestroy(this);
    }

    /**
     * Close the list object (frees statements) and if we are finishing, delete the temp table.
     *
     * This is an ESSENTIAL step; for some reason, in Android 2.1 if these statements are not
     * cleaned up, then the underlying SQLiteDatabase gets double-dereference'd, resulting in
     * the database being closed by the deeply dodgy auto-close code in Android.
     *
     * Also make sure to set our result when really finishing
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

        // make sure to set our result when really finishing
        if (isFinishing()) {
            Intent intent = new Intent();
            intent.putExtra(UniqueId.KEY_ID, mBook.getBookId());
            setResult(Activity.RESULT_OK, intent);
        }

        super.onPause();
    }

    @Override
    @CallSuper
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        Tracker.enterOnSaveInstanceState(this);

        outState.putLong(UniqueId.KEY_ID, mBookId);
        outState.putBundle(UniqueId.BKEY_BOOK_DATA, mBook.getRawData());
        if (mList != null) {
            outState.putInt(REQUEST_KEY_FLATTENED_BOOKLIST_POSITION, (int) mList.getPosition());
        }

        super.onSaveInstanceState(outState);
        Tracker.exitOnSaveInstanceState(this);
    }
}
