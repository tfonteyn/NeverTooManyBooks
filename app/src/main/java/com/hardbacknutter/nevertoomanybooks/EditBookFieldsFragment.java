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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.AltIsbnTextWatcher;
import com.hardbacknutter.nevertoomanybooks.widgets.IsbnValidationTextWatcher;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    public static final String TAG = "EditBookFieldsFragment";
    private static final String BKEY_CONTEXT_MENU_OPEN_INDEX = TAG + ":imgIndex";

    /** the covers. */
    private final ImageView[] mCoverView = new ImageView[2];
    /** Handles cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];
    /** Track on which cover view the context menu was used; stored in savedInstanceState. */
    private int mCurrentCoverHandlerIndex = -1;

    /** The views. */
    private View mTitleView;
    private View mAuthorView;
    private View mSeriesView;
    private View mDescriptionView;
    private View mBookshelvesView;

    private EditText mIsbnView;
    private Button mAltIsbnButton;
    private Button mScanIsbnButton;

    private AutoCompleteTextView mGenreView;

    /** manage the validation check next to the ISBN field. */
    private IsbnValidationTextWatcher mIsbnValidationTextWatcher;

    /**
     * Set to {@code true} limits to using ISBN-10/13.
     * Otherwise we also allow UPC/EAN codes.
     * This is used for validation only, and not enforced.
     */
    private boolean mStrictIsbn = true;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_edit_book_fields, container, false);
        mAuthorView = view.findViewById(R.id.author);
        mSeriesView = view.findViewById(R.id.series);
        mTitleView = view.findViewById(R.id.title);
        mDescriptionView = view.findViewById(R.id.description);
        mIsbnView = view.findViewById(R.id.isbn);
        mGenreView = view.findViewById(R.id.genre);
        mBookshelvesView = view.findViewById(R.id.bookshelves);

        mAltIsbnButton = view.findViewById(R.id.btn_altIsbn);
        mScanIsbnButton = view.findViewById(R.id.btn_scan);

        mCoverView[0] = view.findViewById(R.id.coverImage0);
        mCoverView[1] = view.findViewById(R.id.coverImage1);
        if (!App.isUsed(UniqueId.BKEY_THUMBNAIL)) {
            mCoverView[0].setVisibility(View.GONE);
            mCoverView[1].setVisibility(View.GONE);
        }
        return view;
    }

    @Override
    protected void initFields() {
        super.initFields();
        final Fields fields = getFields();

        //noinspection ConstantConditions
        final boolean showAuthSeriesOnTabs = EditBookFragment.showAuthSeriesOnTabs(getContext());
        if (!showAuthSeriesOnTabs) {
            // The button to bring up the fragment to edit Authors.
            // Not shown if the user preferences are set to use an extra tab for this.
            // defined, but populated/stored manually
            fields.define(mAuthorView, DBDefinitions.KEY_FK_AUTHOR)
                  .setRelatedFields(R.id.lbl_author);
            mAuthorView.setOnClickListener(v -> showEditListFragment(new EditBookAuthorsFragment(),
                                                                     EditBookAuthorsFragment.TAG));

            // The button to bring up the fragment to edit Series.
            // Not shown if the user preferences are set to use an extra tab for this.
            // defined, but populated/stored manually
            fields.define(mSeriesView, DBDefinitions.KEY_SERIES_TITLE)
                  .setRelatedFields(R.id.lbl_series);
            mSeriesView.setOnClickListener(v -> showEditListFragment(new EditBookSeriesFragment(),
                                                                     EditBookSeriesFragment.TAG));
        }

        fields.addString(mTitleView, DBDefinitions.KEY_TITLE);

        fields.addString(mDescriptionView, DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFields(R.id.lbl_description);

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        fields.addString(mIsbnView, DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);
        // but we still support visual aids for ISBN and other codes.
        mIsbnValidationTextWatcher = new IsbnValidationTextWatcher(mIsbnView, true);
        mIsbnView.addTextChangedListener(mIsbnValidationTextWatcher);
        mIsbnView.addTextChangedListener(new AltIsbnTextWatcher(mIsbnView, mAltIsbnButton));
        mScanIsbnButton.setOnClickListener(v -> {
            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
            mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
        });

        fields.addString(mGenreView, DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre)
              .setAutocomplete(mGenreView, mBookModel.getGenres());

        // Personal fields

        // The button to bring up the dialog to edit Bookshelves.
        // Storing the list back into the book is handled by onCheckListEditorSave
        // defined, but populated/stored manually
        fields.define(mBookshelvesView, DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves)
              .addCheckListPicker(getChildFragmentManager(), mBookshelvesView,
                                  R.string.lbl_bookshelves_long,
                                  () -> mBookModel.getEditableBookshelvesList());
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentCoverHandlerIndex = savedInstanceState
                    .getInt(BKEY_CONTEXT_MENU_OPEN_INDEX, -1);
        }
        //noinspection ConstantConditions
        mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void onLoadFields(@NonNull final Book book) {
        super.onLoadFields(book);

        if (App.isUsed(UniqueId.BKEY_THUMBNAIL)) {
            // Hook up the indexed cover image.
            mCoverHandler[0] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 0,
                                                mCoverView[0], ImageUtils.SCALE_MEDIUM);

            mCoverHandler[1] = new CoverHandler(this, mProgressBar,
                                                book, mIsbnView, 1,
                                                mCoverView[1], ImageUtils.SCALE_MEDIUM);
        }
        //noinspection ConstantConditions
        final boolean showAuthSeriesOnTabs = EditBookFragment.showAuthSeriesOnTabs(getContext());

        if (!showAuthSeriesOnTabs) {
            populateAuthorListField(book);
            populateSeriesListField(book);
        }

        populateBookshelvesField(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        getFields().resetVisibility(getView(), false, false);

        if (showAuthSeriesOnTabs) {
            getFields().getField(mAuthorView).setVisibility(getView(), View.GONE);
            getFields().getField(mSeriesView).setVisibility(getView(), View.GONE);
        }
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(final int cIdx) {
        mCurrentCoverHandlerIndex = cIdx;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_STRICT_ISBN, 0, R.string.menu_strict_isbn)
            .setCheckable(true)
            .setChecked(mStrictIsbn)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_STRICT_ISBN: {
                mStrictIsbn = !item.isChecked();
                item.setChecked(mStrictIsbn);
                mIsbnValidationTextWatcher.setStrictIsbn(mStrictIsbn);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    if (BuildConfig.DEBUG) {
                        //noinspection ConstantConditions
                        mScannerModel.fakeBarcodeScan(getContext(), data);
                    }

                    //noinspection ConstantConditions
                    final String barCode =
                            mScannerModel.getScanner().getBarcode(getContext(), data);
                    if (barCode != null) {
                        mBookModel.getBook().putString(DBDefinitions.KEY_ISBN, barCode);
                        return;
                    }
                }
                return;
            }

            default: {
                // handle any cover image request codes
                if (mCurrentCoverHandlerIndex >= -1) {
                    final boolean handled = mCoverHandler[mCurrentCoverHandlerIndex]
                            .onActivityResult(requestCode, resultCode, data);
                    mCurrentCoverHandlerIndex = -1;
                    if (handled) {
                        break;
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BKEY_CONTEXT_MENU_OPEN_INDEX, mCurrentCoverHandlerIndex);
    }

    /**
     * Show the given fragment to edit the list of authors/series.
     *
     * @param frag the fragment to show
     * @param tag  the tag to use for the fragment
     */
    private void showEditListFragment(@NonNull final Fragment frag,
                                      @NonNull final String tag) {
        // The original intent was to simply add the new fragment on the same level
        // as the current one; using getParentFragment().getChildFragmentManager()
        // but we got: IllegalStateException: ViewPager2 does not support direct child views
        // So... we use the top-level fragment manager,
        // and have EditBookFragment#prepareSave explicitly check.

        //noinspection ConstantConditions
        getActivity().getSupportFragmentManager()
                     .beginTransaction()
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .replace(R.id.main_fragment, frag, tag)
                     .addToBackStack(tag)
                     .commit();
    }

    /**
     * Handle the Bookshelf default.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    protected void onAddFromNewData(@NonNull final Book book,
                                    @Nullable final Bundle args) {
        super.onAddFromNewData(book, args);

        // If the new book is not on any Bookshelf, add the current bookshelf by default
        final ArrayList<Bookshelf> list =
                book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);
        if (list.isEmpty()) {
            //noinspection ConstantConditions
            final Bookshelf bookshelf = mBookModel.getBookshelf(getContext());
            list.add(bookshelf);
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, list);
        }
    }

    private void populateAuthorListField(@NonNull final Book book) {
        //noinspection ConstantConditions
        @NonNull
        final Context context = getContext();
        final Locale locale = LocaleUtils.getUserLocale(context);

        final ArrayList<Author> list = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!list.isEmpty() && ItemWithFixableId.pruneList(list, context, mBookModel.getDb(),
                                                           locale, false)) {
            mBookModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, list);
        }

        String value = book.getAuthorTextShort(context);
        if (value.isEmpty() && book.contains(UniqueId.BKEY_SEARCH_AUTHOR)) {
            // allow this fallback. It's used after a search that did not return results,
            // in which case it contains whatever the user typed.
            value = book.getString(UniqueId.BKEY_SEARCH_AUTHOR);
        }

        getFields().getField(mAuthorView).getAccessor().setValue(value);
    }

    private void populateSeriesListField(@NonNull final Book book) {
        //noinspection ConstantConditions
        @NonNull
        Context context = getContext();

        final ArrayList<Series> list = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (!list.isEmpty() && ItemWithFixableId.pruneList(list, context, mBookModel.getDb(),
                                                           book.getLocale(context), false)) {
            mBookModel.setDirty(true);
            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, list);
        }

        String value;
        if (list.isEmpty()) {
            value = "";
        } else {
            value = list.get(0).getLabel(context);
            if (list.size() > 1) {
                value += ' ' + getString(R.string.and_others);
            }
        }

        getFields().getField(mSeriesView).getAccessor().setValue(value);
    }

    private void populateBookshelvesField(final Book book) {

        final ArrayList<Bookshelf> list =
                book.getParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY);

        //noinspection ConstantConditions
        final String value = Csv.join(", ", list, bookshelf -> bookshelf.getLabel(getContext()));
        getFields().getField(mBookshelvesView).getAccessor().setValue(value);
    }
}
