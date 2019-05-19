package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.eleybourn.bookcatalogue.entities.TocEntry;
import com.eleybourn.bookcatalogue.viewmodels.AuthorWorksModel;
import com.eleybourn.bookcatalogue.widgets.FastScrollerOverlay;

/**
 * Display all TocEntry's for an Author.
 * Selecting an entry will take you to the book(s) that contain that entry.
 */
public class AuthorWorksFragment
        extends Fragment {

    /** Fragment manager tag. */
    public static final String TAG = AuthorWorksFragment.class.getSimpleName();

    /** Optional. Also show the authors book. Defaults to {@code true}. */
    @SuppressWarnings("WeakerAccess")
    public static final String BKEY_WITH_BOOKS = TAG + ":withBooks";

    private AuthorWorksModel mModel;

    private RecyclerView mListView;
    private LinearLayoutManager mLinearLayoutManager;
    private TocAdapter mAdapter;

//    @Override
//    @CallSuper
//    public void onCreate(@Nullable final Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        // make sure {@link #onCreateOptionsMenu} is called
//        setHasOptionsMenu(true);
//    }

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

        @SuppressWarnings("ConstantConditions")
        long authorId = getArguments().getLong(DBDefinitions.KEY_ID, 0);
        boolean withBooks = getArguments().getBoolean(BKEY_WITH_BOOKS, true);

        mModel = ViewModelProviders.of(this).get(AuthorWorksModel.class);
        mModel.init(authorId, withBooks);

        String title = mModel.getAuthor().getLabel() + '[' + mModel.getTocEntries().size() + ']';
        //noinspection ConstantConditions
        getActivity().setTitle(title);

        //noinspection ConstantConditions
        mListView = getView().findViewById(android.R.id.list);
        mListView.setHasFixedSize(true);
        mLinearLayoutManager = new LinearLayoutManager(getContext());
        mListView.setLayoutManager(mLinearLayoutManager);
        //noinspection ConstantConditions
        mListView.addItemDecoration(
                new DividerItemDecoration(getContext(), mLinearLayoutManager.getOrientation()));

        mListView.addItemDecoration(
                new FastScrollerOverlay(getContext(), R.drawable.fast_scroll_overlay));

        mAdapter = new TocAdapter(getContext(), mModel);
        mListView.setAdapter(mAdapter);
    }

    /**
     * User tapped on an entry; get the book(s) for that entry and display.
     */
    private void gotoBook(@NonNull final TocEntry item) {
        Intent intent;
        switch (item.getTocType()) {
            case 'T':
                // see note on dba method about Integer vs. Long
                final ArrayList<Integer> books = mModel.getBookIds(item);
                intent = new Intent(getContext(), BooksOnBookshelf.class)
                        // clear the back-stack. We want to keep BooksOnBookshelf on top
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        // bring up list, filtered on the book id's
                        .putExtra(UniqueId.BKEY_ID_LIST, books);
                startActivity(intent);
                break;

            case 'B':
                intent = new Intent(getContext(), BookDetailsActivity.class)
                        .putExtra(DBDefinitions.KEY_ID, item.getId());
                startActivity(intent);
                break;

            default:
                throw new IllegalArgumentException("type=" + item.getTocType());
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

        Holder(@NonNull final View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.title);
            // optional
            authorView = itemView.findViewById(R.id.author);
            // optional
            firstPublicationView = itemView.findViewById(R.id.year);
        }
    }

    public class TocAdapter
            extends RecyclerView.Adapter<Holder>
            implements FastScrollerOverlay.SectionIndexerV2 {

        /** Icon to show for a book. */
        private final Drawable sBookIndicator;
        /** Icon to show for not a book. e.g. a short story... */
        private final Drawable sStoryIndicator;

        private final LayoutInflater mInflater;

        private final AuthorWorksModel mModel;

        TocAdapter(@NonNull final Context context,
                   @NonNull final AuthorWorksModel model) {

            mModel = model;

            sBookIndicator = context.getDrawable(R.drawable.ic_book);
            sStoryIndicator = context.getDrawable(R.drawable.ic_lens);
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View itemView = mInflater.inflate(R.layout.row_toc_entry, parent, false);
            return new Holder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            TocEntry item = mModel.getTocEntries().get(position);
            // decorate the row depending on toc entry or actual book
            switch (item.getTocType()) {
                case 'T':
                    holder.titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sStoryIndicator, null, null, null);
                    break;

                case 'B':
                    holder.titleView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            sBookIndicator, null, null, null);
                    break;

                default:
                    throw new IllegalArgumentException("type=" + item.getTocType());
            }

            holder.titleView.setText(item.getTitle());
            // optional
            if (holder.authorView != null) {
                holder.authorView.setText(item.getAuthor().getLabel());
            }
            // optional
            if (holder.firstPublicationView != null) {
                String date = item.getFirstPublication();
                if (date.isEmpty()) {
                    holder.firstPublicationView.setVisibility(View.GONE);
                } else {
                    String fp = holder.firstPublicationView
                            .getContext().getString(R.string.brackets, date);
                    holder.firstPublicationView.setText(fp);
                    holder.firstPublicationView.setVisibility(View.VISIBLE);
                }
            }

            // click -> get the book(s) for that entry and display.
            holder.itemView.setOnClickListener(v -> gotoBook(item));
        }

        @Override
        public int getItemCount() {
            return mModel.getTocEntries().size();
        }

        @Nullable
        @Override
        public String[] getSectionTextForPosition(final int position) {
            // make sure it's still in range.
            int index = MathUtils.clamp(position, 0, mModel.getTocEntries().size() - 1);

            return new String[]{mModel.getTocEntries().get(index)
                                      .getTitle().substring(0, 1).toUpperCase()};
        }
    }
}
