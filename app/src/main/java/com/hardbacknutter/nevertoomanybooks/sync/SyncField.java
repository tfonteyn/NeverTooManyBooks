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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.List;

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
    @NonNull
    private final Type type;
    /** how to use this field. */
    @NonNull
    private SyncAction syncAction;

    /**
     * Constructor.
     *
     * @param key        Field key
     * @param label      Field label resource id
     * @param type       of field
     * @param syncAction initial action
     */
    SyncField(@NonNull final String key,
              @NonNull final String label,
              @NonNull final Type type,
              @NonNull final SyncAction syncAction) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.syncAction = syncAction;
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    @SuppressWarnings("DataFlowIssue")
    private SyncField(@NonNull final Parcel in) {
        key = in.readString();
        label = in.readString();
        type = in.readParcelable(SyncAction.class.getClassLoader());
        syncAction = in.readParcelable(SyncAction.class.getClassLoader());
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(key);
        dest.writeString(label);
        dest.writeParcelable(type, flags);
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
        return new SyncField(key, label, type, syncAction);
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
        syncAction = type.getDefaultAction();
    }

    /**
     * Get the key (column name) for this field.
     *
     * @return key
     */
    @NonNull
    public String getKey() {
        return key;
    }

    /**
     * Get the label for this field.
     *
     * @return label
     */
    @NonNull
    String getFieldLabel() {
        return label;
    }

    /**
     * Get the {@link Type} for this field.
     *
     * @return type
     */
    @NonNull
    public Type getType() {
        return type;
    }

    /**
     * Get the label for the currently selected action.
     *
     * @param context Current context
     *
     * @return label
     */
    @NonNull
    String getActionLabel(@NonNull final Context context) {
        return syncAction.getLabel(context);
    }

    /**
     * Cycle to the next action.
     */
    void nextState() {
        syncAction = type.nextState(syncAction);
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncField{"
               + "key=`" + key + '`'
               + ", type=" + type
               + ", labelResId=" + label
               + ", syncAction=" + syncAction
               + '}';
    }

    // - uppercase names, as 'List' gets confused with 'java.util.List'
    // - the lists MUST end with Skip, i.e. same as the first step,
    //   to ensure a circular movement
    public enum Type
            implements Parcelable {
        /**
         * {@code List} fields.
         */
        LIST(SyncAction.Append, List.of(
                SyncAction.Skip,
                SyncAction.CopyIfBlank,
                SyncAction.Append,
                SyncAction.Overwrite,
                SyncAction.Skip
        )),
        /**
         * A {@code String} for which we support {@link SyncAction#Append}.
         * Add the key to {@link SyncReaderProcessor}#processAppend
         * if you use this type!
         */
        STRING(SyncAction.CopyIfBlank, List.of(
                SyncAction.Skip,
                SyncAction.CopyIfBlank,
                SyncAction.Append,
                SyncAction.Overwrite,
                SyncAction.Skip
        )),
        /**
         * Any other type, inc. {@code String}, but excl. {@code List},
         * which does not support {@link SyncAction#Append}.
         */
        OTHER(SyncAction.CopyIfBlank, List.of(
                SyncAction.Skip,
                SyncAction.CopyIfBlank,
                SyncAction.Overwrite,
                SyncAction.Skip
        ));

        /** {@link Parcelable}. */
        public static final Creator<Type> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Type createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Type[] newArray(final int size) {
                return new Type[size];
            }
        };

        @NonNull
        private final SyncAction defAction;
        @NonNull
        private final List<SyncAction> actions;

        Type(@NonNull final SyncAction defAction,
             @NonNull final List<SyncAction> actions) {
            this.defAction = defAction;
            this.actions = actions;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @NonNull
        SyncAction getDefaultAction() {
            return defAction;
        }

        @NonNull
        SyncAction nextState(@NonNull final SyncAction syncAction) {
            final int i = actions.indexOf(syncAction);
            // Sanity check against illegal input (from prefs)
            if (i == -1) {
                return SyncAction.Skip;
            }
            return actions.get(i + 1);
        }

        @Override
        @NonNull
        public String toString() {
            return "Type{"
                   + "defAction=" + defAction
                   + ", actions=" + actions
                   + '}';
        }
    }
}
