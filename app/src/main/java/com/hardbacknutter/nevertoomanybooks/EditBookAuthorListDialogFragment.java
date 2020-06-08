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

import android.content.Context;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorListBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.ItemWithFixableId;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Authors of a Book.
 * <p>
 * <strong>Warning:</strong> By exception this DialogFragment uses the parents ViewModel directly.
 * This means that any observables in the ViewModel must be tested/used with care, as their
 * destination view might not be available at the moment of an update being triggered.
 * <p>
 * Maybe TODO: cannot set author type when creating but only when editing existing author.
 */
public class EditBookAuthorListDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    static final String TAG = "EditBookAuthorListDlg";

    /** Database Access. */
    private DAO mDb;

    /** The book. Must be in the Activity scope. */
    private BookViewModel mBookViewModel;

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookViewModel.setDirty(true);
                }
            };
    /** View Binding. */
    private DialogEditBookAuthorListBinding mVb;
    /** the rows. */
    private ArrayList<Author> mList;
    /** The adapter for the list itself. */
    private AuthorListAdapter mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor.
     * <p>
     * Always force full screen as this dialog is to large/complicated.
     */
    public EditBookAuthorListDialogFragment() {
        super(R.layout.dialog_edit_book_author_list, true);
    }

    /**
     * Constructor.
     *
     * @return instance
     */
    public static DialogFragment newInstance() {
        return new EditBookAuthorListDialogFragment();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_App_FullScreen);

        mDb = new DAO(TAG);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookAuthorListBinding.bind(view);

        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
        //noinspection ConstantConditions
        mBookViewModel.init(getContext(), getArguments());

        mVb.toolbar.setSubtitle(mBookViewModel.getBook().getTitle());
        mVb.toolbar.setNavigationOnClickListener(v -> {
            if (saveChanges()) {
                dismiss();
            }
        });
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add) {
                onAdd();
                return true;
            }
            return false;
        });

        final DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item,
                mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));
        mVb.author.setAdapter(nameAdapter);
        mVb.author.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mVb.lblAuthor.setError(null);
            }
        });
        // soft-keyboards 'done' button act as a shortcut to add the author
        mVb.author.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                onAdd();
                return true;
            }
            return false;
        });

        final LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mVb.authorList.setLayoutManager(layoutManager);
        mVb.authorList.setHasFixedSize(true);

        mList = mBookViewModel.getBook().getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        mListAdapter = new AuthorListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.authorList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.authorList);
    }

    private boolean saveChanges() {
        if (!mVb.author.getText().toString().isEmpty()) {
            // Discarding applies to the temp author edit box only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.author.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mBookViewModel.updateAuthors(mList);
        return true;
    }

    private void onAdd() {
        // clear any previous error
        mVb.lblAuthor.setError(null);

        final String name = mVb.author.getText().toString().trim();
        if (name.isEmpty()) {
            mVb.lblAuthor.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Author newAuthor = Author.from(name);

        // see if it already exists
        //noinspection ConstantConditions
        newAuthor.fixId(getContext(), mDb, LocaleUtils.getUserLocale(getContext()));
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            mVb.lblAuthor.setError(getString(R.string.warning_author_already_in_list));
        } else {
            mList.add(newAuthor);
            // clear the form for next entry and scroll to the new item
            mVb.author.setText("");
            mVb.author.requestFocus();
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.authorList.scrollToPosition(mListAdapter.getItemCount() - 1);
        }
    }

    /**
     * Process the modified (if any) data.
     *
     * @param author  the user was editing (with the original data)
     * @param tmpData the modifications the user made in a placeholder object.
     *                Non-modified data was copied here as well.
     *                The id==0 will not be used/updated.
     */
    private void processChanges(@NonNull final Author author,
                                @NonNull final Author tmpData) {

        // name not changed ?
        if (author.getFamilyName().equals(tmpData.getFamilyName())
            && author.getGivenNames().equals(tmpData.getGivenNames())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            author.setComplete(tmpData.isComplete());

            // Type is not part of the Author table, but of the book_author table.
            if (author.getType() != tmpData.getType()) {
                // so if the type is different, just update it
                author.setType(tmpData.getType());
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name of the Author was modified.
        // Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mBookViewModel.isSingleUsage(getContext(), author)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            author.copyFrom(tmpData, true);
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        final String allBooks = getString(R.string.bookshelf_all_books);
        final String message = getString(R.string.confirm_apply_author_changed,
                                         author.getLabel(getContext()),
                                         tmpData.getLabel(getContext()),
                                         allBooks);
        new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_warning)
                .setTitle(R.string.lbl_scope_of_change)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setNeutralButton(allBooks, (d, w) -> {
                    // copy all new data
                    author.copyFrom(tmpData, true);
                    // This change is done in the database right NOW!
                    if (mDb.updateAuthor(getContext(), author)) {
                        mBookViewModel.refreshAuthorList(getContext());
                        mListAdapter.notifyDataSetChanged();

                    } else {
                        Logger.warnWithStackTrace(getContext(), TAG, "Could not update",
                                                  "author=" + author,
                                                  "tmpAuthor=" + tmpData);
                        StandardDialogs.showError(getContext(), R.string.error_unexpected_error);
                    }
                })
                .setPositiveButton(R.string.btn_this_book, (d, w) -> {
                    // treat the new data as a new Author; save it so we have a valid id.
                    // Note that if the user abandons the entire book edit,
                    // we will orphan this new Author. That's ok, it will get
                    // garbage collected from the database sooner or later.
                    mDb.updateOrInsertAuthor(getContext(), tmpData);

                    // unlink the old one (and unmodified), and link with the new one
                    // book/author positions will be fixed up when saving.
                    // Note that the old one *might* be orphaned at this time.
                    // Same remark as above.
                    mList.remove(author);
                    mList.add(tmpData);
                    ItemWithFixableId.pruneList(mList, getContext(), mDb,
                                                LocaleUtils.getUserLocale(getContext()),
                                                false);
                    mListAdapter.notifyDataSetChanged();

                    //URGENT: updated author(s): Book gets them, but TocEntries remain using old set
                    //
                    // A TocEntry is unique based on author and title_od.
                    // Updating the in-memory TOC list and/or the TocEntries stored in the database
                    // with the new author:
                    // .
                    // The problem is two-fold:
                    // If we simply create a new TocEntry?
                    // - old one not used anywhere else ? ok, just delete it
                    // - old one present in other books ? replace ? leave as-is ?
                    // but it's the SAME story (text), now existing with two different authors.
                    // - update the TocEntry as-is... i.e. in the database?
                    // .. more headaches....
                    // .
                    // SOLUTION one of:
                    // - just ASK the user with a "mod toc" or "no"
                    // - don't bother, assume this won't be needed very often and
                    //   have the user will do it manually
                })
                .create()
                .show();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView authorView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            authorView = itemView.findViewById(R.id.row_author);
        }
    }

    /**
     * Edit a single Author from the book's author list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     * <p>
     * Must be a public static class to be properly recreated from instance state.
     */
    public static class EditAuthorForBookDialogFragment
            extends BaseDialogFragment {

        /** Fragment/Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        static final String TAG = "EditBookAuthorDialogFrag";

        /**
         * We create a list of all the Type checkboxes for easy handling.
         * The key is the Type.
         */
        private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();

        /** Database Access. */
        private DAO mDb;
        @Nullable
        private String mBookTitle;
        /** View Binding. */
        private DialogEditBookAuthorBinding mVb;
        /** The Author we're editing. */
        private Author mAuthor;

        /** Current edit. */
        private String mFamilyName;
        /** Current edit. */
        private String mGivenNames;
        /** Current edit. */
        private boolean mIsComplete;
        /** Current edit. */
        @Author.Type
        private int mType;

        /**
         * No-arg constructor.
         * <p>
         * Always force full screen as this dialog is to large/complicated.
         */
        public EditAuthorForBookDialogFragment() {
            super(R.layout.dialog_edit_book_author);
        }

        /**
         * Constructor.
         *
         * @param bookTitle displayed for info only
         * @param author    to edit
         *
         * @return instance
         */
        static DialogFragment newInstance(@NonNull final String bookTitle,
                                          @NonNull final Author author) {
            final DialogFragment frag = new EditAuthorForBookDialogFragment();
            final Bundle args = new Bundle(2);
            args.putString(DBDefinitions.KEY_TITLE, bookTitle);
            args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mDb = new DAO(TAG);

            final Bundle args = requireArguments();
            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

            mAuthor = args.getParcelable(DBDefinitions.KEY_FK_AUTHOR);
            Objects.requireNonNull(mAuthor, ErrorMsg.ARGS_MISSING_AUTHOR);

            if (savedInstanceState == null) {
                mFamilyName = mAuthor.getFamilyName();
                mGivenNames = mAuthor.getGivenNames();
                mIsComplete = mAuthor.isComplete();
                mType = mAuthor.getType();
            } else {
                mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                mIsComplete = savedInstanceState
                        .getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, false);
                mType = savedInstanceState.getInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
            }
        }

        @Override
        public void onViewCreated(@NonNull final View view,
                                  @Nullable final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            mVb = DialogEditBookAuthorBinding.bind(view);

            Objects.requireNonNull(getTargetFragment(), ErrorMsg.NO_TARGET_FRAGMENT_SET);

            mVb.toolbar.setSubtitle(mBookTitle);
            mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
            mVb.toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.MENU_SAVE) {
                    if (saveChanges()) {
                        dismiss();
                    }
                    return true;
                }
                return false;
            });

            //noinspection ConstantConditions
            final DiacriticArrayAdapter<String> mFamilyNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FAMILY_NAME));
            final DiacriticArrayAdapter<String> mGivenNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES));

            mVb.familyName.setText(mFamilyName);
            mVb.familyName.setAdapter(mFamilyNameAdapter);

            mVb.givenNames.setText(mGivenNames);
            mVb.givenNames.setAdapter(mGivenNameAdapter);

            mVb.cbxIsComplete.setChecked(mIsComplete);

            final boolean useAuthorType =
                    DBDefinitions.isUsed(getContext(), DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
            mVb.authorTypeGroup.setVisibility(useAuthorType ? View.VISIBLE : View.GONE);
            if (useAuthorType) {
                mVb.btnUseAuthorType.setOnCheckedChangeListener(
                        (v, isChecked) -> setTypeEnabled(isChecked));

                mTypeButtons.put(Author.TYPE_WRITER, mVb.cbxAuthorTypeWriter);
                mTypeButtons.put(Author.TYPE_CONTRIBUTOR, mVb.cbxAuthorTypeContributor);
                mTypeButtons.put(Author.TYPE_INTRODUCTION, mVb.cbxAuthorTypeIntro);
                mTypeButtons.put(Author.TYPE_TRANSLATOR, mVb.cbxAuthorTypeTranslator);
                mTypeButtons.put(Author.TYPE_EDITOR, mVb.cbxAuthorTypeEditor);

                mTypeButtons.put(Author.TYPE_ARTIST, mVb.cbxAuthorTypeArtist);
                mTypeButtons.put(Author.TYPE_INKING, mVb.cbxAuthorTypeInking);
                mTypeButtons.put(Author.TYPE_COLORIST, mVb.cbxAuthorTypeColorist);

                mTypeButtons.put(Author.TYPE_COVER_ARTIST, mVb.cbxAuthorTypeCoverArtist);
                mTypeButtons.put(Author.TYPE_COVER_INKING, mVb.cbxAuthorTypeCoverInking);
                mTypeButtons.put(Author.TYPE_COVER_COLORIST, mVb.cbxAuthorTypeCoverColorist);

                if (mType != Author.TYPE_UNKNOWN) {
                    setTypeEnabled(true);
                    for (int i = 0; i < mTypeButtons.size(); i++) {
                        mTypeButtons.valueAt(i).setChecked((mType & mTypeButtons.keyAt(i)) != 0);
                    }
                } else {
                    setTypeEnabled(false);
                }

                /*
                    This is a hack/workaround: the style used on the FrameLayout
                    uses a workaround for an issue with the Toolbar.
                    Here we need to workaround the former workaround depending on
                    displaying the dialog in full-screen mode or not.

                    android:layout_marginBottom:
                        screen +sw600: ?attr/actionBarSize
                        screen -sw600: 0dp
                 */
                final int marginBottom;
                if (getResources().getBoolean(R.bool.isLargeScreen)) {
                    marginBottom = AttrUtils.getDimen(getContext(), R.attr.actionBarSize);
                } else {
                    marginBottom = 0;
                }

                // guard against a unpredicted change in the xml.
                //noinspection ConstantConditions
                if (!(mVb.bodyFrame instanceof NestedScrollView)) {
                    throw new IllegalStateException("mVb.bodyFrame instanceof NestedScrollView");
                }

                final ViewGroup.MarginLayoutParams marginParams =
                        (ViewGroup.MarginLayoutParams) mVb.bodyFrame.getLayoutParams();
                marginParams.bottomMargin = marginBottom;
                mVb.bodyFrame.setLayoutParams(marginParams);
                mVb.bodyFrame.setFillViewport(true);
            }
        }

        /**
         * Enable or disable the type related fields.
         *
         * @param enable Flag
         */
        private void setTypeEnabled(final boolean enable) {
            // don't bother changing the 'checked' status, we'll ignore them anyhow.
            // and this is more user friendly if they flip the switch more than once.
            mVb.btnUseAuthorType.setChecked(enable);
            for (int i = 0; i < mTypeButtons.size(); i++) {
                mTypeButtons.valueAt(i).setEnabled(enable);
            }
        }

        private boolean saveChanges() {
            viewToModel();

            // basic check only, we're doing more extensive checks later on.
            if (mFamilyName.isEmpty()) {
                showError(mVb.lblFamilyName, R.string.vldt_non_blank_required);
                return false;
            }

            // Create a new Author as a holder for all changes.
            final Author tmpAuthor = new Author(mFamilyName, mGivenNames);
            tmpAuthor.setComplete(mIsComplete);
            if (mVb.btnUseAuthorType.isChecked()) {
                tmpAuthor.setType(mType);
            } else {
                tmpAuthor.setType(Author.TYPE_UNKNOWN);
            }

            //noinspection ConstantConditions
            ((EditBookAuthorListDialogFragment) getTargetFragment())
                    .processChanges(mAuthor, tmpAuthor);
            return true;
        }

        private void viewToModel() {
            mFamilyName = mVb.familyName.getText().toString().trim();
            mGivenNames = mVb.givenNames.getText().toString().trim();
            mIsComplete = mVb.cbxIsComplete.isChecked();
            mType = Author.TYPE_UNKNOWN;
            for (int i = 0; i < mTypeButtons.size(); i++) {
                if (mTypeButtons.valueAt(i).isChecked()) {
                    mType |= mTypeButtons.keyAt(i);
                }
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull final Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME, mFamilyName);
            outState.putString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES, mGivenNames);
            outState.putBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE, mIsComplete);
            outState.putInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK, mType);
        }

        @Override
        public void onPause() {
            viewToModel();
            super.onPause();
        }

        @Override
        public void onDestroy() {
            if (mDb != null) {
                mDb.close();
            }
            super.onDestroy();
        }
    }

    private class AuthorListAdapter
            extends RecyclerViewAdapterBase<Author, Holder> {

        @NonNull
        private final FieldFormatter<Author> mFormatter;

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of Authors
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final List<Author> items,
                          @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);

            mFormatter = new AuthorFormatter(Author.Details.Full, false);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_author_list, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Author author = getItem(position);
            mFormatter.apply(author, holder.authorView);

            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> {
                final DialogFragment frag = EditAuthorForBookDialogFragment
                        .newInstance(mBookViewModel.getBook().getTitle(), author);
                frag.setTargetFragment(EditBookAuthorListDialogFragment.this, 0);
                frag.show(getParentFragmentManager(), EditAuthorForBookDialogFragment.TAG);
            });
        }
    }
}
