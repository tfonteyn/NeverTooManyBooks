/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
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
import android.content.SharedPreferences;
import android.os.Bundle;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
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

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELVES = CheckListDialogFragment.TAG + ":rk";

    private final CheckListDialogFragment.OnResultListener mOnCheckListListener =
            (fieldId, selectedItems) -> {
                final Field<List<Entity>, TextView> field = getField(fieldId);
                mBookViewModel.getBook().putParcelableArrayList(field.getKey(), selectedItems);
                field.getAccessor().setValue(selectedItems);
                field.onChanged(true);
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
    public String getFragmentId() {
        return TAG;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getChildFragmentManager()
                .setFragmentResultListener(RK_EDIT_BOOKSHELVES, this, mOnCheckListListener);

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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        if (!mFragmentVM.isCoverUsed(getContext(), prefs, 0)) {
            mVb.coverImage0.setVisibility(View.GONE);
        }

        if (!mFragmentVM.isCoverUsed(getContext(), prefs, 1)) {
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

        if (getField(R.id.series_title).isUsed(prefs)) {
            mVb.seriesTitle.setOnClickListener(v -> EditBookSeriesListDialogFragment
                    .newInstance()
                    // no listener/callback. We share the book view model in the Activity scope
                    .show(getChildFragmentManager(), EditBookSeriesListDialogFragment.TAG));
        }

        // Bookshelves editor (dialog)
        if (getField(R.id.bookshelves).isUsed(prefs)) {
            mVb.bookshelves.setOnClickListener(v -> {
                final ArrayList<Entity> allItems =
                        new ArrayList<>(mFragmentVM.getAllBookshelves());
                final ArrayList<Entity> selectedItems =
                        new ArrayList<>(mBookViewModel.getBook().getParcelableArrayList(
                                Book.BKEY_BOOKSHELF_ARRAY));
                CheckListDialogFragment
                        .newInstance(RK_EDIT_BOOKSHELVES,
                                     getString(R.string.lbl_bookshelves_long),
                                     R.id.bookshelves,
                                     allItems, selectedItems)
                        .show(getChildFragmentManager(), CheckListDialogFragment.TAG);
            });
        }

        mIsbnValidityCheck = ISBN.getEditValidityLevel(prefs);

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

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        addAutocomplete(prefs, getField(R.id.genre), () -> mFragmentVM.getAllGenres());
        addAutocomplete(prefs, getField(R.id.language), () -> mFragmentVM.getAllLanguagesCodes());
    }

    @Override
    protected void onInitFields(@NonNull final Fields fields) {

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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        if (mFragmentVM.isCoverUsed(getContext(), prefs, 0)) {
            mCoverHandler[0] = new CoverHandler(
                    this, mProgressBar, book, mVb.isbn, 0, mVb.coverImage0,
                    getResources().getDimensionPixelSize(R.dimen.cover_edit_0_height));
        }

        if (mFragmentVM.isCoverUsed(getContext(), prefs, 1)) {
            mCoverHandler[1] = new CoverHandler(
                    this, mProgressBar, book, mVb.isbn, 1, mVb.coverImage1,
                    getResources().getDimensionPixelSize(R.dimen.cover_edit_1_height));
        }

        // hide unwanted and empty fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(@IntRange(from = 0, to = 1) final int cIdx) {
        mFragmentVM.setCurrentCoverHandlerIndex(cIdx);
    }

    @Override
    public void onCoverChanged(final int cIdx,
                               @Nullable final File file) {
        mBookViewModel.onCoverChanged(cIdx, file);
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

        super.onPrepareOptionsMenu(menu);
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
                @IntRange(from = -1, to = 1)
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
