/*
 * @Copyright 2018-2023 HardBackNutter
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
    private final String key;
    /** label to show to the user. */
    @NonNull
    private final String label;
    /** Default usage at creation time. */
    @NonNull
    private final SyncAction defaultAction;
    /** Is the field capable of appending extra data. It can be a true List, or i.e a String. */
    private final boolean canAppend;
    /** how to use this field. */
    @NonNull
    private SyncAction syncAction;

    /**
     * Constructor.
     *
     * @param key           Field key
     * @param label         Field label resource id
     * @param canAppend     {@code true} if this field is capable of appending extra data.
     * @param defaultAction default action
     * @param syncAction    initial action
     */
    SyncField(@NonNull final String key,
              @NonNull final String label,
              final boolean canAppend,
              @NonNull final SyncAction defaultAction,
              @NonNull final SyncAction syncAction) {
        this.key = key;
        this.label = label;
        this.canAppend = canAppend;
        this.defaultAction = defaultAction;
        this.syncAction = syncAction;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SyncField(@NonNull final Parcel in) {
        //noinspection DataFlowIssue
        key = in.readString();
        //noinspection DataFlowIssue
        label = in.readString();
        canAppend = in.readByte() != 0;
        //noinspection DataFlowIssue
        defaultAction = in.readParcelable(SyncAction.class.getClassLoader());
        //noinspection DataFlowIssue
        syncAction = in.readParcelable(SyncAction.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(key);
        dest.writeString(label);
        dest.writeByte((byte) (canAppend ? 1 : 0));
        dest.writeParcelable(defaultAction, flags);
        dest.writeParcelable(syncAction, flags);
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
    @NonNull
    SyncField createRelatedField(@NonNull final String key) {
        return new SyncField(key, label, canAppend, defaultAction, syncAction);
    }

    /**
     * Get the action required for this field.
     *
     * @return syncAction
     */
    @NonNull
    public SyncAction getAction() {
        return syncAction;
    }

    /**
     * Set the action required for this field.
     *
     * @param syncAction to use
     */
    public void setAction(@NonNull final SyncAction syncAction) {
        this.syncAction = syncAction;
    }

    void setDefaultAction() {
        syncAction = defaultAction;
    }

    /**
     * Get the key (colum nname) for the field.
     *
     * @return key
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * Get the label for the field.
     *
     * @return label
     */
    @NonNull
    public String getFieldLabel() {
        return label;
    }

    /**
     * Get the label for the currently selected action.
     *
     * @return label resource id
     */
    @StringRes
    public int getActionLabelResId() {
        return syncAction.getLabelResId();
    }

    /**
     * Cycle to the next action stage.
     * <p>
     * if (canAppend): Skip -> CopyIfBlank -> Append -> Overwrite -> Skip
     * else          : Skip -> CopyIfBlank -> Overwrite -> Skip
     */
    public void nextState() {
        syncAction = syncAction.nextState(canAppend);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncField{"
               + "key=`" + key + '`'
               + ", canAppend=" + canAppend
               + ", labelResId=" + label
               + ", defaultAction=" + defaultAction
               + ", syncAction=" + syncAction
               + '}';
    }

}
