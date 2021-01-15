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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookAuthorListBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.AuthorFormatter;
import com.hardbacknutter.nevertoomanybooks.fields.formatters.FieldFormatter;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Edit the list of Authors of a Book.
 * <p>
 * Maybe TODO: cannot set author type when creating but only when editing existing author.
 */
public class EditBookAuthorListDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    private static final String TAG = "EditBookAuthorListDlg";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_AUTHOR = TAG + ":rk:" + EditAuthorForBookDialogFragment.TAG;

    /** The book. Must be in the Activity scope. */
    private EditBookFragmentViewModel mVm;
    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /** View Binding. */
    private DialogEditBookAuthorListBinding mVb;
    /** the rows. */
    private ArrayList<Author> mList;
    /** The adapter for the list itself. */
    private AuthorListAdapter mListAdapter;

    private final EditBookBaseFragment.EditItemLauncher<Author> mOnEditAuthorLauncher =
            new EditBookBaseFragment.EditItemLauncher<Author>() {
                @Override
                public void onResult(@NonNull final Author original,
                                     @NonNull final Author modified) {
                    processChanges(original, modified);
                }
            };

    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookAuthorListDialogFragment() {
        super(R.layout.dialog_edit_book_author_list);
        setForceFullscreen();
    }

    /**
     * Constructor.
     *
     * @param fm The FragmentManager this fragment will be added to.
     */
    public static void launch(@NonNull final FragmentManager fm) {
        new EditBookAuthorListDialogFragment()
                .show(fm, TAG);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mOnEditAuthorLauncher.register(getChildFragmentManager(), this, RK_EDIT_AUTHOR);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookAuthorListBinding.bind(view);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

        mVb.toolbar.setSubtitle(mVm.getBook().getTitle());

        //noinspection ConstantConditions
        final DiacriticArrayAdapter<String> nameAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, mVm.getAllAuthorNames());
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

        mVb.authorList.setHasFixedSize(true);

        mList = mVm.getBook().getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        mListAdapter = new AuthorListAdapter(getContext(), mList,
                                             vh -> mItemTouchHelper.startDrag(vh));
        mVb.authorList.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.authorList);
    }

    @Override
    protected void onToolbarNavigationClick(@NonNull final View v) {
        if (saveChanges()) {
            dismiss();
        }
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            onAdd();
            return true;
        }
        return false;
    }

    /**
     * Create a new entry.
     */
    private void onAdd() {
        // clear any previous error
        mVb.lblAuthor.setError(null);

        final String name = mVb.author.getText().toString().trim();
        if (name.isEmpty()) {
            mVb.lblAuthor.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        // Editing the Author type is not provided on the main screen.
        // The user must open the detail dialog after creation of the entry.
        final Author newAuthor = Author.from(name);

        // see if it already exists
        //noinspection ConstantConditions
        mVm.fixId(getContext(), newAuthor);
        // and check it's not already in the list.
        if (mList.contains(newAuthor)) {
            mVb.lblAuthor.setError(getString(R.string.warning_already_in_list));
        } else {
            // add and scroll to the new item
            mList.add(newAuthor);
            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.authorList.scrollToPosition(mListAdapter.getItemCount() - 1);

            // clear the form for next entry
            mVb.author.setText("");
            mVb.author.requestFocus();
        }
    }

    private boolean saveChanges() {
        if (!mVb.author.getText().toString().isEmpty()) {
            // Discarding applies to the edit field(s) only. The list itself is still saved.
            //noinspection ConstantConditions
            StandardDialogs.unsavedEdits(getContext(), null, () -> {
                mVb.author.setText("");
                if (saveChanges()) {
                    dismiss();
                }
            });
            return false;
        }

        mVm.updateAuthors(mList);
        return true;
    }

    /**
     * Process the modified (if any) data.
     *
     * @param original the original data the user was editing
     * @param modified the modifications the user made in a placeholder object.
     *                 Non-modified data was copied here as well.
     */
    private void processChanges(@NonNull final Author original,
                                @NonNull final Author modified) {

        // name not changed ?
        if (original.getFamilyName().equals(modified.getFamilyName())
            && original.getGivenNames().equals(modified.getGivenNames())) {
            // copy the completion state, we don't have to warn/ask the user about it.
            original.setComplete(modified.isComplete());

            // Type is not part of the Author table, but of the book_author table.
            if (original.getType() != modified.getType()) {
                // so if the type is different, just update it
                original.setType(modified.getType());
                //noinspection ConstantConditions
                mVm.pruneAuthors(getContext());
                mListAdapter.notifyDataSetChanged();
            }
            return;
        }

        // The name was modified. Check if it's used by any other books.
        //noinspection ConstantConditions
        if (mVm.isSingleUsage(getContext(), original)) {
            // If it's not, we can simply modify the old object and we're done here.
            // There is no need to consult the user.
            // Copy the new data into the original object that the user was changing.
            original.copyFrom(modified, true);
            //noinspection ConstantConditions
            mVm.pruneAuthors(getContext());
            mListAdapter.notifyDataSetChanged();
            return;
        }

        // At this point, we know the object was modified and it's used in more than one place.
        // We need to ask the user if they want to make the changes globally.
        StandardDialogs.confirmScopeForChange(
                getContext(), original, modified,
                () -> changeForAllBooks(original, modified),
                () -> changeForThisBook(original, modified));
    }

    private void changeForAllBooks(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // copy all new data
        original.copyFrom(modified, true);
        // This change is done in the database right NOW!
        //noinspection ConstantConditions
        if (mVm.changeForAllBooks(getContext(), original)) {
            mListAdapter.notifyDataSetChanged();

        } else {
            Logger.error(getContext(), TAG, new Throwable(), "Could not update",
                         "original=" + original, "modified=" + modified);
            StandardDialogs.showError(getContext(), R.string.error_storage_not_writable);
        }
    }

    private void changeForThisBook(@NonNull final Author original,
                                   @NonNull final Author modified) {
        // treat the new data as a new Author; save it so we have a valid id.
        // Note that if the user abandons the entire book edit,
        // we will orphan this new Author. That's ok, it will get
        // garbage collected from the database sooner or later.
        //noinspection ConstantConditions
        mVm.changeForThisBook(getContext(), original, modified);
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
    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

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
        private static final String TAG = "EditAuthorForBookDialog";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";
        /**
         * We create a list of all the Type checkboxes for easy handling.
         * The key is the Type.
         */
        private final SparseArray<CompoundButton> mTypeButtons = new SparseArray<>();
        /** FragmentResultListener request key to use for our response. */
        private String mRequestKey;
        private EditBookFragmentViewModel mVm;
        /** Displayed for info only. */
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
         * No-arg constructor for OS use.
         */
        public EditAuthorForBookDialogFragment() {
            super(R.layout.dialog_edit_book_author);
            setForceFullscreen();
        }

        /**
         * Launch the dialog.
         *
         * @param fm         The FragmentManager this fragment will be added to.
         * @param requestKey for use with the FragmentResultListener
         * @param bookTitle  displayed for info only
         * @param author     to edit
         */
        static void launch(@NonNull final FragmentManager fm,
                           @SuppressWarnings("SameParameterValue")
                           @NonNull final String requestKey,
                           @NonNull final String bookTitle,
                           @NonNull final Author author) {
            final Bundle args = new Bundle(3);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(DBDefinitions.KEY_TITLE, bookTitle);
            args.putParcelable(DBDefinitions.KEY_FK_AUTHOR, author);

            final DialogFragment frag = new EditAuthorForBookDialogFragment();
            frag.setArguments(args);
            frag.show(fm, TAG);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                 "BKEY_REQUEST_KEY");
            mAuthor = Objects.requireNonNull(args.getParcelable(DBDefinitions.KEY_FK_AUTHOR),
                                             "KEY_FK_AUTHOR");

            mBookTitle = args.getString(DBDefinitions.KEY_TITLE);

            if (savedInstanceState == null) {
                mFamilyName = mAuthor.getFamilyName();
                mGivenNames = mAuthor.getGivenNames();
                mIsComplete = mAuthor.isComplete();
                mType = mAuthor.getType();
            } else {
                //noinspection ConstantConditions
                mFamilyName = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_FAMILY_NAME);
                //noinspection ConstantConditions
                mGivenNames = savedInstanceState.getString(DBDefinitions.KEY_AUTHOR_GIVEN_NAMES);
                mIsComplete = savedInstanceState.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                            false);
                mType = savedInstanceState.getInt(DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
            }
        }

        @Override
        public void onViewCreated(@NonNull final View view,
                                  @Nullable final Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            //noinspection ConstantConditions
            final SharedPreferences global = PreferenceManager
                    .getDefaultSharedPreferences(getContext());

            mVb = DialogEditBookAuthorBinding.bind(view);
            mVb.toolbar.setSubtitle(mBookTitle);

            //noinspection ConstantConditions
            mVm = new ViewModelProvider(getActivity()).get(EditBookFragmentViewModel.class);

            final DiacriticArrayAdapter<String> familyNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mVm.getAllAuthorFamilyNames());
            mVb.familyName.setText(mFamilyName);
            mVb.familyName.setAdapter(familyNameAdapter);

            final DiacriticArrayAdapter<String> givenNameAdapter = new DiacriticArrayAdapter<>(
                    getContext(), R.layout.dropdown_menu_popup_item,
                    mVm.getAllAuthorGivenNames());
            mVb.givenNames.setText(mGivenNames);
            mVb.givenNames.setAdapter(givenNameAdapter);

            mVb.cbxIsComplete.setChecked(mIsComplete);

            final boolean useAuthorType =
                    DBDefinitions.isUsed(global, DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK);
            mVb.authorTypeGroup.setVisibility(useAuthorType ? View.VISIBLE : View.GONE);
            if (useAuthorType) {
                mVb.btnUseAuthorType.setOnCheckedChangeListener(
                        (v, isChecked) -> setTypeEnabled(isChecked));

                // NEWTHINGS: author type: add a button to the layout
                mTypeButtons.put(Author.TYPE_WRITER, mVb.cbxAuthorTypeWriter);
                mTypeButtons.put(Author.TYPE_CONTRIBUTOR, mVb.cbxAuthorTypeContributor);
                mTypeButtons.put(Author.TYPE_INTRODUCTION, mVb.cbxAuthorTypeIntro);
                mTypeButtons.put(Author.TYPE_TRANSLATOR, mVb.cbxAuthorTypeTranslator);
                mTypeButtons.put(Author.TYPE_EDITOR, mVb.cbxAuthorTypeEditor);
                mTypeButtons.put(Author.TYPE_NARRATOR, mVb.cbxAuthorTypeNarrator);

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

        @Override
        protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
            if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
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

            EditBookBaseFragment.EditItemLauncher
                    .sendResult(this, mRequestKey, mAuthor, tmpAuthor);
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
            final Holder holder = new Holder(view);
            // click -> edit
            holder.rowDetailsView.setOnClickListener(v -> EditAuthorForBookDialogFragment
                    .launch(getChildFragmentManager(), RK_EDIT_AUTHOR,
                            mVm.getBook().getTitle(),
                            getItem(holder.getBindingAdapterPosition())));
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final Author author = getItem(position);
            mFormatter.apply(author, holder.authorView);
        }
    }
}
