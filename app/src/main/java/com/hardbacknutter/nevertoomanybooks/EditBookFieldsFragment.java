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
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.accessors.TextViewAccessor;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.LanguageFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    private static final String TAG = "EditBookFieldsFragment";

    /** Dialog listener (strong reference). */
    private final CheckListDialogFragment.CheckListResultsListener mCheckListResultsListener =
            list -> {
                final int fieldId = mFragmentVM.getCurrentDialogFieldId()[0];
                final Field<List<Entity>, TextView> field = getField(fieldId);
                mBookViewModel.getBook().putParcelableArrayList(field.getKey(), list);
                field.getAccessor().setValue(list);
                field.onChanged(true);
            };

    /** (re)attach the result listener when a fragment gets started. */
    private final FragmentOnAttachListener mFragmentOnAttachListener =
            new FragmentOnAttachListener() {
                @Override
                public void onAttachFragment(@NonNull final FragmentManager fragmentManager,
                                             @NonNull final Fragment fragment) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
                        Log.d(getClass().getName(), "onAttachFragment: " + fragment.getTag());
                    }

                    if (fragment instanceof CheckListDialogFragment) {
                        ((CheckListDialogFragment) fragment).setListener(mCheckListResultsListener);
                    }
                }
            };

    /** manage the validation check next to the ISBN field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    /** Watch and clean the text entered in the ISBN field. */
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;
    /** The level of checking the ISBN code. */
    @ISBN.Validity
    private int mIsbnValidityCheck;
    /** The scanner. Must be in the Activity scope. */
    @Nullable
    private ScannerViewModel mScannerModel;
    /** View Binding. */
    private FragmentEditBookFieldsBinding mVb;

    @NonNull
    @Override
    Fields getFields() {
        return mFragmentVM.getFields(TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getChildFragmentManager().addFragmentOnAttachListener(mFragmentOnAttachListener);

        //noinspection ConstantConditions
        mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookFieldsBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        // setup common stuff and calls onInitFields()
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        if (!DBDefinitions.isUsed(getContext(), DBDefinitions.PREFS_IS_USED_THUMBNAIL)) {
            mVb.coverImage0.setVisibility(View.GONE);
            mVb.coverImage1.setVisibility(View.GONE);
        }

        mBookViewModel.getAuthorList().observe(getViewLifecycleOwner(), authors -> {
            final Field<List<Author>, TextView> field = getField(R.id.author);
            field.getAccessor().setValue(authors);
            field.validate();
        });

        mBookViewModel.getSeriesList().observe(getViewLifecycleOwner(), series -> {
            final Field<List<Series>, TextView> field = getField(R.id.series_title);
            field.getAccessor().setValue(series);
            field.validate();
        });

        mVb.btnScan.setOnClickListener(v -> {
            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
            mScannerModel.scan(this, RequestCode.SCAN_BARCODE);
        });

        mVb.author.setOnClickListener(v -> EditBookAuthorListDialogFragment
                .newInstance()
                // no listener/callback. We share the book view model in the Activity scope
                .show(getChildFragmentManager(), EditBookAuthorListDialogFragment.TAG));

        if (getField(R.id.series_title).isUsed(getContext())) {
            mVb.seriesTitle.setOnClickListener(v -> EditBookSeriesListDialogFragment
                    .newInstance()
                    // no listener/callback. We share the book view model in the Activity scope
                    .show(getChildFragmentManager(), EditBookSeriesListDialogFragment.TAG));
        }

        // Bookshelves editor (dialog)
        if (getField(R.id.bookshelves).isUsed(getContext())) {
            mVb.bookshelves.setOnClickListener(v -> {
                mFragmentVM.setCurrentDialogFieldId(R.id.bookshelves);
                CheckListDialogFragment
                        .newInstance(
                                getString(R.string.lbl_bookshelves_long),
                                new ArrayList<>(mFragmentVM.getAllBookshelves()),
                                new ArrayList<>(mBookViewModel.getBook().getParcelableArrayList(
                                        Book.BKEY_BOOKSHELF_ARRAY)))
                        // child fragment. We use a listener, see onAttachFragment
                        .show(getChildFragmentManager(), CheckListDialogFragment.TAG);
            });
        }

        mIsbnValidityCheck = ISBN.getEditValidityLevel(getContext());

        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
        mIsbnValidationTextWatcher = new ISBN.ValidationTextWatcher(
                mVb.lblIsbn, mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);
    }

    @Override
    public void onResume() {
        //noinspection ConstantConditions
        mBookViewModel.pruneAuthors(getContext());
        mBookViewModel.pruneSeries(getContext());

        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();
        // With all Views populated, (re-)add the helpers which rely on fields having valid views

        addAutocomplete(R.id.genre, mFragmentVM.getAllGenres());
        addAutocomplete(R.id.language, mFragmentVM.getAllLanguagesCodes());
    }

    @Override
    protected void onInitFields(@NonNull final Fields fields) {
        super.onInitFields(fields);

        final String nonBlankRequired = getString(R.string.vldt_non_blank_required);

        fields.add(R.id.author, new TextViewAccessor<>(
                           new AuthorListFormatter(Author.Details.Short, true, false)),
                   Book.BKEY_AUTHOR_ARRAY, DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author)
              .setErrorViewId(R.id.lbl_author)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.series_title, new TextViewAccessor<>(
                           new SeriesListFormatter(Series.Details.Short, true, false)),
                   Book.BKEY_SERIES_ARRAY, DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.title, new EditTextAccessor<String>(), DBDefinitions.KEY_TITLE)
              .setErrorViewId(R.id.lbl_title)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.description, new EditTextAccessor<>(), DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFields(R.id.lbl_description);

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        fields.add(R.id.isbn, new EditTextAccessor<>(), DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.language, new EditTextAccessor<>(new LanguageFormatter(), true),
                   DBDefinitions.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.genre, new EditTextAccessor<>(), DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        // Personal fields

        // The Bookshelves are a read-only text field. A click will bring up an editor.
        // Note how we combine an EditTextAccessor with a (non Edit) FieldFormatter
        fields.add(R.id.bookshelves, new EditTextAccessor<>(new CsvFormatter(), true),
                   Book.BKEY_BOOKSHELF_ARRAY, DBDefinitions.KEY_FK_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);
    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        //noinspection ConstantConditions
        if (DBDefinitions.isUsed(getContext(), DBDefinitions.PREFS_IS_USED_THUMBNAIL)) {
            final int[] scale = getResources().getIntArray(R.array.cover_scale_edit);

            mCoverHandler[0] = new CoverHandler(this, mProgressBar,
                                                book, mVb.isbn, 0, mVb.coverImage0,
                                                scale[0]);

            mCoverHandler[1] = new CoverHandler(this, mProgressBar,
                                                book, mVb.isbn, 1, mVb.coverImage1,
                                                scale[1]);
        }

        // hide unwanted fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(@IntRange(from = 0) final int cIdx) {
        mFragmentVM.setCurrentCoverHandlerIndex(cIdx);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.sm_isbn_validity, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        switch (mIsbnValidityCheck) {
            case ISBN.VALIDITY_STRICT:
                menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT).setChecked(true);
                break;

            case ISBN.VALIDITY_LOOSE:
                menu.findItem(R.id.MENU_ISBN_VALIDITY_LOOSE).setChecked(true);
                break;

            case ISBN.VALIDITY_NONE:
            default:
                menu.findItem(R.id.MENU_ISBN_VALIDITY_NONE).setChecked(true);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_ISBN_VALIDITY_NONE:
                mIsbnValidityCheck = ISBN.VALIDITY_NONE;
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_NONE);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_NONE);
                return true;

            case R.id.MENU_ISBN_VALIDITY_LOOSE:
                mIsbnValidityCheck = ISBN.VALIDITY_LOOSE;
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_LOOSE);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_LOOSE);
                return true;

            case R.id.MENU_ISBN_VALIDITY_STRICT:
                mIsbnValidityCheck = ISBN.VALIDITY_STRICT;
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_STRICT);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_STRICT);
                return true;

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
            case RequestCode.SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    if (BuildConfig.DEBUG) {
                        //noinspection ConstantConditions
                        mScannerModel.fakeScanInEmulator(getContext(), data);
                    }

                    //noinspection ConstantConditions
                    final String barCode =
                            mScannerModel.getScanner().getBarcode(getContext(), data);
                    if (barCode != null) {
                        mBookViewModel.getBook().putString(DBDefinitions.KEY_ISBN, barCode);
                        return;
                    }
                }
                return;
            }

            default: {
                // handle any cover image request codes
                final int cIdx = mFragmentVM.getAndClearCurrentCoverHandlerIndex();
                if (cIdx >= 0 && cIdx < mCoverHandler.length) {
                    if (mCoverHandler[cIdx] != null) {
                        if (mCoverHandler[cIdx].onActivityResult(requestCode, resultCode, data)) {
                            break;
                        }
                    } else {
                        // 2020-05-14: Can't explain it yet, but seen this to be null
                        // in the emulator:
                        // start device and app in normal portrait mode.
                        // turn the device twice CW, i.e. the screen should be upside down.
                        // The emulator will be upside down, but the app will be sideways.
                        // Take picture... get here and see NULL mCoverHandler[cIdx].

                        //noinspection ConstantConditions
                        Logger.warnWithStackTrace(getContext(), TAG,
                                                  "onActivityResult"
                                                  + "|mCoverHandler was NULL for cIdx=" + cIdx);
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }
}
