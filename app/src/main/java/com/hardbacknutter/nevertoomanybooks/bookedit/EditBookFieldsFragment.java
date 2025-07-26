/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.CoverScale;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.ImageHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.multichoice.MultiChoiceLauncher;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.menus.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;

/**
 * Note that the 'pick-list' fields are stored directly in the Book as well as the field.
 * i.e. Authors, Series, Bookshelves
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment {

    /** The scanner. */
    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScannerContract(), o -> o.ifPresent(
                    barCode -> {
                        vm.getBook().setIsbn(barCode);
                        //noinspection DataFlowIssue
                        SoundManager.beepOnBarcodeFound(getContext());
                    }));

    /**
     * Delegate to handle cover replacement, rotation, etc.
     * Individual instances are only created when the corresponding image field is globally enabled.
     * i.o.w. check for {@code null} !
     */
    private final ImageHandler[] imageHandler = new ImageHandler[DBKey.NR_OF_BOOK_COVERS];
    private ImageView[] coverViews;

    private MultiChoiceLauncher<Bookshelf> editBookshelvesLauncher;
    /** manage the validation check next to the ISBN field. */
    private ISBN.ValidationTextWatcher isbnValidationTextWatcher;
    /** Watch and clean the text entered in the ISBN field. */
    private ISBN.CleanupTextWatcher isbnCleanupTextWatcher;

    /** The level of checking the ISBN code. */
    private ISBN.Validity isbnValidityCheck;
    /** View Binding. */
    private FragmentEditBookFieldsBinding vb;

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.Main;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();

        editBookshelvesLauncher = new MultiChoiceLauncher<>(DBKey.FK_BOOKSHELF);
        editBookshelvesLauncher.setResultListener(vm::updateBookshelves);
        editBookshelvesLauncher.registerForFragmentResult(fm, this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditBookFieldsBinding.inflate(inflater, container, false);
        coverViews = new ImageView[]{
                vb.coverImage0, vb.coverImage1, vb.coverImage2, vb.coverImage3};
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getToolbar().addMenuProvider(new ToolbarMenuProvider(), getViewLifecycleOwner(),
                                     Lifecycle.State.RESUMED);

        final Context context = getContext();
        //noinspection DataFlowIssue
        vm.initFields(context, FragmentId.Main, FieldGroup.Main);

        createCoverDelegates();

        // Author editor (screen)
        // no listener/callback. We share the book view model in the Activity scope
        vb.lblAuthor.setEndIconOnClickListener(v -> editAuthor());
        vb.author.setOnClickListener(v -> editAuthor());

        // Series editor (screen)
        // no listener/callback. We share the book view model in the Activity scope
        vb.lblSeries.setEndIconOnClickListener(v -> editSeries());
        vb.seriesTitle.setOnClickListener(v -> editSeries());

        // Tags editor (dialog)
        vb.lblTags.setEndIconOnClickListener(v -> editTags());
        vb.tags.setOnClickListener(v -> editTags());

        // Bookshelves editor (dialog)
        vb.lblBookshelves.setEndIconOnClickListener(v -> editBookshelves());
        vb.bookshelves.setOnClickListener(v -> editBookshelves());

        // ISBN: manual edit of the field, or click the end-icon to scan a barcode
        isbnValidityCheck = ISBN.Validity.getLevel(context);
        isbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnCleanupTextWatcher);
        isbnValidationTextWatcher = new ISBN.ValidationTextWatcher(
                vb.lblIsbn, vb.isbn, isbnValidityCheck);
        vb.isbn.addTextChangedListener(isbnValidationTextWatcher);
        vb.lblIsbn.setEndIconOnClickListener(v -> scanLauncher.launch(
                ScannerContract.createDefaultOptions(getContext())));
    }

    private void createCoverDelegates() {
        final Resources res = getResources();
        final TypedArray width = res.obtainTypedArray(R.array.cover_edit_max_width);
        try {
            for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
                // in edit mode, always show all images unless globally disabled
                if (ServiceLocator.getInstance().isFieldEnabled(DBKey.COVER[cIdx])) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = (int) (maxWidth / CoverScale.HW_RATIO);

                    //noinspection DataFlowIssue
                    imageHandler[cIdx] = new ImageHandler
                            .Builder(this, cIdx, maxWidth, maxHeight)
                            .setImageView(coverViews[cIdx])
                            .setImageOwner(() -> vm.getBook())
                            .setOnReloadImage(idx -> imageHandler[idx].onBindView())
                            .setCoverBrowserTitleSupplier(() -> vb.title.getText().toString())
                            .setCoverBrowserIsbnSupplier(() -> vb.isbn.getText().toString())
                            .build();
                } else {
                    coverViews[cIdx].setVisibility(View.GONE);
                }
            }
        } finally {
            width.recycle();
        }
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();
        getFab().setVisibility(View.INVISIBLE);
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        //noinspection DataFlowIssue
        vm.getBook().pruneAuthors(getContext());
        vm.getBook().pruneSeries(getContext());

        super.onPopulateViews(fields, book);

        for (int cIdx = 0; cIdx < DBKey.NR_OF_BOOK_COVERS; cIdx++) {
            if (imageHandler[cIdx] != null) {
                imageHandler[cIdx].onBindView();
            }
        }

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }

    private void editAuthor() {
        EditBookAuthorListDialogFragment.launch(getChildFragmentManager());
    }

    private void editSeries() {
        EditBookSeriesListDialogFragment.launch(getChildFragmentManager());
    }

    private void editTags() {
        EditBookTagsDialogFragment.launch(getChildFragmentManager());
    }

    private void editBookshelves() {
        //noinspection DataFlowIssue
        editBookshelvesLauncher.launch(getActivity(),
                                       getString(R.string.lbl_bookshelves),
                                       null,
                                       vm.getAllBookshelves(),
                                       vm.getBook().getBookshelves(),
                                       null);
    }

    private final class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            MenuCompat.setGroupDividerEnabled(menu, true);
            menuInflater.inflate(R.menu.sm_isbn_validity, menu);

            //noinspection DataFlowIssue
            MenuUtils.customizeMenuGroupTitle(getContext(), menu, R.id.sm_title_isbn_validity);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            switch (isbnValidityCheck) {
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
            final int menuItemId = menuItem.getItemId();

            if (menuItemId == R.id.MENU_ISBN_VALIDITY_NONE) {
                isbnValidityCheck = ISBN.Validity.None;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.None);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.None);
                return true;

            } else if (menuItemId == R.id.MENU_ISBN_VALIDITY_LOOSE) {
                isbnValidityCheck = ISBN.Validity.Loose;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                return true;

            } else if (menuItemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                isbnValidityCheck = ISBN.Validity.Strict;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                return true;
            }

            return false;
        }
    }
}
