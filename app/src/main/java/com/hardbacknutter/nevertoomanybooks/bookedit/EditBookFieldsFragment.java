/*
 * @Copyright 2018-2023 HardBackNutter
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
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.covers.CoverHandler;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.MultiChoiceDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.utils.SoundManager;
import com.hardbacknutter.tinyzxingwrapper.ScanOptions;

/**
 * Note that the 'pick-list' fields are stored directly in the Book as well as the field.
 * i.e. Authors, Series, Bookshelves
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.CoverHandlerOwner {

    /** Log tag. */
    private static final String TAG = "EditBookFieldsFragment";

    /** FragmentResultListener request key. */
    private static final String RK_EDIT_BOOKSHELVES = TAG + ":rk:" + MultiChoiceDialogFragment.TAG;

    private final MultiChoiceDialogFragment.Launcher<Bookshelf> editBookshelvesLauncher =
            new MultiChoiceDialogFragment.Launcher<>(RK_EDIT_BOOKSHELVES,
                                                     this::onBookshelvesSelection);

    /** The scanner. */
    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScannerContract(), o -> o.ifPresent(
                    barCode -> {
                        vm.getBook().putString(DBKey.BOOK_ISBN, barCode);
                        //noinspection DataFlowIssue
                        SoundManager.beepOnBarcodeFound(getContext());
                    }));

    /** Delegate to handle cover replacement, rotation, etc. */
    private final CoverHandler[] coverHandler = new CoverHandler[2];

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

        editBookshelvesLauncher.registerForFragmentResult(getChildFragmentManager(), this);
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentEditBookFieldsBinding.inflate(inflater, container, false);
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

        if (ServiceLocator.getInstance().getGlobalFieldVisibility()
                          .isShowField(DBKey.FK_SERIES).orElse(false)) {
            // Series editor (screen)
            // no listener/callback. We share the book view model in the Activity scope
            vb.lblSeries.setEndIconOnClickListener(v -> editSeries());
            vb.seriesTitle.setOnClickListener(v -> editSeries());
        }

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
        final TypedArray width = res.obtainTypedArray(R.array.cover_edit_width);
        final TypedArray height = res.obtainTypedArray(R.array.cover_edit_height);
        try {
            final FieldVisibility fieldVisibility = ServiceLocator.getInstance()
                                                                  .getGlobalFieldVisibility();

            for (int cIdx = 0; cIdx < width.length(); cIdx++) {
                // in edit mode, always show both covers unless globally disabled
                if (fieldVisibility.isShowField(DBKey.COVER[cIdx]).orElse(false)) {
                    final int maxWidth = width.getDimensionPixelSize(cIdx, 0);
                    final int maxHeight = height.getDimensionPixelSize(cIdx, 0);

                    //noinspection DataFlowIssue
                    coverHandler[cIdx] = new CoverHandler(this, cIdx, maxWidth, maxHeight)
                            .setBookSupplier(() -> vm.getBook())
                            .setProgressView(vb.coverOperationProgressBar)
                            .onFragmentViewCreated(this)
                            .setCoverBrowserTitleSupplier(() -> vb.title.getText().toString())
                            .setCoverBrowserIsbnSupplier(() -> vb.isbn.getText().toString());
                } else {
                    // This is silly... ViewBinding has no arrays.
                    if (cIdx == 0) {
                        vb.coverImage0.setVisibility(View.GONE);
                    } else {
                        vb.coverImage1.setVisibility(View.GONE);
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
        //noinspection DataFlowIssue
        vm.getBook().pruneAuthors(getContext(), true);
        vm.getBook().pruneSeries(getContext(), true);

        super.onPopulateViews(fields, book);

        if (coverHandler[0] != null) {
            coverHandler[0].onBindView(vb.coverImage0);
            coverHandler[0].attachOnClickListeners(getChildFragmentManager(), vb.coverImage0);
        }

        if (coverHandler[1] != null) {
            coverHandler[1].onBindView(vb.coverImage1);
            coverHandler[1].attachOnClickListeners(getChildFragmentManager(), vb.coverImage1);
        }

        getFab().setVisibility(View.INVISIBLE);

        //noinspection DataFlowIssue
        fields.forEach(field -> field.setVisibility(getView(), false, false));
    }

    @Override
    public void reloadImage(@IntRange(from = 0, to = 1) final int cIdx) {
        if (coverHandler[cIdx] != null) {
            final ImageView view = cIdx == 0 ? vb.coverImage0 : vb.coverImage1;
            coverHandler[cIdx].onBindView(view);
        }
    }

    private void editAuthor() {
        EditBookAuthorListDialogFragment.launch(getChildFragmentManager());
    }

    private void editSeries() {
        EditBookSeriesListDialogFragment.launch(getChildFragmentManager());
    }

    private void editBookshelves() {
        //noinspection DataFlowIssue
        editBookshelvesLauncher.launch(getContext(), getString(R.string.lbl_bookshelves),
                                       vm.getAllBookshelves(),
                                       vm.getBook().getBookshelves());
    }

    private void onBookshelvesSelection(@NonNull final Set<Long> selectedIds) {
        final Field<List<Bookshelf>, TextView> field =
                vm.requireField(R.id.bookshelves);
        final List<Bookshelf> previous = field.getValue();

        final List<Bookshelf> selected =
                vm.getAllBookshelves()
                  .stream()
                  .filter(bookshelf -> selectedIds.contains(bookshelf.getId()))
                  .collect(Collectors.toCollection(ArrayList::new));

        vm.getBook().setBookshelves(selected);
        field.setValue(selected);
        field.notifyIfChanged(previous);
    }

    private class ToolbarMenuProvider
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
            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_ISBN_VALIDITY_NONE) {
                isbnValidityCheck = ISBN.Validity.None;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.None);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.None);
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_LOOSE) {
                isbnValidityCheck = ISBN.Validity.Loose;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Loose);
                return true;

            } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
                isbnValidityCheck = ISBN.Validity.Strict;
                isbnCleanupTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                isbnValidationTextWatcher.setValidityLevel(ISBN.Validity.Strict);
                return true;
            }

            return false;
        }
    }
}
