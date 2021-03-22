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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentCalibreLibraryMapperBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditCalibreLibraryBinding;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class CalibreLibraryMappingFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "CalibreLibraryMapFrag";

    private CalibreLibraryMappingViewModel mVm;

    /** View Binding. */
    private FragmentCalibreLibraryMapperBinding mVb;

    private List<Bookshelf> mBookshelfList;
    private ExtArrayAdapter<CalibreLibrary> mLibraryArrayAdapter;
    private ExtArrayAdapter<Bookshelf> mBookshelfAdapter;

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

        mVm.onMetaDataRead().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        mVm.onMetaDataFailure().observe(getViewLifecycleOwner(), this::onMetaDataFailure);

        //noinspection ConstantConditions
        mLibraryArrayAdapter = new EntityArrayAdapter<>(getContext(), mVm.getLibraries());
        mVb.libraryName.setAdapter(mLibraryArrayAdapter);
        mVb.libraryName.setOnItemClickListener(
                (av, v, position, id) -> onLibrarySelected(position));

        mBookshelfList = mVm.getBookshelfList();
        mBookshelfAdapter = new EntityArrayAdapter<>(getContext(), mBookshelfList);
        mVb.bookshelf.setAdapter(mBookshelfAdapter);
        mVb.bookshelf.setOnItemClickListener((av, v, position, id) -> {
            final Bookshelf bookshelf = mBookshelfAdapter.getItem(position);
            //noinspection ConstantConditions
            mVm.mapBookshelfToLibrary(bookshelf);
            mVb.bookshelf.setText(bookshelf.getName());
        });

        mVb.btnCreate.setOnClickListener(btn -> {
            try {
                btn.setEnabled(false);
                final Bookshelf bookshelf = mVm.createLibraryAsBookshelf(getContext());
                addBookshelf(bookshelf, mVb.bookshelf);

            } catch (@NonNull final DaoWriteException e) {
                //TODO: better error msg
                Snackbar.make(mVb.getRoot(), R.string.error_unknown,
                              Snackbar.LENGTH_LONG).show();
            }
        });

        if (mVm.getLibraries().isEmpty()) {
            Snackbar.make(view, R.string.progress_msg_connecting, Snackbar.LENGTH_SHORT).show();
            mVm.readMetaData();
        } else {
            onLibrarySelected(0);
            mVb.getRoot().setVisibility(View.VISIBLE);
        }
    }

    private void onMetaDataRead(@NonNull final FinishedMessage<ArchiveMetaData> message) {
        Objects.requireNonNull(message.result, FinishedMessage.MISSING_TASK_RESULTS);

        mVm.setLibraries(message.result);
        mLibraryArrayAdapter.notifyDataSetChanged();

        onLibrarySelected(0);
        mVb.getRoot().setVisibility(View.VISIBLE);
    }

    private void addBookshelf(@NonNull final Bookshelf bookshelf,
                              @NonNull final AutoCompleteTextView view) {
        mBookshelfAdapter.add(bookshelf);
        view.setText(bookshelf.getName(), false);
        // ESSENTIAL STEP: force the internal filtered list to ALSO add the new object
        // See internal code for ArrayAdapter#add and the use of mObjects/mOriginalValues
        mBookshelfAdapter.getFilter().filter(bookshelf.getName());
    }

    private void onLibrarySelected(final int position) {
        // remember and display data for the selected library
        mVm.setCurrentLibrary(position);
        final CalibreLibrary library = mVm.getCurrentLibrary();
        mVb.libraryName.setText(library.getName(), false);

        mBookshelfList.stream()
                      .filter(bookshelf -> bookshelf.getId() == library.getMappedBookshelfId())
                      .map(Bookshelf::getName)
                      .findFirst()
                      .ifPresent(mVb.bookshelf::setText);

        mVb.btnCreate.setEnabled(mBookshelfList.stream()
                                               .map(Bookshelf::getName)
                                               .noneMatch(name -> name.equals(library.getName())));

        if (library.getVirtualLibraries().isEmpty()) {
            mVb.headerVlibs.setVisibility(View.GONE);
            mVb.virtualLibraries.setVisibility(View.GONE);
            mVb.virtualLibraries.setAdapter(null);

        } else {
            mVb.headerVlibs.setVisibility(View.VISIBLE);
            mVb.virtualLibraries.setVisibility(View.VISIBLE);
            //noinspection ConstantConditions
            mVb.virtualLibraries.setAdapter(new VirtualLibraryMapperAdapter(getContext()));
        }
    }

    private void onMetaDataFailure(@NonNull final FinishedMessage<Exception> message) {
        final Context context = getContext();
        //noinspection ConstantConditions
        String msg = ExMsg.map(context, TAG, message.result);
        if (msg == null) {
            msg = ExMsg.ioExFallbackMsg(context, message.result,
                                        getString(R.string.error_network_site_access_failed,
                                                  CalibreContentServer.getHostUrl()));
        }
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setTitle(R.string.lbl_calibre_content_server)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    d.dismiss();
                    // just pop, we're always called from a fragment
                    getParentFragmentManager().popBackStack();
                })
                .create()
                .show();
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

    public class VirtualLibraryMapperAdapter
            extends RecyclerView.Adapter<Holder> {

        /** Cached inflater. */
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        VirtualLibraryMapperAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View itemView = mInflater
                    .inflate(R.layout.row_edit_calibre_library, parent, false);
            final Holder holder = new Holder(itemView);

            holder.mVb.bookshelf.setAdapter(mBookshelfAdapter);
            holder.mVb.bookshelf.setOnItemClickListener((av, v, position, id) -> {
                final Bookshelf bookshelf = mBookshelfAdapter.getItem(position);
                //noinspection ConstantConditions
                mVm.mapBookshelfToVirtualLibrary(bookshelf, holder.getBindingAdapterPosition());
                holder.mVb.bookshelf.setText(bookshelf.getName());
            });

            holder.mVb.btnCreate.setOnClickListener(btn -> {
                try {
                    btn.setEnabled(false);
                    //noinspection ConstantConditions
                    final Bookshelf bookshelf = mVm.createVirtualLibraryAsBookshelf(
                            getContext(), holder.getBindingAdapterPosition());
                    addBookshelf(bookshelf, holder.mVb.bookshelf);

                } catch (@NonNull final DaoWriteException e) {
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
            final CalibreVirtualLibrary vlib = mVm.getVirtualLibrary(position);
            holder.mVb.libraryName.setText(vlib.getName());

            mBookshelfList.stream()
                          .filter(bookshelf -> bookshelf.getId() == vlib.getMappedBookshelfId())
                          .map(Bookshelf::getName)
                          .findFirst()
                          .ifPresent(holder.mVb.bookshelf::setText);

            holder.mVb.btnCreate.setEnabled(
                    mBookshelfList.stream()
                                  .map(Bookshelf::getName)
                                  .noneMatch(name -> name.equals(vlib.getName())));
        }

        @Override
        public int getItemCount() {
            return mVm.getCurrentLibrary().getVirtualLibraries().size();
        }
    }
}
