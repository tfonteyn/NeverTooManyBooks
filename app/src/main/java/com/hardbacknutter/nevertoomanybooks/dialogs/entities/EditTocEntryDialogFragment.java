/*
 * @Copyright 2018-2024 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.utils.PartialDate;
import com.hardbacknutter.nevertoomanybooks.core.widgets.adapters.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookTocContentBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.EditLauncher;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link TocEntry}.
 */
public class EditTocEntryDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "EditTocEntryDialogFrag";
    private static final String BKEY_ANTHOLOGY = TAG + ":anthology";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";
    private static final String BKEY_POSITION = TAG + ":pos";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    private String requestKey;

    /** View Binding. */
    private DialogEditBookTocContentBinding vb;

    @Nullable
    private String bookTitle;

    /** The one we're editing. */
    private TocEntry tocEntry;
    /** the position of the tocEntry in the TOC list. */
    private int editPosition;

    /** Current edit. FIXME: replace with TocEntry currentEdit ? what about the Author ? */
    private String title;
    /** Current edit. */
    private PartialDate firstPublicationDate;
    /** Current edit. */
    private String authorName;

    /** Helper to show/hide the author edit field. */
    private boolean isAnthology;

    /**
     * No-arg constructor for OS use.
     */
    public EditTocEntryDialogFragment() {
        super(R.layout.dialog_edit_book_toc, R.layout.dialog_edit_book_toc_content);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        bookTitle = args.getString(DBKey.TITLE);
        isAnthology = args.getBoolean(BKEY_ANTHOLOGY, false);
        tocEntry = Objects.requireNonNull(args.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY);
        editPosition = args.getInt(BKEY_POSITION, 0);

        if (savedInstanceState == null) {
            title = tocEntry.getTitle();
            firstPublicationDate = tocEntry.getFirstPublicationDate();
            //noinspection DataFlowIssue
            authorName = tocEntry.getPrimaryAuthor().getLabel(getContext());
        } else {
            title = savedInstanceState.getString(DBKey.TITLE);
            firstPublicationDate = savedInstanceState.getParcelable(DBKey.FIRST_PUBLICATION__DATE);
            authorName = savedInstanceState.getString(DBKey.AUTHOR_FORMATTED);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogEditBookTocContentBinding.bind(view.findViewById(R.id.dialog_content));
        setTitle(bookTitle);

        //ENHANCE: should we provide a TocAdapter to aid manually adding TOC titles?
        // What about the publication year?
        vb.title.setText(title);
        autoRemoveError(vb.title, vb.lblTitle);

        if (firstPublicationDate.isPresent()) {
            vb.firstPublication.setText(String.valueOf(firstPublicationDate.getYearValue()));
        }

        if (isAnthology) {
            //noinspection DataFlowIssue
            final ExtArrayAdapter<String> authorAdapter = new ExtArrayAdapter<>(
                    getContext(), R.layout.popup_dropdown_menu_item,
                    ExtArrayAdapter.FilterType.Diacritic,
                    ServiceLocator.getInstance().getAuthorDao()
                                  .getNames(DBKey.AUTHOR_FORMATTED));
            vb.author.setAdapter(authorAdapter);
            vb.author.setText(authorName);
            vb.author.selectAll();
            vb.author.requestFocus();

            vb.lblAuthor.setVisibility(View.VISIBLE);
            vb.author.setVisibility(View.VISIBLE);

        } else {
            vb.title.requestFocus();

            vb.lblAuthor.setVisibility(View.GONE);
            vb.author.setVisibility(View.GONE);
        }
    }

    @Override
    protected boolean onToolbarButtonClick(@Nullable final View button) {
        if (button != null) {
            final int id = button.getId();
            if (id == R.id.btn_save || id == R.id.btn_positive) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
        }
        return false;
    }

    private boolean saveChanges() {
        viewToModel();
        if (title.isEmpty()) {
            vb.lblTitle.setError(getString(R.string.vldt_non_blank_required));
            return false;
        }

        // anything actually changed ?
        //noinspection DataFlowIssue
        if (tocEntry.getTitle().equals(title)
            && tocEntry.getFirstPublicationDate().equals(firstPublicationDate)
            && tocEntry.getPrimaryAuthor().getLabel(getContext()).equals(authorName)) {
            return true;
        }

        // store changes
        tocEntry.setTitle(title);
        tocEntry.setFirstPublicationDate(firstPublicationDate);
        if (isAnthology) {
            tocEntry.setPrimaryAuthor(Author.from(authorName));
        }

        // We don't update/insert to the database here, but just send the data back.
        // TOCs are updated in bulk/list per Book
        Launcher.setResult(this, requestKey, tocEntry, editPosition);
        return true;
    }

    private void viewToModel() {
        //noinspection DataFlowIssue
        title = vb.title.getText().toString().trim();
        //noinspection DataFlowIssue
        firstPublicationDate = new PartialDate(vb.firstPublication.getText().toString().trim());
        if (isAnthology) {
            authorName = vb.author.getText().toString().trim();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBKey.TITLE, title);
        outState.putParcelable(DBKey.FIRST_PUBLICATION__DATE, firstPublicationDate);
        outState.putString(DBKey.AUTHOR_FORMATTED, authorName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public static class Launcher
            extends EditLauncher {

        @NonNull
        private final ResultListener resultListener;

        public Launcher(@NonNull final String requestKey,
                        @NonNull final ResultListener resultListener) {
            super(requestKey, EditTocEntryDialogFragment::new);
            this.resultListener = resultListener;
        }

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final TocEntry tocEntry,
                              final int position) {

            final Bundle result = new Bundle(2);
            result.putParcelable(BKEY_TOC_ENTRY, tocEntry);
            result.putInt(BKEY_POSITION, position);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Constructor.
         *
         * @param book        the entry belongs to
         * @param position    of the tocEntry in the list
         * @param tocEntry    to edit.
         * @param isAnthology Flag that will enable/disable the author edit field
         */
        public void launch(@NonNull final Book book,
                           final int position,
                           @NonNull final TocEntry tocEntry,
                           final boolean isAnthology) {

            final Bundle args = new Bundle(5);
            args.putString(DBKey.TITLE, book.getTitle());
            args.putBoolean(BKEY_ANTHOLOGY, isAnthology);
            args.putInt(BKEY_POSITION, position);
            args.putParcelable(BKEY_TOC_ENTRY, tocEntry);

            createDialog(args);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            resultListener.onResult(
                    Objects.requireNonNull(result.getParcelable(BKEY_TOC_ENTRY), BKEY_TOC_ENTRY),
                    result.getInt(BKEY_POSITION));
        }

        @FunctionalInterface
        public interface ResultListener {
            /**
             * Callback handler.
             *
             * @param tocEntry the modified entry
             * @param position the position in the list we we're editing
             */
            void onResult(@NonNull TocEntry tocEntry,
                          int position);
        }
    }
}
