/*
 * @Copyright 2018-2021 HardBackNutter
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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
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
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.CoverViewHost {

    /** Log tag. */
    private static final String TAG = "EditBookFieldsFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELVES = TAG + ":rk:" + CheckListDialogFragment.TAG;

    private final CheckListDialogFragment.Launcher mEditBookshelvesLauncher =
            new CheckListDialogFragment.Launcher() {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final ArrayList<Entity> selectedItems) {
                    final Field<List<Entity>, TextView> field = getField(fieldId);
                    mVm.getBook().putParcelableArrayList(field.getKey(), selectedItems);
                    field.getAccessor().setValue(selectedItems);
                    field.onChanged(true);
                }
            };
    /** The scanner. */
    private final ActivityResultLauncher<Fragment> mScannerLauncher = registerForActivityResult(
            new ScannerContract(), barCode -> {
                if (barCode != null) {
                    mVm.getBook().putString(DBDefinitions.KEY_ISBN, barCode);
                }
            });
    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    /** manage the validation check next to the ISBN field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    /** Watch and clean the text entered in the ISBN field. */
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;
    /** The level of checking the ISBN code. */
    @ISBN.Validity
    private int mIsbnValidityCheck;
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

        mEditBookshelvesLauncher.register(getChildFragmentManager(), this, RK_EDIT_BOOKSHELVES);
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
        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        final Resources res = getResources();

        if (mVm.isCoverUsed(global, 0)) {
            createCoverHandler(res.getDimensionPixelSize(R.dimen.cover_edit_0_width),
                               res.getDimensionPixelSize(R.dimen.cover_edit_0_height),
                               0, mVb.coverOperationProgressBar);
        } else {
            mVb.coverImage0.setVisibility(View.GONE);
        }

        if (mVm.isCoverUsed(global, 1)) {
            createCoverHandler(res.getDimensionPixelSize(R.dimen.cover_edit_1_width),
                               res.getDimensionPixelSize(R.dimen.cover_edit_1_height),
                               1, mVb.coverOperationProgressBar);
        } else {
            mVb.coverImage1.setVisibility(View.GONE);
        }

        mVb.btnScan.setOnClickListener(v -> mScannerLauncher.launch(this));

        mVm.onAuthorList().observe(getViewLifecycleOwner(), authors -> {
            final Field<List<Author>, TextView> field = getField(R.id.author);
            field.getAccessor().setValue(authors);
            field.validate();
        });

        // no listener/callback. We share the book view model in the Activity scope
        mVb.author.setOnClickListener(v -> EditBookAuthorListDialogFragment
                .launch(getChildFragmentManager()));

        if (getField(R.id.series_title).isUsed(global)) {
            mVm.onSeriesList().observe(getViewLifecycleOwner(), series -> {
                final Field<List<Series>, TextView> field = getField(R.id.series_title);
                field.getAccessor().setValue(series);
                field.validate();
            });

            // no listener/callback. We share the book view model in the Activity scope
            mVb.seriesTitle.setOnClickListener(v -> EditBookSeriesListDialogFragment
                    .launch(getChildFragmentManager()));
        }

        // Bookshelves editor (dialog)
        if (getField(R.id.bookshelves).isUsed(global)) {
            mVb.bookshelves.setOnClickListener(v -> {
                final ArrayList<Entity> allItems = new ArrayList<>(mVm.getAllBookshelves());
                final ArrayList<Entity> selectedItems = new ArrayList<>(
                        mVm.getBook().getParcelableArrayList(Book.BKEY_BOOKSHELF_LIST));

                mEditBookshelvesLauncher.launch(getString(R.string.lbl_bookshelves),
                                                R.id.bookshelves, allItems, selectedItems);
            });
        }

        mIsbnValidityCheck = ISBN.getEditValidityLevel(global);
        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
        mIsbnValidationTextWatcher = new ISBN.ValidationTextWatcher(
                mVb.lblIsbn, mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);
    }

    private void createCoverHandler(final int maxWidth,
                                    final int maxHeight,
                                    final int cIdx,
                                    @NonNull final ProgressBar progressBar) {
        mCoverHandler[cIdx] = new CoverHandler(mVm.getDb(), cIdx, maxWidth, maxHeight);
        mCoverHandler[cIdx].onViewCreated(this);
        mCoverHandler[cIdx].setProgressBar(progressBar);
        mCoverHandler[cIdx].setBookSupplier(() -> mVm.getBook());

        //noinspection ConstantConditions
        mCoverHandler[cIdx].setCoverBrowserTitleSupplier(() -> mVb.title.getText().toString());
        //noinspection ConstantConditions
        mCoverHandler[cIdx].setCoverBrowserIsbnSupplier(() -> mVb.isbn.getText().toString());
    }

    @Override
    public void onResume() {
        //noinspection ConstantConditions
        mVm.pruneAuthors(getContext());
        mVm.pruneSeries(getContext());

        // hook up the Views, and calls {@link #onPopulateViews}
        super.onResume();
        // With all Views populated, (re-)add the helpers which rely on fields having valid views

        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());
        addAutocomplete(global, getField(R.id.genre), () -> mVm.getAllGenres());
        addAutocomplete(global, getField(R.id.language), () -> mVm.getAllLanguagesCodes());
    }

    @Override
    protected void onInitFields(@NonNull final Fields fields) {

        final String nonBlankRequired = getString(R.string.vldt_non_blank_required);

        //noinspection ConstantConditions
        final Locale userLocale = AppLocale.getInstance().getUserLocale(getContext());

        fields.add(R.id.author, new TextViewAccessor<>(
                           new AuthorListFormatter(Author.Details.Short, true, false)),
                   Book.BKEY_AUTHOR_LIST, DBDefinitions.KEY_FK_AUTHOR)
              .setRelatedFields(R.id.lbl_author)
              .setErrorViewId(R.id.lbl_author)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.series_title, new TextViewAccessor<>(
                           new SeriesListFormatter(Series.Details.Short, true, false)),
                   Book.BKEY_SERIES_LIST, DBDefinitions.KEY_SERIES_TITLE)
              .setRelatedFields(R.id.lbl_series);

        fields.add(R.id.title, new EditTextAccessor<String>(), DBDefinitions.KEY_TITLE)
              .setErrorViewId(R.id.lbl_title)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.description, new EditTextAccessor<>(), DBDefinitions.KEY_DESCRIPTION)
              .setRelatedFields(R.id.lbl_description);

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        fields.add(R.id.isbn, new EditTextAccessor<>(), DBDefinitions.KEY_ISBN)
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.language, new EditTextAccessor<>(new LanguageFormatter(userLocale), true),
                   DBDefinitions.KEY_LANGUAGE)
              .setRelatedFields(R.id.lbl_language)
              .setErrorViewId(R.id.lbl_language)
              .setFieldValidator(field -> field.getAccessor().setErrorIfEmpty(nonBlankRequired));

        fields.add(R.id.genre, new EditTextAccessor<>(), DBDefinitions.KEY_GENRE)
              .setRelatedFields(R.id.lbl_genre);

        // Personal fields

        // The Bookshelves are a read-only text field. A click will bring up an editor.
        // Note how we combine an EditTextAccessor with a (non Edit) FieldFormatter
        fields.add(R.id.bookshelves, new EditTextAccessor<>(new CsvFormatter(), true),
                   Book.BKEY_BOOKSHELF_LIST, DBDefinitions.KEY_FK_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);
    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        if (mCoverHandler[0] != null) {
            mCoverHandler[0].onBindView(mVb.coverImage0, book);
            mCoverHandler[0].attachOnClickListeners(getChildFragmentManager(), mVb.coverImage0);
        }

        if (mCoverHandler[1] != null) {
            mCoverHandler[1].onBindView(mVb.coverImage1, book);
            mCoverHandler[1].attachOnClickListeners(getChildFragmentManager(), mVb.coverImage1);
        }

        // hide unwanted and empty fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }

    @Override
    public void showMsg(@StringRes final int stringRes) {
        //noinspection ConstantConditions
        Snackbar.make(getView(), getString(stringRes), Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void refresh(@IntRange(from = 0, to = 1) final int cIdx) {
        if (mCoverHandler[cIdx] != null) {
            final ImageView view = cIdx == 0 ? mVb.coverImage0 : mVb.coverImage1;
            mCoverHandler[cIdx].onBindView(view, mVm.getBook());
        }
    }

    @Override
    public void postRefresh(final int cIdx) {
        //noinspection ConstantConditions
        getView().post(() -> refresh(cIdx));
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
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_ISBN_VALIDITY_NONE) {
            mIsbnValidityCheck = ISBN.VALIDITY_NONE;
            mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_NONE);
            mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_NONE);
            return true;

        } else if (itemId == R.id.MENU_ISBN_VALIDITY_LOOSE) {
            mIsbnValidityCheck = ISBN.VALIDITY_LOOSE;
            mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_LOOSE);
            mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_LOOSE);
            return true;

        } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
            mIsbnValidityCheck = ISBN.VALIDITY_STRICT;
            mIsbnCleanupTextWatcher.setValidityLevel(ISBN.VALIDITY_STRICT);
            mIsbnValidationTextWatcher.setValidityLevel(ISBN.VALIDITY_STRICT);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
