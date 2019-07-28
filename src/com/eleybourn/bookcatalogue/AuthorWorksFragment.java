package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.TipManager;
import com.eleybourn.bookcatalogue.dialogs.picker.MenuPicker;
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.viewmodels.AuthorWorksModel;
import com.eleybourn.bookcatalogue.widgets.FastScrollerOverlay;
import com.eleybourn.bookcatalogue.widgets.cfs.CFSRecyclerView;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = "AuthorWorksFragment";

    /** Optional. Also show the authors book. Defaults to {@code true}. */
    public static final String BKEY_WITH_TOCS = TAG + ":withTocEntries";
    public static final String BKEY_WITH_BOOKS = TAG + ":withBooks";

    private AuthorWorksModel mModel;
    private TocAdapter mAdapter;

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_author_works, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mModel = ViewModelProviders.of(getActivity()).get(AuthorWorksModel.class);
        mModel.init(requireArguments());

        String title = mModel.getAuthor().getLabel() + " [" + mModel.getTocEntries().size() + ']';
        getActivity().setTitle(title);

        //noinspection ConstantConditions
        RecyclerView listView = getView().findViewById(android.R.id.list);
        listView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        listView.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        if (!(listView instanceof CFSRecyclerView)) {
            listView.addItemDecoration(
                    new FastScrollerOverlay(getContext(), R.drawable.fast_scroll_overlay));
        }

        mAdapter = new TocAdapter(getContext(), mModel);
        listView.setAdapter(mAdapter);

        TipManager.display(getLayoutInflater(),
                           R.string.tip_authors_works, null);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        menu.add(R.id.MENU_AUTHOR_WORKS, R.id.MENU_AUTHOR_WORKS_ALL, 0,
                 R.string.menu_author_works_all).setChecked(true);

        menu.add(R.id.MENU_AUTHOR_WORKS, R.id.MENU_AUTHOR_WORKS_TOC, 0,
                 R.string.menu_author_works_toc);
        menu.add(R.id.MENU_AUTHOR_WORKS, R.id.MENU_AUTHOR_WORKS_BOOKS, 0,
                 R.string.menu_author_works_book);

        menu.setGroupCheckable(R.id.MENU_AUTHOR_WORKS, true, true);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        switch (item.getItemId()) {
            case R.id.MENU_AUTHOR_WORKS_ALL:
                item.setChecked(true);
                mModel.loadTocEntries(true, true);
                mAdapter.notifyDataSetChanged();
                return true;

            case R.id.MENU_AUTHOR_WORKS_TOC:
                item.setChecked(true);
                mModel.loadTocEntries(true, false);
                mAdapter.notifyDataSetChanged();
                return true;

            case R.id.MENU_AUTHOR_WORKS_BOOKS:
                item.setChecked(true);
                mModel.loadTocEntries(false, true);
                mAdapter.notifyDataSetChanged();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        TocEntry item = mModel.getTocEntries().get(position);

        //noinspection ConstantConditions
        Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_DELETE, 0, R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        String menuTitle = item.getTitle();
        final MenuPicker<Integer> picker = new MenuPicker<>(getContext(), menuTitle, menu, position,
                                                            this::onContextItemSelected);
        picker.show();
    }

    /**
     * <ul>Delete.
     * <li>TocEntry.TYPE_BOOK: confirmation from user is requested.</li>
     * <li>TocEntry.TYPE_TOC: deletion is immediate.</li>
     * </ul>
     *
     * @return {@code true} if handled
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Integer position) {
        TocEntry item = mModel.getTocEntries().get(position);

        //noinspection SwitchStatementWithTooFewBranches
        switch (menuItem.getItemId()) {
            case R.id.MENU_DELETE:
                switch (item.getType()) {
                    case Book:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteBookAlert(getContext(), item.getTitle(),
                                                        item.getAuthors(), () -> {
                                    mModel.delTocEntry(item);
                                    mAdapter.notifyItemRemoved(position);
                                });
                        return true;

                    case Toc:
                        //noinspection ConstantConditions
                        StandardDialogs.deleteTocEntryAlert(getContext(), item, () -> {
                            mModel.delTocEntry(item);
                            mAdapter.notifyItemRemoved(position);
                        });
                        return true;
                }
                break;
        }

        return false;
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     */
    private void gotoBook(@NonNull final TocEntry item) {
        Intent intent;
        switch (item.getType()) {
            case Toc:
                final ArrayList<Long> bookIds = mModel.getBookIds(item);
                // story in one book, goto that book.
                if (bookIds.size() == 1) {
                    intent = new Intent(getContext(), BookDetailsActivity.class)
                            .putExtra(DBDefinitions.KEY_PK_ID, bookIds.get(0));
                    startActivity(intent);

                } else {
                    // multiple books, go to the list, filtering on the books.
                    intent = new Intent(getContext(), BooksOnBookshelf.class)
                            // clear the back-stack. We want to keep BooksOnBookshelf on top
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            // bring up list, filtered on the book ID's
                            .putExtra(UniqueId.BKEY_ID_LIST, bookIds);
                    startActivity(intent);
                }
                break;

            case Book:
                intent = new Intent(getContext(), BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, item.getId());
                startActivity(intent);
                break;

            default:
                throw new IllegalArgumentException("type=" + item.getType());
        }

        //noinspection ConstantConditions
        getActivity().finish();
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView titleView;
        @Nullable
        final TextView authorView;
        @Nullable
        final TextView firstPublicationView;

        @Nullable
        final CompoundButton multipleBooksView;

        Holder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);
            // optional
            multipleBooksView = itemView.findViewById(R.id.cbx_multiple_books);
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScrollerOverlay.SectionIndexerV2 {

        /** Icon to show for a book. */
        private final Drawable mBookIndicator;
        /** Icon to show for not a book. e.g. a short story... */
        private final Drawable mStoryIndicator;
        /** Caching the inflater. */
        private final LayoutInflater mInflater;

        private final AuthorWorksModel mModel;

        /**
         * Constructor.
         *
         * @param context Current context
         * @param model   the ViewModel for this fragment.
         */
        TocAdapter(@NonNull final Context context,
                   @NonNull final AuthorWorksModel model) {

            mModel = model;
            mInflater = LayoutInflater.from(context);

            mBookIndicator = context.getDrawable(R.drawable.ic_book);
            mStoryIndicator = context.getDrawable(R.drawable.ic_paragraph);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            TocEntry.Type type = TocEntry.Type.get((char) viewType);

            View itemView;
            switch (type) {
                case Toc:
                    itemView = mInflater.inflate(R.layout.row_toc_entry, parent, false);
                    break;
                case Book:
                    itemView = mInflater.inflate(R.layout.row_toc_entry_book, parent, false);
                    break;
                default:
                    throw new IllegalArgumentException("type=" + viewType);
            }

            Holder holder = new Holder(itemView);

            // decorate the row depending on toc entry or actual book

            switch (type) {
                case Toc:
                    holder.titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            mStoryIndicator, null, null, null);
                    break;

                case Book:
                    holder.titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            mBookIndicator, null, null, null);
                    break;

                default:
                    throw new IllegalArgumentException("type=" + viewType);
            }
            return holder;
        }

        @Override
        public int getItemViewType(final int position) {
            return mModel.getTocEntries().get(position).getType().getInt();
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            TocEntry tocEntry = mModel.getTocEntries().get(position);

            holder.titleView.setText(tocEntry.getTitle());

            // optional
            if (holder.authorView != null) {
                holder.authorView.setText(tocEntry.getAuthor().getLabel());
            }
            // optional
            if (holder.firstPublicationView != null) {
                String date = tocEntry.getFirstPublication();
                if (date.isEmpty()) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    String fp = holder.firstPublicationView
                            .getContext().getString(R.string.brackets, date);
                    holder.firstPublicationView.setText(fp);
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                }
            }
            // optional
            if (holder.multipleBooksView != null) {
                boolean isSet = tocEntry.getBookCount() > 1;
                holder.multipleBooksView.setChecked(isSet);
                holder.multipleBooksView.setVisibility(isSet ? View.VISIBLE : View.GONE);
            }
            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(tocEntry));

            // long-click -> menu
            holder.itemView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mModel.getTocEntries().size();
        }

        @Nullable
        @Override
        public String[] getSectionText(@NonNull final Context context,
                                       final int position) {
            // make sure it's still in range.
            int index = MathUtils.clamp(position, 0, mModel.getTocEntries().size() - 1);
            // first character, don't care about Locale locale = LocaleUtils.from(getContext());
            return new String[]{mModel.getTocEntries().get(index)
                                      .getTitle().substring(0, 1).toUpperCase()};
        }
    }
}
