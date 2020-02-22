/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.LendBookDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.RequestAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.SendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;

/**
 * Class for representing read-only book details.
 * <p>
 * Keep in mind the fragment can be re-used.
 * Do NOT assume fields are empty by default when populating them manually.
 * <p>
 * Initializing the Fields is done in the ViewModel.
 */
public class BookDetailsFragment
        extends BookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    public static final String TAG = "BookDetailsFragment";

    /** the covers. */
    private final ImageView[] mCoverView = new ImageView[2];
    /** Handles cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    private TextView mIsbnView;

    /** Label for anthologies. */
    private TextView mIsAnthologyLabelView;
    /** Checkbox for anthologies. Set manually. */
    private CompoundButton mIsAnthologyCbx;
    /** Switch the user can flick to display/hide the TOC (if present). */
    private CompoundButton mTocButton;
    /** We display/hide the TOC header label as needed. */
    private View mTocLabelView;
    /** The TOC list. */
    private LinearLayout mTocView;
    /** The loanee banner. */
    private TextView mLoanedToView;

    /** Listen for changes. */
    private final BookChangedListener mBookChangedListener = (bookId, fieldsChanged, data) -> {
        if (data != null) {
            if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
                populateLoanedToField(data.getString(DBDefinitions.KEY_LOANEE));
            } else {
                // we don't expect/implement any others.
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "bookId=" + bookId + "|fieldsChanged=" + fieldsChanged);
                }
            }
        }
    };

    /** The scroll view encompassing the entire fragment page. */
    private NestedScrollView mTopScrollView;

    /** Registered with the Activity to deliver us gestures. */
    private View.OnTouchListener mOnTouchListener;

    /** Handle next/previous paging in the flattened booklist; called by mOnTouchListener. */
    private GestureDetector mGestureDetector;

    private BookDetailsFragmentViewModel mFragmentVM;

    @Override
    Fields getFields() {
        return mFragmentVM.getFields();
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (LendBookDialogFragment.TAG.equals(childFragment.getTag())) {
            ((LendBookDialogFragment) childFragment).setListener(mBookChangedListener);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        //noinspection ConstantConditions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        final View view = inflater.inflate(R.layout.fragment_book_details, container, false);

        mTopScrollView = view.findViewById(R.id.topScroller);

        mIsbnView = view.findViewById(R.id.isbn);

        // Anthology/TOC fields
        mIsAnthologyLabelView = view.findViewById(R.id.lbl_anthology);
        mIsAnthologyCbx = view.findViewById(R.id.cbx_anthology);
        mTocLabelView = view.findViewById(R.id.lbl_toc);
        mTocView = view.findViewById(R.id.toc);
        mTocButton = view.findViewById(R.id.toc_button);
        if (!App.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            mIsAnthologyLabelView.setVisibility(View.GONE);
            mIsAnthologyCbx.setVisibility(View.GONE);
            mTocLabelView.setVisibility(View.GONE);
            mTocView.setVisibility(View.GONE);
            mTocButton.setVisibility(View.GONE);
        }

        // Covers
        mCoverView[0] = view.findViewById(R.id.coverImage0);
        mCoverView[1] = view.findViewById(R.id.coverImage1);
        if (!App.isUsed(prefs, UniqueId.BKEY_THUMBNAIL)) {
            mCoverView[0].setVisibility(View.GONE);
            mCoverView[1].setVisibility(View.GONE);
        }

        mLoanedToView = view.findViewById(R.id.loaned_to);
        if (!App.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            mLoanedToView.setVisibility(View.GONE);
        }

        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mFragmentVM = new ViewModelProvider(this)
                .get(BookDetailsFragmentViewModel.class);
        //noinspection ConstantConditions
        mFragmentVM.init(getContext(), getArguments(), mBookViewModel.getBook());
        mFragmentVM.getUserMessage().observe(getViewLifecycleOwner(), this::showUserMessage);
        mFragmentVM.getNeedsGoodreads().observe(getViewLifecycleOwner(), needs -> {
            if (needs != null && needs) {
                final Context context = getContext();
                RequestAuthTask.prompt(context, mFragmentVM.getGoodreadsTaskListener(context));
            }
        });

        //noinspection ConstantConditions
        final FloatingActionButton fabButton = getActivity().findViewById(R.id.fab);
        fabButton.setImageResource(R.drawable.ic_edit);
        fabButton.setVisibility(View.VISIBLE);
        fabButton.setOnClickListener(v -> startEditBook());

        // ENHANCE: should be replaced by a ViewPager2/FragmentStateAdapter
        mGestureDetector = new GestureDetector(getContext(), new FlingHandler());
        mOnTouchListener = (v, event) -> mGestureDetector.onTouchEvent(event);

        // show/hide the TOC as the user flips the switch.
        mTocButton.setOnClickListener(v -> {
            // note that the button is explicitly (re)set.
            // If user clicks to fast it seems to get out of sync.
            if (mTocView.getVisibility() == View.VISIBLE) {
                // force a scroll; a manual scroll is no longer possible after the TOC closes.
                mTopScrollView.fullScroll(View.FOCUS_UP);
                mTocView.setVisibility(View.GONE);
                mTocButton.setChecked(false);

            } else {
                mTocView.setVisibility(View.VISIBLE);
                mTocButton.setChecked(true);
            }
        });

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.display(getContext(), R.string.tip_view_only_help, null);
        }
    }

    @CallSuper
    @Override
    public void onResume() {
        // The parent will kick of the process that triggers {@link #onPopulateViews}.
        super.onResume();
        // No ViewPager2 involved, override the parent (see google bug comment there)
        setHasOptionsMenu(true);

        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).registerOnTouchListener(mOnTouchListener);
    }

    @Override
    @CallSuper
    public void onPause() {
        //noinspection ConstantConditions
        ((BookDetailsActivity) getActivity()).unregisterOnTouchListener(mOnTouchListener);

        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case UniqueId.REQ_UPDATE_FIELDS_FROM_INTERNET:
            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookViewModel.putResultData(data);
                    }
                    // onResume will display the changed book.
                    mBookViewModel.reload();
                }
                break;

            case UniqueId.REQ_BOOK_DUPLICATE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        // pass the data up
                        mBookViewModel.putResultData(data);

                        long id = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                        if (id != 0) {
                            mBookViewModel.loadBook(id);
                        }
                    }
                    // onResume will display the new book
                    mBookViewModel.reload();
                    //FIXME: swiping through the flattened booklist will not see
                    // the duplicated book until we go back to BoB.
                    // Easiest solution would be to remove the dup. option from this screen...
                }
                break;

            default: {
                int cIdx = mFragmentVM.getCurrentCoverHandlerIndex();
                // handle any cover image request codes
                if (cIdx >= 0) {
                    boolean handled = mCoverHandler[cIdx]
                            .onActivityResult(requestCode, resultCode, data);
                    mFragmentVM.setCurrentCoverHandlerIndex(-1);
                    if (handled) {
                        break;
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(final int cIdx) {
        mFragmentVM.setCurrentCoverHandlerIndex(cIdx);
    }

    /**
     * <p>
     * At this point we're told to load our local (to the fragment) fields from the Book.
     * </p>
     * <br>{@inheritDoc}
     *
     * @param book to load
     */
    @Override
    protected void onPopulateViews(@NonNull final Book book) {
        // do all the defined Field's
        super.onPopulateViews(book);

        //noinspection ConstantConditions
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // handle special fields
        if (App.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
            populateLoanedToField(mBookViewModel.getLoanee());
        }

        if (App.isUsed(prefs, DBDefinitions.KEY_TOC_BITMASK)) {
            populateToc(book);
        }

        if (App.isUsed(prefs, UniqueId.BKEY_THUMBNAIL)) {
            // Hook up the indexed cover image.
            mCoverHandler[0] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 0, mCoverView[0],
                                                ImageUtils.SCALE_LARGE);

            mCoverHandler[1] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 1, mCoverView[1],
                                                ImageUtils.SCALE_SMALL);
        }

        // hide unwanted and empty fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), true, false);

        // Hide the Publication section label if none of the publishing fields are shown.
        setSectionLabelVisibility(R.id.lbl_publication_section,
                                  R.id.publisher,
                                  R.id.date_published,
                                  R.id.price_listed,
                                  R.id.first_publication,
                                  R.id.edition);
        // Hide the "Personal notes" label if none of the notes fields are shown.
        setSectionLabelVisibility(R.id.lbl_notes,
                                  R.id.notes,
                                  R.id.cbx_signed,
                                  R.id.date_acquired,
                                  R.id.price_paid,
                                  R.id.read_start,
                                  R.id.read_end,
                                  R.id.location);
    }

    /**
     * Inflates 'Loaned' field showing a person the book loaned to.
     * Allows returning the book via a context menu.
     *
     * <strong>Note:</strong> we pass in the loanee and handle visibility local as this
     * method can be called from anywhere.
     *
     * @param loanee the one who shall not be mentioned.
     */
    private void populateLoanedToField(@Nullable final String loanee) {
        if (loanee != null && !loanee.isEmpty()) {
            mLoanedToView.setText(getString(R.string.lbl_loaned_to_name, loanee));
            mLoanedToView.setVisibility(View.VISIBLE);
            mLoanedToView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                /** TODO: convert to MenuPicker context menu.... if I can be bothered. */
                @Override
                @CallSuper
                public void onCreateContextMenu(@NonNull final ContextMenu menu,
                                                @NonNull final View v,
                                                @NonNull
                                                final ContextMenu.ContextMenuInfo menuInfo) {
                    Resources r = getResources();
                    menu.add(Menu.NONE, R.id.MENU_BOOK_LOAN_DELETE,
                             r.getInteger(R.integer.MENU_ORDER_LENDING),
                             R.string.menu_loan_return_book);
                }
            });
        } else {
            mLoanedToView.setVisibility(View.GONE);
            mLoanedToView.setText("");
        }
    }

    /**
     * Show or hide the Table Of Content section.
     */
    private void populateToc(@NonNull final Book book) {
        final boolean isAnthology = book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                                  Book.TOC_MULTIPLE_WORKS);
        mIsAnthologyLabelView.setVisibility(isAnthology ? View.VISIBLE : View.GONE);
        mIsAnthologyCbx.setVisibility(isAnthology ? View.VISIBLE : View.GONE);
        mIsAnthologyCbx.setChecked(isAnthology);

        // we can get called more than once (when user moves sideways to another book),
        // so clear and hide/disable the view before populating it.
        // Actual visibility is handled after building the list.
        mTocView.removeAllViews();
        mTocView.setVisibility(View.GONE);
        mTocButton.setChecked(false);

        final ArrayList<TocEntry> tocList =
                book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        if (!tocList.isEmpty()) {

            @SuppressWarnings("ConstantConditions")
            @NonNull
            final Context context = getContext();

            for (TocEntry tocEntry : tocList) {
                final View rowView = getLayoutInflater()
                        .inflate(R.layout.row_toc_entry_with_author, mTocView, false);

                final TextView titleView = rowView.findViewById(R.id.title);
                final TextView authorView = rowView.findViewById(R.id.author);
                final TextView firstPubView = rowView.findViewById(R.id.year);
                final CheckBox multipleBooksView = rowView.findViewById(R.id.cbx_multiple_books);

                titleView.setText(tocEntry.getLabel(context));

                if (multipleBooksView != null) {
                    final boolean isSet = tocEntry.getBookCount() > 1;
                    multipleBooksView.setChecked(isSet);
                    multipleBooksView.setVisibility(isSet ? View.VISIBLE : View.GONE);
                }
                if (authorView != null) {
                    authorView.setText(tocEntry.getAuthor().getLabel(context));
                }
                if (firstPubView != null) {
                    final String date = tocEntry.getFirstPublication();
                    // "< 4" covers empty and illegal dates
                    if (date.length() < 4) {
                        firstPubView.setVisibility(View.GONE);
                    } else {
                        firstPubView.setVisibility(View.VISIBLE);
                        // show full date string (if available)
                        firstPubView.setText(context.getString(R.string.brackets, date));
                    }
                }
                mTocView.addView(rowView);
            }

            mTocLabelView.setVisibility(View.VISIBLE);
            mTocButton.setVisibility(View.VISIBLE);

        } else {
            mTocLabelView.setVisibility(View.GONE);
            mTocButton.setVisibility(View.GONE);
        }
    }

    /**
     * If all 'fields' are View.GONE, set 'sectionLabelId' to View.GONE as well.
     * Otherwise, set 'sectionLabelId' to View.VISIBLE.
     *
     * @param sectionLabelId field to set
     * @param fieldIds       to check
     */
    private void setSectionLabelVisibility(@IdRes final int sectionLabelId,
                                           @IdRes final int... fieldIds) {
        View parent = getView();
        //noinspection ConstantConditions
        final View fieldView = parent.findViewById(sectionLabelId);
        if (fieldView != null) {
            for (int fieldId : fieldIds) {
                View view = parent.findViewById(fieldId);
                if (view != null && view.getVisibility() != View.GONE) {
                    // at least one field was visible
                    fieldView.setVisibility(View.VISIBLE);
                    return;
                }
            }
            // all fields were gone.
            fieldView.setVisibility(View.GONE);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.book, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        Book book = mBookViewModel.getBook();

        final boolean isSaved = !book.isNew();
        final boolean isRead = book.getBoolean(DBDefinitions.KEY_READ);
        final boolean isAvailable = mBookViewModel.isAvailable();

        menu.findItem(R.id.MENU_BOOK_READ).setVisible(isSaved && !isRead);
        menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isSaved && isRead);

        //FIXME: swiping through the flattened booklist will not see
        // the duplicated book until we go back to BoB.
        // Easiest solution would be to remove the dup. option from this screen...
        menu.findItem(R.id.MENU_BOOK_DUPLICATE).setVisible(false);

        //noinspection ConstantConditions
        menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
            .setVisible(GoodreadsHandler.isShowSyncMenus(getContext()));

        // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
        final boolean useLending = App.isUsed(DBDefinitions.KEY_LOANEE);
        menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isSaved && isAvailable);
        menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && isSaved && !isAvailable);

        MenuHandler.prepareOptionalMenus(menu, book);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        final Book book = mBookViewModel.getBook();

        switch (item.getItemId()) {
            case R.id.MENU_BOOK_EDIT: {
                startEditBook();
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final String title = book.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors =
                        book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
                //noinspection ConstantConditions
                StandardDialogs.deleteBook(getContext(), title, authors, () -> {
                    mBookViewModel.deleteBook(getContext());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mBookViewModel.getResultData());
                    getActivity().finish();
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Intent dupIntent = new Intent(getContext(), EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(dupIntent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;
            }
            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle 'read' status of the book
                boolean value = mBookViewModel.toggleRead();
                mFragmentVM.getFields()
                           .getField(R.id.cbx_read).getAccessor().setValue(value);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_ADD: {
                //noinspection ConstantConditions
                LendBookDialogFragment.newInstance(getContext(), book)
                                      .show(getChildFragmentManager(), LendBookDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                mBookViewModel.deleteLoan();
                populateLoanedToField(null);
                return true;
            }
            case R.id.MENU_SHARE: {
                //noinspection ConstantConditions
                startActivity(Intent.createChooser(book.getShareBookIntent(getContext()),
                                                   getString(R.string.menu_share_this)));
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.progress_msg_connecting, Snackbar.LENGTH_LONG)
                        .show();
                //noinspection ConstantConditions
                new SendOneBookTask(book.getId(), mFragmentVM
                        .getGoodreadsTaskListener(getContext()))
                        .execute();
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startEditBook() {
        final Intent editIntent = new Intent(getContext(), EditBookActivity.class)
                .putExtra(DBDefinitions.KEY_PK_ID, mBookViewModel.getBook().getId());
        startActivityForResult(editIntent, UniqueId.REQ_BOOK_EDIT);
    }

    @Override
    @CallSuper
    public boolean onContextItemSelected(@NonNull final MenuItem menuItem) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_BOOK_LOAN_DELETE:
                mBookViewModel.deleteLoan();
                populateLoanedToField(null);
                return true;

            default:
                return super.onContextItemSelected(menuItem);
        }
    }

    /**
     * Listener to handle 'fling' events to move to the next/previous book.
     */
    private class FlingHandler
            extends GestureDetector.SimpleOnGestureListener {

        private static final float sensitivity = 50;

        @Override
        public boolean onFling(@NonNull final MotionEvent e1,
                               @NonNull final MotionEvent e2,
                               final float velocityX,
                               final float velocityY) {

            if ((e1.getX() - e2.getX()) > sensitivity) {
                if (mFragmentVM.move(mBookViewModel.getBook(), true)) {
                    populateViews();
                    return true;
                }

            } else if ((e2.getX() - e1.getX()) > sensitivity) {
                if (mFragmentVM.move(mBookViewModel.getBook(), false)) {
                    populateViews();
                    return true;
                }
            }

            return false;
        }

        /**
         * <a href="https://developer.android.com/training/gestures/detector.html#detect-a-subset-of-supported-gestures">
         * detect-a-subset-of-supported-gestures</a>
         * <p>
         * ... implement an onDown() method that returns true. This is because all gestures
         * begin with an onDown() message. If you return false from onDown(),
         * as GestureDetector.SimpleOnGestureListener does by default, the system assumes that
         * you want to ignore the rest of the gesture, and the other methods of
         * GestureDetector.OnGestureListener never get called...
         *
         * @param e The down motion event.
         *
         * @return {@code true}
         */
        @Override
        public boolean onDown(@NonNull final MotionEvent e) {
            return true;
        }
    }
}
