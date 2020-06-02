/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue;

import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;

/**
 * Holder to maintain task views.
 */
class TaskViewHolder
        extends BindableItemViewHolder {

    /** Use {@link #setRetryInfo} to access. */
    protected final TextView retryInfoView;
    final TextView descriptionView;
    final TextView stateView;
    final TextView errorView;
    final CompoundButton checkButton;
    final Button retryButton;
    /** Use {@link #setJobInfo} to access. */
    private final TextView jobInfoView;

    TaskViewHolder(@NonNull final View itemView) {
        super(itemView);

        descriptionView = itemView.findViewById(R.id.description);
        stateView = itemView.findViewById(R.id.state);
        retryInfoView = itemView.findViewById(R.id.retry_info);
        errorView = itemView.findViewById(R.id.error);
        jobInfoView = itemView.findViewById(R.id.job_info);
        checkButton = itemView.findViewById(R.id.cbx_selected);
        retryButton = itemView.findViewById(R.id.btn_retry);
    }

    public void setJobInfo(final long taskId,
                           @NonNull final LocalDateTime queuedDate,
                           @NonNull final Locale userLocale) {
        jobInfoView.setText(jobInfoView.getContext().getString(
                R.string.gr_tq_generic_task_info, taskId,
                toPrettyDateTime(queuedDate, userLocale)));
    }

    public void setRetryInfo(final int retries,
                             final int retryLimit,
                             @NonNull final LocalDateTime retryDate,
                             @NonNull final Locale userLocale) {
        retryInfoView.setText(retryInfoView.getContext().getString(
                R.string.gr_tq_retry_x_of_y_next_at_z, retries, retryLimit,
                toPrettyDateTime(retryDate, userLocale)));
    }
}
