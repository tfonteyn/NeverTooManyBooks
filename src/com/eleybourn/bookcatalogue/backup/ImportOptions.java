package com.eleybourn.bookcatalogue.backup;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public class ImportOptions
        implements Parcelable {

    /*
     * options as to *what* should be exported.
     */
    public static final int BOOK_CSV = 1;
    public static final int PREFERENCES = 1 << 1;
    public static final int BOOK_LIST_STYLES = 1 << 2;
    public static final int COVERS = 1 << 3;
    public static final int XML_TABLES = 1 << 4;
    //public static final int IMPORT_5 = 1 << 5;
    //public static final int IMPORT_6 = 1 << 6;
    //public static final int IMPORT_7 = 1 << 7;
    // pointless to implement. Just here for mirroring export flags
    //public static final int DATABASE = 1 << 8;

    /** Options value to indicate ALL things should be exported. */
    public static final int ALL = BOOK_CSV | COVERS | BOOK_LIST_STYLES | PREFERENCES;
    public static final int NOTHING = 0;

    /**
     * 0: all books
     * 1: only new books and books with more recent update_date fields should be imported.
     */
    public static final int IMPORT_ONLY_NEW_OR_UPDATED = 1 << 16;
    public static final Creator<ImportOptions> CREATOR = new Creator<ImportOptions>() {
        @Override
        public ImportOptions createFromParcel(@NonNull final Parcel source) {
            return new ImportOptions(source);
        }

        @Override
        public ImportOptions[] newArray(final int size) {
            return new ImportOptions[size];
        }
    };
    /**
     * all defined flags.
     */
    static final int MASK = ALL | IMPORT_ONLY_NEW_OR_UPDATED;
    /**
     * Bitmask.
     */
    public int what;
    /**
     * File to import from.
     */
    @Nullable
    public File file;

    @Nullable
    public Importer.Results results;

    public ImportOptions() {
    }

    public ImportOptions(@NonNull final File file) {
        this.file = file;
    }

    /** {@link Parcelable}. */
    protected ImportOptions(@NonNull final Parcel in) {
        what = in.readInt();
        if (in.readInt() != 0) {
            file = new File(in.readString());
        }
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(what);

        if (file != null) {
            // has file
            dest.writeInt(1);
            dest.writeString(file.getPath());
        } else {
            // no file
            dest.writeInt(0);
        }
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "ImportOptions{"
                + "file=`" + file + '`'
                + "what=0%" + Integer.toBinaryString(what)
                + '}';
    }

    public void validate() {
    }
}
