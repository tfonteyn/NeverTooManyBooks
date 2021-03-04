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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.database.DBKeys;

/**
 * Definition of the <strong>supported</strong> user custom fields.
 * <p>
 * The names are hardcoded for now, ENHANCE: make custom field names editable.
 */
public final class CustomFields {

    @Type
    static final String TYPE_BOOL = "bool";
    @Type
    static final String TYPE_DATETIME = "datetime";
    @Type
    static final String TYPE_COMMENTS = "comments";
    @Type
    static final String TYPE_TEXT = "text";

    static final String METADATA_DATATYPE = "datatype";
    static final String VALUE = "#value#";

    private static CustomFields sInstance;

    private final Set<Field> mFields = new HashSet<>();

    private CustomFields() {
        mFields.add(new Field("#read", DBKeys.KEY_READ, TYPE_BOOL));

        mFields.add(new Field("#read_start", DBKeys.KEY_READ_START, TYPE_DATETIME));
        mFields.add(new Field("#read_end", DBKeys.KEY_READ_END, TYPE_DATETIME));
        mFields.add(new Field("#date_read", DBKeys.KEY_READ_END, TYPE_DATETIME));

        mFields.add(new Field("#notes", DBKeys.KEY_PRIVATE_NOTES, TYPE_TEXT));
        mFields.add(new Field("#notes", DBKeys.KEY_PRIVATE_NOTES, TYPE_COMMENTS));
    }

    public static Set<Field> getFields() {
        if (sInstance == null) {
            sInstance = new CustomFields();
        }
        return sInstance.mFields;
    }

    @StringDef({TYPE_BOOL, TYPE_DATETIME, TYPE_TEXT, TYPE_COMMENTS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {

    }

    public static class Field
            implements Parcelable {

        public static final Creator<Field> CREATOR = new Creator<Field>() {
            @Override
            public Field createFromParcel(@NonNull final Parcel in) {
                return new Field(in);
            }

            @Override
            public Field[] newArray(final int size) {
                return new Field[size];
            }
        };
        @NonNull
        final String calibreKey;
        @NonNull
        final String dbKey;
        @NonNull
        @Type
        final String type;

        Field(@NonNull final String calibreKey,
              @NonNull final String dbKey,
              @NonNull @Type final String type) {
            this.calibreKey = calibreKey;
            this.dbKey = dbKey;
            this.type = type;
        }

        protected Field(@NonNull final Parcel in) {
            //noinspection ConstantConditions
            calibreKey = in.readString();
            //noinspection ConstantConditions
            dbKey = in.readString();
            //noinspection ConstantConditions
            type = in.readString();
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(calibreKey);
            dest.writeString(dbKey);
            dest.writeString(type);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
