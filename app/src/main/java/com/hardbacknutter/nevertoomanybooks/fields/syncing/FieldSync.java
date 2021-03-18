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
package com.hardbacknutter.nevertoomanybooks.fields.syncing;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * How to handle a data field when updating the entity it belongs to.
 * e.g. skip it, overwrite the value, etc...
 */
public final class FieldSync {

    @NonNull
    public final String key;

    /** label to show to the user. */
    @StringRes
    private final int mLabelId;

    /** Default usage at creation time. */
    @NonNull
    private final SyncAction mDefaultAction;

    /** Is the field capable of appending extra data. */
    private final boolean mCanAppend;

    /** how to use this field. */
    @NonNull
    private SyncAction mSyncAction;

    /**
     * Constructor.
     *
     * @param key           Field key
     * @param labelId       Field label resource id
     * @param canAppend     {@code true} if this field is capable of appending extra data.
     * @param defaultAction default action
     * @param action        initial action
     */
    FieldSync(@NonNull final String key,
              @StringRes final int labelId,
              final boolean canAppend,
              @NonNull final SyncAction defaultAction,
              @NonNull final SyncAction action) {
        this.key = key;
        mLabelId = labelId;
        mCanAppend = canAppend;
        mDefaultAction = defaultAction;
        mSyncAction = action;
    }

    /**
     * Constructor for a related field depending on this field.
     *
     * @param key     Field key
     * @param labelId Field label resource id
     *
     * @return a FieldSync record for the given field.
     */
    FieldSync createRelatedField(@NonNull final String key,
                                 @StringRes final int labelId) {
        return new FieldSync(key, labelId, mCanAppend, mDefaultAction, mSyncAction);
    }

    @NonNull
    public SyncAction getAction() {
        return mSyncAction;
    }

    public void setAction(@NonNull final SyncAction syncAction) {
        mSyncAction = syncAction;
    }

    public void setDefaultAction() {
        mSyncAction = mDefaultAction;
    }

    public boolean isWanted() {
        return mSyncAction != SyncAction.Skip;
    }

    /**
     * Get the label for the field.
     *
     * @return label resource id
     */
    @StringRes
    public int getFieldLabelId() {
        return mLabelId;
    }

    /**
     * Get the label for the currently selected action.
     *
     * @return label resource id
     */
    @StringRes
    public int getActionLabelId() {
        return mSyncAction.getLabelId();
    }

    /**
     * Cycle to the next action stage.
     * <p>
     * if (canAppend): Skip -> CopyIfBlank -> Append -> Overwrite -> Skip
     * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        mSyncAction = mSyncAction.nextState(mCanAppend);
    }

    @Override
    @NonNull
    public String toString() {
        return "FieldSync{"
               + "fieldId=`" + key + '`'
               + ", mCanAppend=" + mCanAppend
               + ", mNameStringId=" + mLabelId
               + ", mDefValue=" + mDefaultAction
               + ", mUsage=" + mSyncAction
               + '}';
    }

}
