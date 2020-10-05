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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
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
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogTocConfirmBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetEditionsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ItemTouchHelperViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * The ISFDB direct interaction should be seen as temporary as this class
 * should not have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a
 * single book, and hence a single TOC. The interaction here with ISFDB allows
 * the user to reject the first (book)TOC found, and get the next one (etc...).
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** Log tag. */
    private static final String TAG = "EditBookTocFragment";

    /** FragmentResultListener request key. */
    private static final String RK_MENU_PICKER = MenuPickerDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_EDIT_TOC = EditTocEntryDialogFragment.TAG + ":rk";
    /** FragmentResultListener request key. */
    private static final String RK_CONFIRM_TOC = ConfirmTocDialogFragment.TAG + ":rk";

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookViewModel.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @NonNull
    private final List<Edition> mIsfdbEditions = new ArrayList<>();
    /** the rows. */
    private ArrayList<TocEntry> mList;
    /** View Binding. */
    private FragmentEditBookTocBinding mVb;
    /** The adapter for the list. */
    private TocListEditAdapter mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    private DiacriticArrayAdapter<String> mAuthorAdapter;
    /** Stores the item position in the list while we're editing that item. */
    @Nullable
    private Integer mEditPosition;
    /** Listen for the results of the entry edit-dialog. */
    private final BookChangedListener mOnBookChangedListener = (bookId, fieldChanges, data) -> {
        Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);

        if ((fieldChanges & BookChangedListener.TOC_ENTRY) != 0) {
            final TocEntry tocEntry = data
                    .getParcelable(EditTocEntryDialogFragment.BKEY_TOC_ENTRY);
            Objects.requireNonNull(tocEntry, ErrorMsg.NULL_TOC_ENTRY);
            final boolean multipleAuthors = data
                    .getBoolean(EditTocEntryDialogFragment.BKEY_HAS_MULTIPLE_AUTHORS);

            onEntryUpdated(tocEntry, multipleAuthors);

        } else {
            // we don't expect/implement any others.
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "bookId=" + bookId + "|fieldChanges=" + fieldChanges);
            }
        }

    };

    private IsfdbGetEditionsTask mIsfdbGetEditionsTask;
    private IsfdbGetBookTask mIsfdbGetBookTask;

    private final FragmentResultListener mConfirmTocResultsListener =
            new ConfirmTocDialogFragment.OnResultListener() {
                @Override
                public void onResult(@Book.TocBits final long tocBitMask,
                                     @NonNull final List<TocEntry> tocEntries) {
                    onIsfdbDataConfirmed(tocBitMask, tocEntries);
                }

                @Override
                public void searchNextEdition() {
                    searchIsfdb();
                }
            };

    @NonNull
    @Override
    public String getFragmentId() {
        return TAG;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FragmentManager fm = getChildFragmentManager();
        fm.setFragmentResultListener(RK_EDIT_TOC, this, mOnBookChangedListener);
        fm.setFragmentResultListener(RK_CONFIRM_TOC, this, mConfirmTocResultsListener);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            fm.setFragmentResultListener(
                    RK_MENU_PICKER, this,
                    (MenuPickerDialogFragment.OnResultListener) this::onContextItemSelected);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookTocBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        // setup common stuff and calls onInitFields()
        super.onViewCreated(view, savedInstanceState);

        mIsfdbGetEditionsTask = new ViewModelProvider(this).get(IsfdbGetEditionsTask.class);
        mIsfdbGetEditionsTask.onCancelled().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mIsfdbGetEditionsTask.onFailure().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_no_editions,
                              Snackbar.LENGTH_LONG).show();
            }
        });
        mIsfdbGetEditionsTask.onFinished().observe(getViewLifecycleOwner(), this::onIsfdbEditions);

        mIsfdbGetBookTask = new ViewModelProvider(this).get(IsfdbGetBookTask.class);
        mIsfdbGetBookTask.onCancelled().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_task_cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mIsfdbGetBookTask.onFailure().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_search_failed,
                              Snackbar.LENGTH_LONG).show();
            }
        });
        mIsfdbGetBookTask.onFinished().observe(getViewLifecycleOwner(), this::onIsfdbBook);

        //noinspection ConstantConditions
        mVb.tocList.addItemDecoration(
                new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.tocList.setHasFixedSize(true);

        mList = mBookViewModel.getBook().getParcelableArrayList(Book.BKEY_TOC_LIST);
        mListAdapter = new TocListEditAdapter(getContext(), mList,
                                              vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.tocList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.tocList);

        mVb.cbxMultipleAuthors.setOnCheckedChangeListener(
                (v, isChecked) -> updateMultiAuthor(isChecked));
        // adding a new entry
        mVb.btnAdd.setOnClickListener(v -> onAdd());

        // ready for user input
        if (mVb.author.getVisibility() == View.VISIBLE) {
            mVb.author.requestFocus();
        } else {
            mVb.title.requestFocus();
        }
    }

    private void onEntryUpdated(@NonNull final TocEntry tocEntry,
                                final boolean hasMultipleAuthors) {
        updateMultiAuthor(hasMultipleAuthors);

        if (mEditPosition == null) {
            // It's a new entry for the list.
            addNewEntry(tocEntry);

        } else {
            // It's an existing entry in the list, find it and update with the new data
            final TocEntry original = mList.get(mEditPosition);
            original.copyFrom(tocEntry);
            mListAdapter.notifyItemChanged(mEditPosition);
        }
    }

    @Override
    void onInitFields(@NonNull final Fields fields) {

    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        populateTocBits(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        // Combine the separate checkboxes into the single field.
        book.setBit(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS,
                    mVb.cbxIsAnthology.isChecked());
        book.setBit(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_AUTHORS,
                    mVb.cbxMultipleAuthors.isChecked());
    }

    @Override
    public boolean hasUnfinishedEdits() {
        // We only check the title field; disregarding the author and first-publication fields.
        //noinspection ConstantConditions
        return !mVb.title.getText().toString().isEmpty();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.isfdb_menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB: {
                final Book book = mBookViewModel.getBook();
                final long isfdbId = book.getLong(DBDefinitions.KEY_EID_ISFDB);
                if (isfdbId != 0) {
                    Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    mIsfdbGetBookTask.search(isfdbId);
                    return true;
                }

                final String isbnStr = book.getString(DBDefinitions.KEY_ISBN);
                if (!isbnStr.isEmpty()) {
                    final ISBN isbn = ISBN.createISBN(isbnStr);
                    if (isbn.isValid(true)) {
                        Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                                      Snackbar.LENGTH_LONG).show();
                        mIsfdbGetEditionsTask.search(isbn);
                        return true;
                    }
                }
                Snackbar.make(mVb.getRoot(), R.string.warning_requires_isbn,
                              Snackbar.LENGTH_LONG).show();
                return false;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        final Resources res = getResources();
        final TocEntry item = mList.get(position);
        //noinspection ConstantConditions
        final String title = item.getLabel(getContext());

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();
            menu.add(new MenuPickerDialogFragment.Pick(
                    R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                    getString(R.string.action_edit_ellipsis),
                    R.drawable.ic_edit));
            menu.add(new MenuPickerDialogFragment.Pick(
                    R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                    getString(R.string.action_delete),
                    R.drawable.ic_delete));

            MenuPickerDialogFragment.newInstance(RK_MENU_PICKER, title, menu, position)
                                    .show(getChildFragmentManager(), MenuPickerDialogFragment.TAG);
        } else {
            final Menu menu = MenuPicker.createMenu(getContext());
            menu.add(Menu.NONE, R.id.MENU_EDIT,
                     res.getInteger(R.integer.MENU_ORDER_EDIT),
                     R.string.action_edit_ellipsis)
                .setIcon(R.drawable.ic_edit);
            menu.add(Menu.NONE, R.id.MENU_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.ic_delete);

            new MenuPicker(getContext(), title, menu, position, this::onContextItemSelected)
                    .show();
        }
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {
        switch (menuItem) {
            case R.id.MENU_EDIT:
                editEntry(position);
                return true;

            case R.id.MENU_DELETE:
                deleteEntry(position);
                return true;

            default:
                return false;
        }
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param position the position of the item
     */
    private void editEntry(final int position) {
        mEditPosition = position;

        final TocEntry tocEntry = mList.get(position);
        EditTocEntryDialogFragment
                .newInstance(RK_EDIT_TOC, mBookViewModel.getBook(),
                             tocEntry, mVb.cbxMultipleAuthors.isChecked())
                .show(getChildFragmentManager(), EditTocEntryDialogFragment.TAG);
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final TocEntry tocEntry = mList.get(position);
        //noinspection ConstantConditions
        StandardDialogs.deleteTocEntry(getContext(),
                                       tocEntry.getTitle(),
                                       tocEntry.getPrimaryAuthor(), () -> {
                    if (mFragmentVM.deleteTocEntry(getContext(), tocEntry)) {
                        mList.remove(tocEntry);
                        mListAdapter.notifyItemRemoved(position);
                    }
                });
    }

    private void populateTocBits(@NonNull final Book book) {
        mVb.cbxIsAnthology.setChecked(book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                                    Book.TOC_MULTIPLE_WORKS));
        updateMultiAuthor(book.isBitSet(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_AUTHORS));
    }

    private void updateMultiAuthor(final boolean isChecked) {
        mVb.cbxMultipleAuthors.setChecked(isChecked);
        if (isChecked) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new DiacriticArrayAdapter<>(
                        getContext(), R.layout.dropdown_menu_popup_item,
                        mFragmentVM.getAllAuthorNames());
                mVb.author.setAdapter(mAuthorAdapter);
            }

            //noinspection ConstantConditions
            final Author author = mBookViewModel.getPrimaryAuthor(getContext());
            mVb.author.setText(author.getLabel(getContext()));
            mVb.author.selectAll();
            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);
            mVb.author.requestFocus();
        } else {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
            mVb.title.requestFocus();
        }
    }

    /**
     * Add a new entry to the list based on the on-screen fields. (i.e. not from the edit-dialog).
     */
    private void onAdd() {
        // clear any previous error
        mVb.lblTitle.setError(null);

        //noinspection ConstantConditions
        final String title = mVb.title.getText().toString().trim();
        if (title.isEmpty()) {
            mVb.title.setError(getString(R.string.vldt_non_blank_required));
            return;
        }

        final Author author;
        if (mVb.cbxMultipleAuthors.isChecked()) {
            author = Author.from(mVb.author.getText().toString().trim());
        } else {
            //noinspection ConstantConditions
            author = mBookViewModel.getPrimaryAuthor(getContext());
        }
        //noinspection ConstantConditions
        final TocEntry newTocEntry = new TocEntry(author,
                                                  mVb.title.getText().toString().trim(),
                                                  mVb.firstPublication.getText().toString().trim());
        addNewEntry(newTocEntry);
    }

    /**
     * Add a new entry to the list.
     * Called either by {@link #onAdd} or from the edit-dialog listener.
     *
     * @param tocEntry to add
     */
    private void addNewEntry(@NonNull final TocEntry tocEntry) {
        // see if it already exists
        //noinspection ConstantConditions
        mBookViewModel.fixTocEntryId(getContext(), tocEntry);
        // and check it's not already in the list.
        if (mList.contains(tocEntry)) {
            mVb.lblTitle.setError(getString(R.string.warning_already_in_list));
        } else {
            mList.add(tocEntry);
            // clear the form for next entry and scroll to the new item
            if (mVb.cbxMultipleAuthors.isChecked()) {
                final Author author = mBookViewModel.getPrimaryAuthor(getContext());
                mVb.author.setText(author.getLabel(getContext()));
                mVb.author.selectAll();
            }
            mVb.title.setText("");
            mVb.firstPublication.setText("");
            mVb.title.requestFocus();

            mListAdapter.notifyItemInserted(mList.size() - 1);
            mVb.tocList.scrollToPosition(mListAdapter.getItemCount() - 1);
        }
    }

    /**
     * We got one or more editions from ISFDB.
     */
    private void onIsfdbEditions(@NonNull final FinishedMessage<List<Edition>> message) {
        if (message.isNewEvent()) {
            // Stores the url's locally as the user might want to try the next in line
            mIsfdbEditions.clear();
            if (message.result != null) {
                mIsfdbEditions.addAll(message.result);
            }
            searchIsfdb();
        }
    }

    private void onIsfdbBook(@NonNull final FinishedMessage<Bundle> message) {
        if (message.isNewEvent()) {
            if (message.result == null) {
                Snackbar.make(mVb.getRoot(), R.string.warning_book_not_found,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            final Book book = mBookViewModel.getBook();

            // update the book with Series information that was gathered from the TOC
            final List<Series> series =
                    message.result.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            if (series != null && !series.isEmpty()) {
                final ArrayList<Series> inBook =
                        book.getParcelableArrayList(Book.BKEY_SERIES_LIST);
                // add, weeding out duplicates
                for (Series s : series) {
                    if (!inBook.contains(s)) {
                        inBook.add(s);
                    }
                }
            }

            // update the book with the first publication date that was gathered from the TOC
            final String bookFirstPublication =
                    message.result.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION);
            if (bookFirstPublication != null) {
                if (book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION).isEmpty()) {
                    book.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, bookFirstPublication);
                }
            }

            // finally the TOC itself:  display it for the user to approve
            // If there are more editions, the neutral button will allow to fetch the next one.
            ConfirmTocDialogFragment
                    .newInstance(RK_CONFIRM_TOC, message.result, !mIsfdbEditions.isEmpty())
                    .show(getChildFragmentManager(), ConfirmTocDialogFragment.TAG);
        }
    }

    private void onIsfdbDataConfirmed(@Book.TocBits final long tocBitMask,
                                      @NonNull final Collection<TocEntry> tocEntries) {
        if (tocBitMask != 0) {
            final Book book = mBookViewModel.getBook();
            book.putLong(DBDefinitions.KEY_TOC_BITMASK, tocBitMask);
            populateTocBits(book);
        }

        // append the new data
        // can create duplicates if the user mixes manual input with automatic (or 2 x automatic...)
        // They will get weeded out when saved to the DAO
        mList.addAll(tocEntries);
        mListAdapter.notifyDataSetChanged();
    }

    private void searchIsfdb() {
        if (!mIsfdbEditions.isEmpty()) {
            Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mIsfdbGetBookTask.search(mIsfdbEditions.get(0));
            mIsfdbEditions.remove(0);
        } else {
            Snackbar.make(mVb.getRoot(), R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
        }
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "ConfirmTocDialogFrag";
        private static final String BKEY_REQUEST_KEY = TAG + ":rk";
        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";
        /** FragmentResultListener request key to use for our response. */
        private String mRequestKey;
        private boolean mHasOtherEditions;
        @Book.TocBits
        private long mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        /**
         * Constructor.
         *
         * @param requestKey       for use with the FragmentResultListener
         * @param hasOtherEditions flag
         *
         * @return instance
         */
        static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                          @NonNull final String requestKey,
                                          @NonNull final Bundle bookData,
                                          final boolean hasOtherEditions) {
            final DialogFragment frag = new ConfirmTocDialogFragment();
            bookData.putString(BKEY_REQUEST_KEY, requestKey);
            bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);
            frag.setArguments(bookData);
            return frag;
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = args.getString(BKEY_REQUEST_KEY);
            mTocEntries = args.getParcelableArrayList(Book.BKEY_TOC_LIST);
            Objects.requireNonNull(mTocEntries, ErrorMsg.NULL_TOC_ENTRY);

            mTocBitMask = args.getLong(DBDefinitions.KEY_TOC_BITMASK);
            mHasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            final DialogTocConfirmBinding vb = DialogTocConfirmBinding.inflate(getLayoutInflater());

            final boolean hasToc = mTocEntries != null && !mTocEntries.isEmpty();
            if (hasToc) {
                //noinspection ConstantConditions
                final StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(mTocEntries.stream()
                                                   .map(entry -> entry.getLabel(getContext()))
                                                   .collect(Collectors.joining(", ")));
                vb.content.setText(message);

            } else {
                vb.content.setText(getString(R.string.error_auto_toc_population_failed));
            }

            //noinspection ConstantConditions
            final AlertDialog dialog =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIcon(R.drawable.ic_warning)
                            .setView(vb.getRoot())
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 this::onCommitToc);
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (mHasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_retry),
                                 this::onSearchNextEdition);
            }

            return dialog;
        }

        private void onCommitToc(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                                 @SuppressWarnings("unused") final int which) {
            OnResultListener.sendResult(this, mRequestKey, mTocBitMask, mTocEntries);
        }

        private void onSearchNextEdition(@SuppressWarnings("unused")
                                         @NonNull final DialogInterface d,
                                         @SuppressWarnings("unused") final int which) {
            OnResultListener.searchNextEdition(this, mRequestKey);
        }

        public interface OnResultListener
                extends FragmentResultListener {

            /* private. */ String SEARCH_NEXT_EDITION = "searchNextEdition";
            /* private. */ String TOC_BIT_MASK = "tocBitMask";
            /* private. */ String TOC_LIST = "tocEntries";

            static void searchNextEdition(@NonNull final Fragment fragment,
                                          @NonNull final String requestKey) {
                final Bundle result = new Bundle(1);
                result.putBoolean(SEARCH_NEXT_EDITION, true);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            static void sendResult(@NonNull final Fragment fragment,
                                   @NonNull final String requestKey,
                                   @Book.TocBits long tocBitMask,
                                   @NonNull ArrayList<TocEntry> tocEntries) {
                final Bundle result = new Bundle(2);
                result.putLong(TOC_BIT_MASK, tocBitMask);
                result.putParcelableArrayList(TOC_LIST, tocEntries);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            @Override
            default void onFragmentResult(@NonNull final String requestKey,
                                          @NonNull final Bundle result) {
                if (result.getBoolean(SEARCH_NEXT_EDITION)) {
                    searchNextEdition();
                } else {
                    onResult(result.getLong(TOC_BIT_MASK),
                             Objects.requireNonNull(result.getParcelableArrayList(TOC_LIST)));
                }
            }


            void onResult(@Book.TocBits long tocBitMask,
                          @NonNull List<TocEntry> tocEntries);

            void searchNextEdition();
        }

    }

    /**
     * Holder for each row.
     */
    private static class Holder
            extends ItemTouchHelperViewHolderBase {

        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        @NonNull
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            titleView = rowDetailsView.findViewById(R.id.title);
            authorView = rowDetailsView.findViewById(R.id.author);
            firstPublicationView = rowDetailsView.findViewById(R.id.year);
        }
    }

    private class TocListEditAdapter
            extends RecyclerViewAdapterBase<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of TocEntry's
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        TocListEditAdapter(@NonNull final Context context,
                           @NonNull final List<TocEntry> items,
                           @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_toc_entry, parent, false);
            final Holder holder = new Holder(view);

            // click -> edit
            holder.rowDetailsView.setOnClickListener(
                    v -> editEntry(holder.getBindingAdapterPosition()));

            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
                return true;
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final TocEntry item = getItem(position);

            holder.titleView.setText(item.getTitle());
            holder.authorView.setText(item.getPrimaryAuthor().getLabel(getContext()));

            final PartialDate date = item.getFirstPublicationDate();
            if (date.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                // cut the date to just the year.
                holder.firstPublicationView.setText(getString(R.string.brackets,
                                                              String.valueOf(date.getYearValue())));
            }
        }

        @Override
        protected void onDelete(final int adapterPosition,
                                @NonNull final TocEntry item) {
            deleteEntry(adapterPosition);
        }
    }
}
