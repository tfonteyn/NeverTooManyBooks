/*
 * @Copyright 2018-2025 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.text.Html;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collection;

import com.hardbacknutter.nevertoomanybooks.databinding.RowSyncfieldConfigBinding;
import com.hardbacknutter.nevertoomanybooks.widgets.adapters.MultiColumnRecyclerViewAdapter;

public class SyncFieldAdapter
        extends MultiColumnRecyclerViewAdapter<SyncFieldAdapter.Holder> {

    private static final SyncField[] Z_ARRAY_SYNC_FIELD = new SyncField[0];

    @NonNull
    private final SyncField[] syncFields;

    /**
     * Constructor.
     *
     * @param context     Current context.
     * @param syncFields  to show
     * @param columnCount the number of columns to be used
     */
    public SyncFieldAdapter(@NonNull final Context context,
                            @NonNull final Collection<SyncField> syncFields,
                            final int columnCount) {
        super(context, columnCount);
        this.syncFields = syncFields.toArray(Z_ARRAY_SYNC_FIELD);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {

        final RowSyncfieldConfigBinding vb = RowSyncfieldConfigBinding
                .inflate(getInflater(), parent, false);
        adjustColumns(vb.getRoot());
        final Holder holder = new Holder(vb);

        holder.vb.cbxUsage.setOnClickListener(v -> {
            final int gridPosition = holder.getBindingAdapterPosition();
            final int position = transpose(gridPosition);
            requireValidOrThrow(position, gridPosition);

            final SyncField fs = syncFields[position];
            fs.nextState();
            holder.vb.cbxUsage.setChecked(fs.getAction() != SyncAction.Skip);
            holder.vb.cbxUsage.setText(fs.getActionLabel(holder.vb.cbxUsage.getContext()));
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {

        final int listIndex = transpose(position);
        if (listIndex == RecyclerView.NO_POSITION) {
            holder.onBind(null);
        } else {
            holder.onBind(syncFields[listIndex]);
        }
    }

    @Override
    protected int getRealItemCount() {
        return syncFields.length;
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowSyncfieldConfigBinding vb;

        Holder(@NonNull final RowSyncfieldConfigBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }

        void onBind(@Nullable final SyncField syncField) {
            if (syncField == null) {
                vb.getRoot().setVisibility(View.INVISIBLE);
            } else {
                vb.getRoot().setVisibility(View.VISIBLE);

                vb.field.setText(Html.fromHtml(syncField.getFieldLabel(), 0));
                vb.cbxUsage.setChecked(syncField.getAction() != SyncAction.Skip);
                vb.cbxUsage.setText(syncField.getActionLabel(vb.cbxUsage.getContext()));
            }
        }
    }
}
