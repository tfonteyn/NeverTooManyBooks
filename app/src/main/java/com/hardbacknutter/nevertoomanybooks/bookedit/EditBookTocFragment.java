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

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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

import com.hardbacknutter.nevertoomanybooks.FragmentLauncherBase;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogTocConfirmBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.EntityStage;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.tasks.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
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
    private static final String RK_EDIT_TOC = TAG + ":rk:" + EditTocEntryDialogFragment.TAG;
    /** FragmentResultListener request key. */
    private static final String RK_CONFIRM_TOC = TAG + ":rk:" + ConfirmTocDialogFragment.TAG;

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mVm.getBook().setStage(EntityStage.Stage.Dirty);
                }
            };
    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @NonNull
    private final List<Edition> mIsfdbEditions = new ArrayList<>();
    /** the rows. A reference to the parcelled list in the Book. */
    private ArrayList<TocEntry> mList;
    /** View Binding. */
    private FragmentEditBookTocBinding mVb;
    /** The adapter for the list. */
    private TocListEditAdapter mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;
    private ExtArrayAdapter<String> mAuthorAdapter;
    /**
     * Stores the item position in the list while we're editing that item.
     * Editing is done using a dialog, so no need to store it more permanently.
     */
    @Nullable
    private Integer mEditPosition;

    /** Listen for the results of the entry edit-dialog. */
    private final EditTocEntryDialogFragment.Launcher mEditTocEntryLauncher =
            new EditTocEntryDialogFragment.Launcher(RK_EDIT_TOC) {
                @Override
                public void onResult(@NonNull final TocEntry tocEntry,
                                     final boolean isAnthology) {
                    onEntryUpdated(tocEntry, isAnthology);
                }
            };

    private EditBookTocViewModel mEditTocVm;
    private final ConfirmTocDialogFragment.Launcher mConfirmTocResultsLauncher =
            new ConfirmTocDialogFragment.Launcher(RK_CONFIRM_TOC) {
                @Override
                public void onResult(@NonNull final Book.ContentType contentType,
                                     @NonNull final List<TocEntry> tocEntries) {
                    onIsfdbDataConfirmed(contentType, tocEntries);
                }

                @Override
                public void searchNextEdition() {
                    searchIsfdb();
                }
            };
    private ExtPopupMenu mContextMenu;

    @NonNull
    @Override
    public String getFragmentId() {
        return TAG;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mEditTocVm = new ViewModelProvider(this).get(EditBookTocViewModel.class);

        final FragmentManager fm = getChildFragmentManager();

        mEditTocEntryLauncher.registerForFragmentResult(fm, this);
        mConfirmTocResultsLauncher.registerForFragmentResult(fm, this);
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

        mEditTocVm.onIsfdbEditions().observe(getViewLifecycleOwner(), this::onIsfdbEditions);
        mEditTocVm.onIsfdbBook().observe(getViewLifecycleOwner(), this::onIsfdbBook);

        mEditTocVm.onIsfdbEditionsCancelled().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mEditTocVm.onIsfdbEditionsFailure().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_no_editions,
                              Snackbar.LENGTH_LONG).show();
            }
        });

        mEditTocVm.onIsfdbBookCancelled().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.cancelled, Snackbar.LENGTH_LONG)
                        .show();
            }
        });
        mEditTocVm.onIsfdbBookFailure().observe(getViewLifecycleOwner(), message -> {
            if (message.isNewEvent()) {
                Snackbar.make(mVb.getRoot(), R.string.warning_search_failed,
                              Snackbar.LENGTH_LONG).show();
            }
        });

        //noinspection ConstantConditions
        mVb.tocList.addItemDecoration(
                new DividerItemDecoration(getContext(), RecyclerView.VERTICAL));
        mVb.tocList.setHasFixedSize(true);

        mList = mVm.getBook().getToc();
        mListAdapter = new TocListEditAdapter(getContext(), mList,
                                              vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.tocList.setAdapter(mListAdapter);

        final SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.tocList);

        mVb.rbIsCollection.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });
        mVb.rbIsAnthology.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });
        mVb.rbIsNeither.setOnClickListener(v -> {
            mVm.getBook().setStage(EntityStage.Stage.Dirty);
            updateAnthology();
        });

        mVb.btnAdd.setOnClickListener(v -> onAdd());

        final Resources res = getResources();
        final Menu menu = ExtPopupMenu.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT, res.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.action_edit_ellipsis)
            .setIcon(R.drawable.ic_baseline_edit_24);
        menu.add(Menu.NONE, R.id.MENU_DELETE, res.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.action_delete)
            .setIcon(R.drawable.ic_baseline_delete_24);

        mContextMenu = new ExtPopupMenu(getContext(), menu, this::onContextItemSelected);

        // ready for user input
        if (mVb.author.getVisibility() == View.VISIBLE) {
            mVb.author.requestFocus();
        } else {
            mVb.title.requestFocus();
        }
    }

    private void onEntryUpdated(@NonNull final TocEntry tocEntry,
                                final boolean isAnthology) {
        if (isAnthology) {
            mVb.rbIsAnthology.setChecked(true);
        }
        updateAnthology();

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
        // no fields as such in this fragment
    }

    @Override
    void onPopulateViews(@NonNull final Fields fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        populateTocBits(book.getContentType());

        // hide unwanted fields
        //noinspection ConstantConditions
        fields.setVisibility(getView(), false, false);
    }

    private void populateTocBits(@NonNull final Book.ContentType contentType) {
        switch (contentType) {
            case Collection:
                mVb.rbIsCollection.setChecked(true);
                break;

            case Anthology:
                mVb.rbIsAnthology.setChecked(true);
                break;

            case Book:
            default:
                mVb.rbIsNeither.setChecked(true);
                break;
        }

        updateAnthology();
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        if (mVb.rbIsCollection.isChecked()) {
            book.setContentType(Book.ContentType.Collection);
        } else if (mVb.rbIsAnthology.isChecked()) {
            book.setContentType(Book.ContentType.Anthology);
        } else {
            book.setContentType(Book.ContentType.Book);
        }
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
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.isfdb_menu_populate_toc);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_POPULATE_TOC_FROM_ISFDB) {
            final Book book = mVm.getBook();
            final long isfdbId = book.getLong(DBKey.SID_ISFDB);
            if (isfdbId != 0) {
                Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                mEditTocVm.searchBook(isfdbId);
                return true;
            }

            final String isbnStr = book.getString(DBKey.KEY_ISBN);
            if (!isbnStr.isEmpty()) {
                final ISBN isbn = ISBN.createISBN(isbnStr);
                if (isbn.isValid(true)) {
                    Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    mEditTocVm.searchByIsbn(isbn);
                    return true;
                }
            }
            Snackbar.make(mVb.getRoot(), R.string.warning_requires_isbn,
                          Snackbar.LENGTH_LONG).show();
            return false;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          final int position) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.MENU_EDIT) {
            editEntry(position);
            return true;

        } else if (itemId == R.id.MENU_DELETE) {
            deleteEntry(position);
            return true;
        }
        return false;
    }

    /**
     * Start the fragment dialog to edit an entry.
     *
     * @param position the position of the item
     */
    private void editEntry(final int position) {
        mEditPosition = position;

        final TocEntry tocEntry = mList.get(position);
        mEditTocEntryLauncher.launch(mVm.getBook(), tocEntry, mVb.rbIsAnthology.isChecked());
    }

    /**
     * Prompt the user to delete the given item.
     *
     * @param position the position of the item
     */
    private void deleteEntry(final int position) {
        final TocEntry tocEntry = mList.get(position);
        if (tocEntry.getId() != 0) {
            //noinspection ConstantConditions
            StandardDialogs.deleteTocEntry(getContext(),
                                           tocEntry.getTitle(),
                                           tocEntry.getPrimaryAuthor(), () -> {
                        if (mVm.deleteTocEntry(getContext(), tocEntry)) {
                            mList.remove(tocEntry);
                            mListAdapter.notifyItemRemoved(position);
                        }
                    });
        } else {
            mList.remove(tocEntry);
            mListAdapter.notifyItemRemoved(position);
        }
    }

    /**
     * If the radiobutton 'anthology' is on, then we need to show the Author field.
     * Otherwise, hide it.
     */
    private void updateAnthology() {
        if (mVb.rbIsAnthology.isChecked()) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new ExtArrayAdapter<>(
                        getContext(), R.layout.dropdown_menu_popup_item,
                        ExtArrayAdapter.FilterType.Diacritic, mVm.getAllAuthorNames());
                mVb.author.setAdapter(mAuthorAdapter);
            }

            //noinspection ConstantConditions
            final Author author = mVm.getPrimaryAuthor(getContext());
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
        if (mVb.rbIsAnthology.isChecked()) {
            author = Author.from(mVb.author.getText().toString().trim());
        } else {
            //noinspection ConstantConditions
            author = mVm.getPrimaryAuthor(getContext());
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
        mVm.fixId(getContext(), tocEntry);
        // and check it's not already in the list.
        if (mList.contains(tocEntry)) {
            mVb.lblTitle.setError(getString(R.string.warning_already_in_list));
        } else {
            mList.add(tocEntry);
            // clear the form for next entry and scroll to the new item
            if (mVb.rbIsAnthology.isChecked()) {
                final Author author = mVm.getPrimaryAuthor(getContext());
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
            // Stores the urls locally as the user might want to try the next in line
            mIsfdbEditions.clear();
            final List<Edition> result = message.getResult();
            if (result != null) {
                mIsfdbEditions.addAll(result);
            }
            searchIsfdb();
        }
    }

    private void onIsfdbBook(@NonNull final FinishedMessage<Bundle> message) {
        if (message.isNewEvent()) {
            final Bundle result = message.getResult();
            if (result == null) {
                Snackbar.make(mVb.getRoot(), R.string.warning_book_not_found,
                              Snackbar.LENGTH_LONG).show();
                return;
            }

            final Book book = mVm.getBook();

            // update the book with Series information that was gathered from the TOC
            final List<Series> series = result.getParcelableArrayList(Book.BKEY_SERIES_LIST);
            if (series != null && !series.isEmpty()) {
                final ArrayList<Series> inBook = book.getSeries();
                // add, weeding out duplicates
                for (final Series s : series) {
                    if (!inBook.contains(s)) {
                        inBook.add(s);
                    }
                }
            }

            // update the book with the first publication date that was gathered from the TOC
            final String bookFirstPublication = result.getString(DBKey.DATE_FIRST_PUBLICATION);
            if (bookFirstPublication != null) {
                if (book.getString(DBKey.DATE_FIRST_PUBLICATION).isEmpty()) {
                    book.putString(DBKey.DATE_FIRST_PUBLICATION, bookFirstPublication);
                }
            }

            // finally the TOC itself:  display it for the user to approve
            // If there are more editions, the neutral button will allow to fetch the next one.
            mConfirmTocResultsLauncher.launch(result, !mIsfdbEditions.isEmpty());
        }
    }

    private void onIsfdbDataConfirmed(@NonNull final Book.ContentType contentType,
                                      @NonNull final Collection<TocEntry> tocEntries) {
        if (contentType != Book.ContentType.Book) {
            final Book book = mVm.getBook();
            book.setContentType(contentType);
            populateTocBits(book.getContentType());
        }

        // append the new data
        // can create duplicates if the user mixes manual input with automatic (or 2 x automatic...)
        // They will get weeded out when saved to the DAO
        mList.addAll(tocEntries);
        mListAdapter.notifyDataSetChanged();
    }

    private void searchIsfdb() {
        if (mIsfdbEditions.isEmpty()) {
            Snackbar.make(mVb.getRoot(), R.string.warning_no_editions,
                          Snackbar.LENGTH_LONG).show();
        } else {
            Snackbar.make(mVb.getRoot(), R.string.progress_msg_connecting,
                          Snackbar.LENGTH_LONG).show();
            mEditTocVm.searchEdition(mIsfdbEditions.get(0));
            mIsfdbEditions.remove(0);
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
        private Book.ContentType mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY),
                                                 "BKEY_REQUEST_KEY");
            mTocEntries = Objects.requireNonNull(args.getParcelableArrayList(Book.BKEY_TOC_LIST),
                                                 "BKEY_TOC_LIST");

            mTocBitMask = Book.ContentType.getType(args.getLong(DBKey.BITMASK_TOC));
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
                            .setIcon(R.drawable.ic_baseline_warning_24)
                            .setView(vb.getRoot())
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 (d, which) -> Launcher.setResult(this, mRequestKey,
                                                                  mTocBitMask, mTocEntries));
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (mHasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.action_retry),
                                 (d, which) -> Launcher.searchNextEdition(this, mRequestKey));
            }

            return dialog;
        }

        public abstract static class Launcher
                extends FragmentLauncherBase {

            private static final String SEARCH_NEXT_EDITION = "searchNextEdition";
            private static final String TOC_BIT_MASK = "tocBitMask";
            private static final String TOC_LIST = "tocEntries";

            public Launcher(@NonNull final String requestKey) {
                super(requestKey);
            }

            static void setResult(@NonNull final Fragment fragment,
                                  @NonNull final String requestKey,
                                  @NonNull final Book.ContentType tocBitMask,
                                  @NonNull final ArrayList<TocEntry> tocEntries) {
                final Bundle result = new Bundle(2);
                result.putLong(TOC_BIT_MASK, tocBitMask.value);
                result.putParcelableArrayList(TOC_LIST, tocEntries);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            static void searchNextEdition(@NonNull final Fragment fragment,
                                          @NonNull final String requestKey) {
                final Bundle result = new Bundle(1);
                result.putBoolean(SEARCH_NEXT_EDITION, true);
                fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
            }

            /**
             * Launch the dialog.
             *
             * @param bookData         the result of the search
             * @param hasOtherEditions flag
             */
            public void launch(@NonNull final Bundle bookData,
                               final boolean hasOtherEditions) {

                bookData.putString(BKEY_REQUEST_KEY, RK_CONFIRM_TOC);
                bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);

                final DialogFragment frag = new ConfirmTocDialogFragment();
                frag.setArguments(bookData);
                frag.show(mFragmentManager, TAG);
            }

            @Override
            public void onFragmentResult(@NonNull final String requestKey,
                                         @NonNull final Bundle result) {
                if (result.getBoolean(SEARCH_NEXT_EDITION)) {
                    searchNextEdition();
                } else {
                    onResult(Book.ContentType.getType(result.getLong(TOC_BIT_MASK)),
                             Objects.requireNonNull(result.getParcelableArrayList(TOC_LIST)));
                }
            }

            /**
             * Callback handler.
             *
             * @param contentType bit flags
             * @param tocEntries  the list of entries
             */
            public abstract void onResult(@NonNull final Book.ContentType contentType,
                                          @NonNull List<TocEntry> tocEntries);

            /**
             * Callback handler.
             */
            public abstract void searchNextEdition();
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
                mContextMenu.showAsDropDown(v, holder.getBindingAdapterPosition());
                return true;
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final TocEntry tocEntry = getItem(position);

            holder.titleView.setText(tocEntry.getTitle());
            holder.authorView.setText(tocEntry.getPrimaryAuthor().getLabel(getContext()));

            final PartialDate date = tocEntry.getFirstPublicationDate();
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
