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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReadMetaDataTask;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentCalibreLibraryMapperBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditCalibreLibraryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class CalibreLibraryMappingFragment
        extends Fragment {

    /** Log tag. */
    public static final String TAG = "CalibreLibraryMapFrag";

    private CalibreLibraryMappingViewModel mVm;

    /** View Binding. */
    private FragmentCalibreLibraryMapperBinding mVb;
    private LibraryBookshelfMapperAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentCalibreLibraryMapperBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(CalibreLibraryMappingViewModel.class);
        mVm.init(getArguments());

        final ArchiveReadMetaDataTask readMetaDataTask = new ViewModelProvider(this)
                .get(ArchiveReadMetaDataTask.class);
        readMetaDataTask.onFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        readMetaDataTask.onFailure().observe(getViewLifecycleOwner(), this::onMetaDataFailure);

        if (mVm.getLibraries().isEmpty()) {
            final ImportHelper helper = mVm.getImportHelper();
            Snackbar.make(view, R.string.progress_msg_connecting, Snackbar.LENGTH_SHORT).show();
            readMetaDataTask.start(helper);
        }

        //noinspection ConstantConditions
        mAdapter = new LibraryBookshelfMapperAdapter(getContext());
        mVb.libraryList.setAdapter(mAdapter);
    }

    private void onMetaDataRead(@NonNull final FinishedMessage<ArchiveMetaData> message) {
        Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

        // at this moment, all server libs have been synced with our database
        // and are mapped to a valid bookshelf

        final Bundle metadata = message.result.getBundle();
        //TODO: once again, we only support the single physical lib.
        final CalibreLibrary calibreLibrary = metadata.getParcelable(
                CalibreContentServerReader.BKEY_LIBRARY);
        Objects.requireNonNull(calibreLibrary, "calibreLibrary");

        final ArrayList<CalibreLibrary> vLibs = metadata.getParcelableArrayList(
                CalibreContentServerReader.BKEY_VIRTUAL_LIBRARY_LIST);

        mVm.setLibraries(calibreLibrary, vLibs);
        mAdapter.notifyDataSetChanged();
    }

    private void onMetaDataFailure(final FinishedMessage<Exception> message) {
        //URGENT: clean screen + good msg + offer to go to connection options
        //noinspection ConstantConditions
        StandardDialogs.showError(getContext(), R.string.error_unknown);
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowEditCalibreLibraryBinding mVb;

        Holder(@NonNull final View itemView) {
            super(itemView);
            mVb = RowEditCalibreLibraryBinding.bind(itemView);
        }
    }

    public class LibraryBookshelfMapperAdapter
            extends RecyclerView.Adapter<Holder> {

        final ExtArrayAdapter<Bookshelf> mBookshelfAdapter;
        /** Cached inflater. */
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        LibraryBookshelfMapperAdapter(@NonNull final Context context) {
            super();
            mInflater = LayoutInflater.from(context);
            mBookshelfAdapter = new EntityArrayAdapter<>(context,
                                                         R.layout.dropdown_menu_popup_item,
                                                         ExtArrayAdapter.FilterType.Passthrough,
                                                         mVm.getBookshelfList());
        }

        @SuppressLint("ClickableViewAccessibility")
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View itemView = mInflater
                    .inflate(R.layout.row_edit_calibre_library, parent, false);
            final Holder holder = new Holder(itemView);

            holder.mVb.bookshelf.setAdapter(mBookshelfAdapter);
            // reminder: setOnItemSelectedListener() DOES NOT WORK with AutoCompleteTextView.
            // Use setOnItemClickListener() instead!
            holder.mVb.bookshelf.setOnItemClickListener((adapterView, view, position, id) -> mVm
                    .setLibraryBookshelf(holder.getBindingAdapterPosition(), position));

            holder.mVb.btnCreate.setOnClickListener(btn -> {
                try {
                    btn.setEnabled(false);
                    final Bookshelf bookshelf = mVm.createLibraryAsBookshelf(
                            btn.getContext(), holder.getBindingAdapterPosition());
                    mBookshelfAdapter.notifyDataSetChanged();
                    holder.mVb.bookshelf.setText(bookshelf.getName(), false);

                } catch (@NonNull final DAO.DaoWriteException e) {
                    //TODO: better error msg
                    Snackbar.make(holder.itemView, R.string.error_unknown,
                                  Snackbar.LENGTH_LONG).show();
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final CalibreLibrary library = mVm.getLibrary(position);
            holder.mVb.libraryName.setText(library.getName());
            holder.mVb.bookshelf.setText(library.getMappedBookshelf().getName(), false);
            holder.mVb.btnCreate.setEnabled(!mVm.isLibraryNameAnExistingBookshelfName(position));
        }

        @Override
        public int getItemCount() {
            return mVm.getLibraries().size();
        }
    }
}
