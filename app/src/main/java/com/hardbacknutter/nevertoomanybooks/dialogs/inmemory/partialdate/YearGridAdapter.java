/*
 * @Copyright 2018-2026 HardBackNutter
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

package com.hardbacknutter.nevertoomanybooks.dialogs.inmemory.partialdate;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;

class YearGridAdapter
        extends RecyclerView.Adapter<YearGridAdapter.Holder> {

    private final int initYear;
    private final int startYear;
    private final int endYear;
    @NonNull
    private final Consumer<Integer> yearListener;

    YearGridAdapter(final int initYear,
                    final int startYear,
                    final int endYear,
                    @NonNull final Consumer<Integer> yearListener) {
        this.initYear = initYear;
        this.startYear = startYear;
        this.endYear = endYear;
        this.yearListener = yearListener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                     final int viewType) {
        final MaterialButton view = (MaterialButton) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.partial_date_picker_year, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder,
                                 final int position) {
        final int year = startYear + position;
        holder.yearView.setText(String.format(Locale.getDefault(), "%d", year));
        holder.yearView.setOnClickListener(v -> yearListener.accept(year));
        holder.yearView.setChecked(year == initYear);
    }

    @Override
    public int getItemCount() {
        return endYear - startYear;
    }

    int positionForYear(final int year) {
        return year - startYear;
    }

    public static class Holder
            extends RecyclerView.ViewHolder {

        final MaterialButton yearView;

        Holder(@NonNull final MaterialButton itemView) {
            super(itemView);
            this.yearView = itemView;
        }
    }
}
