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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * How to handle a data field when updating the entity it belongs to.
 * e.g. skip it, overwrite the value, etc...
 */
public final class SyncField
        implements Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<SyncField> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public SyncField createFromParcel(@NonNull final Parcel in) {
            return new SyncField(in);
        }

        @Override
        @NonNull
        public SyncField[] newArray(final int size) {
            return new SyncField[size];
        }
    };

    @NonNull
    public final String key;
    /** label to show to the user. */
    @StringRes
    private final int mLabelId;
    /** Default usage at creation time. */
    @NonNull
    private final SyncAction mDefaultAction;
    /** Is the field capable of appending extra data. It can be a true List, or i.e a String. */
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
    SyncField(@NonNull final String key,
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

    SyncField(@NonNull final Parcel in) {
        //noinspection ConstantConditions
        key = in.readString();
        mLabelId = in.readInt();
        mCanAppend = in.readByte() != 0;
        //noinspection ConstantConditions
        mDefaultAction = in.readParcelable(SyncAction.class.getClassLoader());
        //noinspection ConstantConditions
        mSyncAction = in.readParcelable(SyncAction.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(key);
        dest.writeInt(mLabelId);
        dest.writeByte((byte) (mCanAppend ? 1 : 0));
        dest.writeParcelable(mDefaultAction, flags);
        dest.writeParcelable(mSyncAction, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Constructor for a related field depending on this field.
     *
     * @param key Field key
     *
     * @return a SyncField record for the given field.
     */
    SyncField createRelatedField(@NonNull final String key) {
        return new SyncField(key, mLabelId, mCanAppend, mDefaultAction, mSyncAction);
    }

    @NonNull
    public SyncAction getAction() {
        return mSyncAction;
    }

    public void setAction(@NonNull final SyncAction syncAction) {
        mSyncAction = syncAction;
    }

    void setDefaultAction() {
        mSyncAction = mDefaultAction;
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
        return "SyncField{"
               + "fieldId=`" + key + '`'
               + ", mCanAppend=" + mCanAppend
               + ", mNameStringId=" + mLabelId
               + ", mDefValue=" + mDefaultAction
               + ", mUsage=" + mSyncAction
               + '}';
    }

}
