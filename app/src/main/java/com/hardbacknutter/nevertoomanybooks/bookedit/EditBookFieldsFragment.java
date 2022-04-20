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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.MenuHelper;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;

public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.CoverHandlerOwner {

    /** Log tag. */
    private static final String TAG = "EditBookFieldsFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELVES = TAG + ":rk:" + MultiChoiceDialogFragment.TAG;

    private final MultiChoiceDialogFragment.Launcher<Bookshelf> mEditBookshelvesLauncher =
            new MultiChoiceDialogFragment.Launcher<>(RK_EDIT_BOOKSHELVES) {
                @Override
                public void onResult(@IdRes final int fieldId,
                                     @NonNull final ArrayList<Bookshelf> selectedItems) {
                    final Field<List<Bookshelf>, TextView> field = mVm.requireField(fieldId);
                    final List<Bookshelf> previous = field.getValue();
                    mVm.getBook().putParcelableArrayList(Book.BKEY_BOOKSHELF_LIST, selectedItems);
                    field.setValue(selectedItems);
                    field.notifyIfChanged(previous);
                }
            };

    /** The scanner. */
    private final ActivityResultLauncher<Fragment> mScannerLauncher = registerForActivityResult(
            new ScannerContract(), barCode -> {
                if (barCode != null) {
                    mVm.getBook().putString(DBKey.KEY_ISBN, barCode);
                }
            });

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    /** manage the validation check next to the ISBN field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    /** Watch and clean the text entered in the ISBN field. */
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;

    /** The level of checking the ISBN code. */
    private ISBN.Validity mIsbnValidityCheck;
    /** View Binding. */
    private FragmentEditBookFieldsBinding mVb;

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider mToolbarMenuProvider;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Main;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEditBookshelvesLauncher.registerForFragmentResult(getChildFragmentManager(), this);
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
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        mToolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(mToolbarMenuProvider, getViewLifecycleOwner(),
                                Lifecycle.State.RESUMED);

        final Context context = getContext();
        //noinspection ConstantConditions
        mVm.initFields(context, FragmentId.Main, FieldGroup.Main);

        final SharedPreferences global = PreferenceManager.getDefaultSharedPreferences(context);
        createCoverDelegates(global);

        mVm.onAuthorList().observe(getViewLifecycleOwner(),
                                   authors -> mVm.requireField(R.id.author)
                                                 .setValue(authors));

        // Author editor (screen)
        // no listener/callback. We share the book view model in the Activity scope
        mVb.lblAuthor.setEndIconOnClickListener(v -> editAuthor());
        mVb.author.setOnClickListener(v -> editAuthor());

        if (DBKey.isUsed(global, DBKey.KEY_SERIES_TITLE)) {
            mVm.onSeriesList().observe(getViewLifecycleOwner(),
                                       series -> mVm.requireField(R.id.series_title)
                                                    .setValue(series));
            // Series editor (screen)
            // no listener/callback. We share the book view model in the Activity scope
            mVb.lblSeries.setEndIconOnClickListener(v -> editSeries());
            mVb.seriesTitle.setOnClickListener(v -> editSeries());
        }

        // Bookshelves editor (dialog)
        mVb.lblBookshelves.setEndIconOnClickListener(v -> editBookshelves());
        mVb.bookshelves.setOnClickListener(v -> editBookshelves());

        // ISBN: manual edit of the field, or click the end-icon to scan a barcode
        mIsbnValidityCheck = ISBN.Validity.getLevel(global);
        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
        mIsbnValidationTextWatcher = new ISBN.ValidationTextWatcher(
                mVb.lblIsbn, mVb.isbn, mIsbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);
        mVb.lblIsbn.setEndIconOnClickListener(v -> mScannerLauncher.launch(this));
    }

    private void createCoverDelegates(@NonNull final SharedPreferences global) {
        final Resources res = getResources();
        final TypedArray width = res.obtainTypedArray(R.array.cover_edit_width);
        final TypedArray height = res.obtainTypedArray(R.array.cover_edit_height);
        try {
            for (int cIdx = 0; cIdx < width.length(); cIdx++) {
                // in edit mode, always show both covers unless globally disabled
                if (DBKey.isUsed(global, DBKey.COVER_IS_USED[cIdx])) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = height.getDimensionPixelSize(cIdx, 0);

                    //noinspection ConstantConditions
                    mCoverHandler[cIdx] = new CoverHandler(this, cIdx, maxWidth, maxHeight)
                            .setBookSupplier(() -> mVm.getBook())
                            .setProgressView(mVb.coverOperationProgressBar)
                            .onFragmentViewCreated(this)
                            .setCoverBrowserTitleSupplier(() -> mVb.title.getText().toString())
                            .setCoverBrowserIsbnSupplier(() -> mVb.isbn.getText().toString());
                } else {
                    // This is silly... ViewBinding has no arrays.
                    if (cIdx == 0) {
                        mVb.coverImage0.setVisibility(View.GONE);
                    } else {
                        mVb.coverImage1.setVisibility(View.GONE);
                    }
                }
            }
        } finally {
            width.recycle();
            height.recycle();
        }
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        //noinspection ConstantConditions
        mVm.getBook().pruneAuthors(getContext(), true);
        mVm.getBook().pruneSeries(getContext(), true);

        super.onPopulateViews(fields, book);

        if (mCoverHandler[0] != null) {
            mCoverHandler[0].onBindView(mVb.coverImage0);
            mCoverHandler[0].attachOnClickListeners(getChildFragmentManager(), mVb.coverImage0);
        }

        if (mCoverHandler[1] != null) {
            mCoverHandler[1].onBindView(mVb.coverImage1);
            mCoverHandler[1].attachOnClickListeners(getChildFragmentManager(), mVb.coverImage1);
        }


        final SharedPreferences global = PreferenceManager
                .getDefaultSharedPreferences(getContext());

        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(getView(), global, false, false));
    }

    @Override
    public void reloadImage(@IntRange(from = 0, to = 1) final int cIdx) {
        if (mCoverHandler[cIdx] != null) {
            final ImageView view = cIdx == 0 ? mVb.coverImage0 : mVb.coverImage1;
            mCoverHandler[cIdx].onBindView(view);
        }
    }

    private void editAuthor() {
        EditBookAuthorListDialogFragment.launch(getChildFragmentManager());
    }

    private void editSeries() {
        EditBookSeriesListDialogFragment.launch(getChildFragmentManager());
    }

    private void editBookshelves() {
        final ArrayList<Bookshelf> allItems = new ArrayList<>(mVm.getAllBookshelves());
        final ArrayList<Bookshelf> selectedItems = new ArrayList<>(
                mVm.getBook().getBookshelves());

        mEditBookshelvesLauncher.launch(getString(R.string.lbl_bookshelves),
                                        R.id.bookshelves, allItems, selectedItems);
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.sm_isbn_validity, menu);

            //noinspection ConstantConditions
            MenuHelper.customizeMenuGroupTitle(getContext(), menu, R.id.sm_title_isbn_validity);

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            switch (mIsbnValidityCheck) {
                case Strict:
                    menu.findItem(R.id.MENU_ISBN_VALIDITY_STRICT).setChecked(true);
                    break;

                case Loose:
                    menu.findItem(R.id.MENU_ISBN_VALIDITY_LOOSE).setChecked(true);
                    break;

                case None:
                default:
                    menu.findItem(R.id.MENU_ISBN_VALIDITY_NONE).setChecked(true);
                    break;
            }
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_ISBN_VALIDITY_NONE) {
                mIsbnValidityCheck = ISBN.Validity.None;
                onPrepareMenu(getToolbar().getMenu());
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.None);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.Validity.None);
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_LOOSE) {
                mIsbnValidityCheck = ISBN.Validity.Loose;
                onPrepareMenu(getToolbar().getMenu());
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                mIsbnValidityCheck = ISBN.Validity.Strict;
                onPrepareMenu(getToolbar().getMenu());
                mIsbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                mIsbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                return true;
            }

            return false;
        }
    }
}
