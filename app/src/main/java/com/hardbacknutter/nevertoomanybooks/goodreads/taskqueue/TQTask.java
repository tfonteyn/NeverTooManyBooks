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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * Base class for tasks. This builds and populates simple View objects to display the task.
 * <p>
 * A Task *MUST* be serializable.z\
 * This means that it can not contain any references to UI components or similar objects.
 */
public abstract class TQTask
        extends Task<TasksCursor, TaskViewHolder> {

    private static final long serialVersionUID = -937739402397246913L;
    @Nullable
    private GrStatus mLastExtStatus;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    protected TQTask(@NonNull final String description) {
        super(description);
    }

    protected void setLastExtStatus(@Nullable final GrStatus status) {
        mLastExtStatus = status;
    }

    /**
     * @return {@code false} to requeue, {@code true} for success
     */
    public abstract boolean run(@NonNull QueueManager queueManager);

    @Override
    @NonNull
    public TaskViewHolder onCreateViewHolder(@NonNull final ViewGroup parent) {
        View itemView = LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.row_task_info, parent, false);
        return new TaskViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull final TaskViewHolder holder,
                                 @NonNull final TasksCursor row,
                                 @NonNull final DAO db) {

        Context context = holder.itemView.getContext();
        Locale userLocale = LocaleUtils.getUserLocale(context);

        holder.descriptionView.setText(getDescription(context));
        String statusCode = row.getStatusCode().toUpperCase(Locale.ENGLISH);
        String statusText;
        switch (statusCode) {
            case COMPLETED:
                statusText = context.getString(R.string.gr_tq_completed);
                holder.retryInfoView.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;

            case FAILED:
                statusText = context.getString(R.string.gr_tq_failed);
                holder.retryInfoView.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.VISIBLE);
                break;

            case QUEUED:
                statusText = context.getString(R.string.gr_tq_queued);
                String date = DateUtils.toPrettyDateTime(row.getRetryDate(), userLocale);
                String info = context.getString(R.string.gr_tq_retry_x_of_y_next_at_z,
                                                getRetries(), getRetryLimit(), date);
                holder.retryInfoView.setText(info);
                holder.retryInfoView.setVisibility(View.VISIBLE);
                holder.retryButton.setVisibility(View.GONE);
                break;

            default:
                statusText = context.getString(R.string.unknown);
                holder.retryInfoView.setVisibility(View.GONE);
                holder.retryButton.setVisibility(View.GONE);
                break;
        }

        statusText += context.getString(R.string.gr_tq_events_recorded, row.getNoteCount());
        holder.stateView.setText(statusText);

        @Nullable
        Exception e = getLastException();
        // take a copy!
        @Nullable
        GrStatus extStatus = mLastExtStatus;

        if (extStatus != null && extStatus != GrStatus.Completed) {
            String msg;
            if (extStatus == GrStatus.UnexpectedError && e != null) {
                msg = e.getLocalizedMessage();
                if (msg == null) {
                    msg = e.getClass().getSimpleName();
                }
            } else {
                msg = extStatus.getString(context);
            }

            msg = context.getString(R.string.gr_tq_last_error_e, msg);
            holder.errorView.setText(msg);
            holder.errorView.setVisibility(View.VISIBLE);

        } else {
            holder.errorView.setVisibility(View.GONE);
        }

        String date = DateUtils.toPrettyDateTime(row.getQueuedDate(), userLocale);
        String info = context.getString(R.string.gr_tq_generic_task_info, getId(), date);
        holder.jobInfoView.setText(info);
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull final List<ContextDialogItem> menuItems,
                                    @NonNull final DAO db) {

        menuItems.add(new ContextDialogItem(
                context.getString(R.string.gr_tq_menu_delete_task),
                () -> QueueManager.getQueueManager().deleteTask(getId())));
    }
}
