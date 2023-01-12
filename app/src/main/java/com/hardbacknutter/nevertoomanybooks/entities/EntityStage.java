/*
 * @Copyright 2018-2022 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * State engine for the status of an entity.
 */
public class EntityStage
        implements Parcelable {

    public static final Creator<EntityStage> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public EntityStage createFromParcel(@NonNull final Parcel in) {
            return new EntityStage(in);
        }

        @Override
        @NonNull
        public EntityStage[] newArray(final int size) {
            return new EntityStage[size];
        }
    };
    @NonNull
    private Stage stage = Stage.Clean;
    private boolean locked;

    public EntityStage() {
    }

    protected EntityStage(@NonNull final Parcel in) {
        stage = in.readParcelable(Stage.class.getClassLoader());
        locked = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeParcelable(stage, flags);
        dest.writeByte((byte) (locked ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    void lock() {
        if (!locked) {
            locked = true;
        } else {
            throw new IllegalStateException("Already locked");
        }
    }

    void unlock() {
        if (locked) {
            locked = false;
        } else {
            throw new IllegalStateException("Already unlocked");
        }
    }

    @NonNull
    public Stage getStage() {
        return stage;
    }

    public void setStage(@NonNull final Stage stage) {
        if (locked) {
            return;
        }
        this.stage = stage;
    }

    public enum Stage
            implements Parcelable {
        /** The entity <strong>is not</strong> modified. */
        Clean,
        /** The entity <strong>can</strong> be modified, but that has not been done yet. */
        WriteAble,
        /** The entity <strong>has</strong> been modified. */
        Dirty;

        /** {@link Parcelable}. */
        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        public static final Creator<Stage> CREATOR = new Creator<>() {
            @Override
            @NonNull
            public Stage createFromParcel(@NonNull final Parcel in) {
                return values()[in.readInt()];
            }

            @Override
            @NonNull
            public Stage[] newArray(final int size) {
                return new Stage[size];
            }
        };

        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeInt(ordinal());
        }
    }
}
