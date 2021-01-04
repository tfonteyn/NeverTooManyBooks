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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentAuthorWorksBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookAsWork;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.AttrUtils;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;
import com.hardbacknutter.nevertoomanybooks.viewmodels.AuthorWorksViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfViewModel;
import com.hardbacknutter.nevertoomanybooks.widgets.fastscroller.FastScroller;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 *
 * <strong>Note:</strong> when an item is clicked, we start a <strong>NEW</strong> Activity.
 * Doing a 'back' will then get the user back here.
 * This is intentionally different from the behaviour of {@link SearchFtsFragment}.
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "AuthorWorksFragment";

    /** Optional. Show the TOC. Defaults to {@code true}. */
    public static final String BKEY_WITH_TOC = TAG + ":tocs";
    /** Optional. Show the books. Defaults to {@code true}. */
    public static final String BKEY_WITH_BOOKS = TAG + ":books";

    /** FragmentResultListener request key. */
    private static final String RK_MENU_PICKER = TAG + ":rk:" + MenuPickerDialogFragment.TAG;
    /** The Fragment ViewModel. */
    private AuthorWorksViewModel mVm;
    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, mVm.getResultIntent());
                    getActivity().finish();
                }
            };

    private final MenuPickerDialogFragment.Launcher mMenuLauncher =
            new MenuPickerDialogFragment.Launcher() {
                @Override
                public boolean onResult(@IdRes final int menuItemId,
                                        final int position) {
                    return onContextItemSelected(menuItemId, position);
                }
            };

    /** The Adapter. */
    private TocAdapter mAdapter;
    private ActionBar mActionBar;
    /** View Binding. */
    private FragmentAuthorWorksBinding mVb;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.register(this, RK_MENU_PICKER);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentAuthorWorksBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Context context = getContext();

        // Popup the search widget when the user starts to type.
        //noinspection ConstantConditions
        getActivity().setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), mOnBackPressedCallback);

        mVm = new ViewModelProvider(this).get(AuthorWorksViewModel.class);
        //noinspection ConstantConditions
        mVm.init(context, requireArguments());

        //noinspection ConstantConditions
        mActionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        //noinspection ConstantConditions
        mActionBar.setTitle(mVm.getScreenTitle(context));
        mActionBar.setSubtitle(mVm.getScreenSubtitle());

        mVb.authorWorks.setHasFixedSize(true);
        mVb.authorWorks
                .addItemDecoration(new DividerItemDecoration(context, RecyclerView.VERTICAL));

        FastScroller.attach(mVb.authorWorks);

        mAdapter = new TocAdapter(context);
        mVb.authorWorks.setAdapter(mAdapter);

        if (savedInstanceState == null) {
            TipManager.getInstance().display(context, R.string.tip_authors_works, null);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        inflater.inflate(R.menu.author_works, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        final MenuItem all = menu.findItem(R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES);
        // show if we got here with a specific bookshelf selected.
        // hide if the bookshelf was set to Bookshelf.ALL_BOOKS.
        all.setVisible(mVm.getBookshelfId() != Bookshelf.ALL_BOOKS);

        all.setChecked(mVm.isAllBookshelves());

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_AUTHOR_WORKS_ALL) {
            item.setChecked(true);
            mVm.reloadWorkList(true, true);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_TOC) {
            item.setChecked(true);
            mVm.reloadWorkList(true, false);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_BOOKS) {
            item.setChecked(true);
            mVm.reloadWorkList(false, true);
            mAdapter.notifyDataSetChanged();
            return true;

        } else if (itemId == R.id.MENU_AUTHOR_WORKS_ALL_BOOKSHELVES) {
            final boolean checked = !item.isChecked();
            item.setChecked(checked);
            mVm.setAllBookshelves(checked);
            mVm.reloadWorkList();
            mAdapter.notifyDataSetChanged();
            //noinspection ConstantConditions
            mActionBar.setTitle(mVm.getScreenTitle(getContext()));
            mActionBar.setSubtitle(mVm.getScreenSubtitle());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Create and show a context menu for the given position.
     *
     * @param position in the list
     */
    private void onCreateContextMenu(final int position) {
        final Resources res = getResources();
        final AuthorWork item = mVm.getWorks().get(position);
        //noinspection ConstantConditions
        final String title = item.getLabel(getContext());

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            final ArrayList<MenuPickerDialogFragment.Pick> menu = new ArrayList<>();
            menu.add(new MenuPickerDialogFragment.Pick(R.id.MENU_DELETE,
                                                       res.getInteger(R.integer.MENU_ORDER_DELETE),
                                                       getString(R.string.action_delete),
                                                       R.drawable.ic_delete));
            mMenuLauncher.launch(title, null, menu, position);

        } else {
            final Menu menu = MenuPicker.createMenu(getContext());
            menu.add(Menu.NONE, R.id.MENU_DELETE,
                     res.getInteger(R.integer.MENU_ORDER_DELETE),
                     R.string.action_delete)
                .setIcon(R.drawable.ic_delete);

            new MenuPicker(getContext(), title, null, menu, position, this::onContextItemSelected)
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
        final AuthorWork work = mVm.getWorks().get(position);

        if (menuItem == R.id.MENU_DELETE) {
            deleteWork(position, work);
            return true;
        }
        return false;
    }

    private void deleteWork(final int position,
                            @NonNull final AuthorWork work) {
        if (work instanceof TocEntry) {
            //noinspection ConstantConditions
            StandardDialogs.deleteTocEntry(
                    getContext(), work.getLabel(getContext()),
                    work.getPrimaryAuthor(), () -> {
                        mVm.delete(getContext(), work);
                        mAdapter.notifyItemRemoved(position);
                    });

        } else if (work instanceof BookAsWork) {
            //noinspection ConstantConditions
            StandardDialogs.deleteBook(
                    getContext(), work.getLabel(getContext()),
                    Collections.singletonList(work.getPrimaryAuthor()), () -> {
                        mVm.delete(getContext(), work);
                        mAdapter.notifyItemRemoved(position);
                    });
        } else {
            throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     *
     * @param position in the list
     */
    private void gotoBook(final int position) {
        final AuthorWork work = mVm.getWorks().get(position);

        if (work instanceof TocEntry) {
            final TocEntry tocEntry = (TocEntry) work;
            final ArrayList<Long> bookIdList = mVm.getBookIds(tocEntry);
            if (bookIdList.size() == 1) {
                // open new activity to show the book, 'back' will return to this one.
                final Intent intent = new Intent(getContext(), ShowBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookIdList.get(0));
                startActivity(intent);

            } else {
                // multiple books, open the list as a NEW ACTIVITY
                final Intent intent = new Intent(getContext(), BooksOnBookshelf.class)
                        .putExtra(Book.BKEY_BOOK_ID_LIST, bookIdList)
                        // Open the list expanded, as otherwise you end up with
                        // the author as a single line, and no books shown at all,
                        // which can be quite confusing to the user.
                        .putExtra(BooksOnBookshelfViewModel.BKEY_LIST_STATE,
                                  Booklist.PREF_REBUILD_EXPANDED);

                if (mVm.isAllBookshelves()) {
                    intent.putExtra(BooksOnBookshelfViewModel.BKEY_BOOKSHELF, Bookshelf.ALL_BOOKS);
                }

                startActivity(intent);
            }

        } else if (work instanceof BookAsWork) {
            // open new activity to show the book, 'back' will return to this one.
            final Intent intent = new Intent(getContext(), ShowBookActivity.class)
                    .putExtra(DBDefinitions.KEY_PK_ID, work.getId());
            startActivity(intent);

        } else {
            throw new IllegalArgumentException(String.valueOf(work));
        }
    }

    /**
     * Row ViewHolder for {@link TocAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);
        }
    }

    public static class ResultContract
            extends ActivityResultContract<ResultContract.Input, Bundle> {

        @NonNull
        @Override
        public Intent createIntent(@NonNull final Context context,
                                   @NonNull final ResultContract.Input input) {
            return new Intent(context, FragmentHostActivity.class)
                    .putExtra(FragmentHostActivity.BKEY_FRAGMENT_TAG, AuthorWorksFragment.TAG)
                    .putExtra(DBDefinitions.KEY_PK_ID, input.authorId)
                    .putExtra(DBDefinitions.KEY_FK_BOOKSHELF, input.bookshelfId);
        }

        @Override
        @Nullable
        public Bundle parseResult(final int resultCode,
                                  @Nullable final Intent intent) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                Logger.d(TAG, "parseResult",
                         "|resultCode=" + resultCode + "|intent=" + intent);
            }

            if (intent == null || resultCode != Activity.RESULT_OK) {
                return null;
            }
            return intent.getExtras();
        }

        static class Input {

            final long authorId;
            final long bookshelfId;

            Input(final long authorId,
                  final long bookshelfId) {
                this.authorId = authorId;
                this.bookshelfId = bookshelfId;
            }
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScroller.PopupTextProvider {

        /** Cached inflater. */
        private final LayoutInflater mInflater;
        /** Row indicator icon. */
        private final ColorStateList mDrawableOn;
        /** Row indicator icon. */
        private final ColorStateList mDrawableOff;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        TocAdapter(@NonNull final Context context) {
            super();
            mInflater = LayoutInflater.from(context);

            final Resources.Theme theme = context.getTheme();
            mDrawableOn = context.getResources().getColorStateList(
                    AttrUtils.getResId(context, R.attr.colorControlNormal), theme);
            mDrawableOff = context.getResources().getColorStateList(
                    android.R.color.transparent, theme);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final char type = (char) viewType;
            final View itemView;
            switch (type) {
                case AuthorWork.TYPE_TOC:
                    itemView = mInflater.inflate(R.layout.row_toc_entry, parent, false);
                    break;
                case AuthorWork.TYPE_BOOK:
                    itemView = mInflater.inflate(R.layout.row_toc_entry_book, parent, false);
                    break;
                default:
                    throw new IllegalArgumentException(String.valueOf(viewType));
            }

            final Holder holder = new Holder(itemView);

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(holder.getBindingAdapterPosition()));

            holder.itemView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getBindingAdapterPosition());
                return true;
            });

            return holder;
        }

        @SuppressLint("UseCompatTextViewDrawableApis")
        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final Context context = getContext();

            final AuthorWork work = mVm.getWorks().get(position);
            //noinspection ConstantConditions
            holder.titleView.setText(work.getLabel(context));

            // optional
            if (holder.authorView != null) {
                final Author author = Objects.requireNonNull(work.getPrimaryAuthor(),
                                                             "work.getPrimaryAuthor()");
                holder.authorView.setText(author.getLabel(context));
            }
            // optional
            if (holder.firstPublicationView != null) {
                final PartialDate date = work.getFirstPublicationDate();
                if (date.isEmpty()) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    // screen space is at a premium here, and books can have 'yyyy-mm-dd' dates,
                    // cut the date to just the year.
                    final String fp = context.getString(R.string.brackets,
                                                        String.valueOf(date.getYearValue()));
                    holder.firstPublicationView.setText(fp);
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                }
            }

            if (work instanceof TocEntry) {
                if (work.getBookCount() > 1) {
                    holder.titleView.setCompoundDrawableTintList(mDrawableOn);
                } else {
                    holder.titleView.setCompoundDrawableTintList(mDrawableOff);
                }
            }
        }

        @Override
        public int getItemViewType(final int position) {
            return mVm.getWorks().get(position).getType();
        }

        @Override
        public int getItemCount() {
            return mVm.getWorks().size();
        }

        @NonNull
        @Override
        public String[] getPopupText(final int position) {
            final String title = mVm.getWorks().get(position)
                                    .getLabel(mInflater.getContext());
            return new String[]{title};
        }
    }
}
