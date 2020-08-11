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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.admin;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.BaseTQTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue.TQTaskCursorRow;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Holder to maintain {@link BaseTQTask} views.
 */
public class TaskViewHolder
        extends BaseViewHolder {

    private final TextView descriptionView;
    private final TextView stateView;
    private final Button retryButton;
    private final TextView retryInfoView;
    private final TextView errorView;
    private final TextView infoView;

    @SuppressWarnings("FieldCanBeLocal")
    private final CompoundButton checkedButton;

    TaskViewHolder(@NonNull final View itemView) {
        super(itemView);

        descriptionView = itemView.findViewById(R.id.description);
        stateView = itemView.findViewById(R.id.state);
        retryButton = itemView.findViewById(R.id.btn_retry);
        retryInfoView = itemView.findViewById(R.id.retry_info);
        errorView = itemView.findViewById(R.id.error);
        infoView = itemView.findViewById(R.id.info);

        // not used for now
        checkedButton = itemView.findViewById(R.id.cbx_selected);
        checkedButton.setVisibility(View.GONE);
    }

    public void bind(@NonNull final TQTaskCursorRow rowData,
                     @NonNull final BaseTQTask task) {

        final Context context = itemView.getContext();
        final Locale userLocale = LocaleUtils.getUserLocale(context);

        descriptionView.setText(task.getDescription(context));
        final String statusCode = rowData.getStatusCode();
        String statusText;
        switch (statusCode) {
            case TQTask.COMPLETED:
                statusText = context.getString(R.string.gr_tq_completed);
                retryInfoView.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                break;

            case TQTask.FAILED:
                statusText = context.getString(R.string.gr_tq_failed);
                retryInfoView.setVisibility(View.GONE);
                retryButton.setVisibility(View.VISIBLE);
                break;

            case TQTask.QUEUED:
                statusText = context.getString(R.string.gr_tq_queued);
                retryInfoView.setText(context.getString(
                        R.string.gr_tq_retry_x_of_y_next_at_z, task.getRetries(),
                        task.getRetryLimit(),
                        toPrettyDateTime(rowData.getRetryDate(context), userLocale)));
                retryInfoView.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.GONE);
                break;

            default:
                statusText = context.getString(R.string.unknown);
                retryInfoView.setVisibility(View.GONE);
                retryButton.setVisibility(View.GONE);
                break;
        }

        statusText += context.getString(R.string.gr_tq_events_recorded, rowData.getEventCount());
        stateView.setText(statusText);

        @GrStatus.Status
        final int extStatus = task.getLastExtStatus();
        if (extStatus != GrStatus.SUCCESS) {
            final GrStatus grStatus = new GrStatus(extStatus, task.getLastException());
            final String msg = context.getString(R.string.gr_tq_last_error_e,
                                                 grStatus.getMessage(context));
            errorView.setText(msg);
            errorView.setVisibility(View.VISIBLE);

        } else {
            errorView.setVisibility(View.GONE);
        }

        infoView.setText(context.getString(
                R.string.gr_tq_generic_task_info, task.getId(),
                toPrettyDateTime(rowData.getQueuedDate(context), userLocale)));
    }
}
