package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.Date;

/**
 * Implementation of {@link FileDetails} that collects data about backup files in a background thread.
 *
 * Implements Parcelable but is in fact never Parceled. See constructor/writeToParcel
 * -> the 'File' object was originally serialized, but is now (its name) parcelled. Neither is tested (by me)
 *
 * @author pjw
 */
public class BackupFileDetails implements FileDetails, Parcelable {

    public static final Parcelable.Creator<BackupFileDetails> CREATOR = new Parcelable.Creator<BackupFileDetails>() {
        public BackupFileDetails createFromParcel(final @NonNull Parcel in) {
            return new BackupFileDetails(in);
        }

        public BackupFileDetails[] newArray(final int size) {
            return new BackupFileDetails[size];
        }
    };

    /** @see #isArchive(File) */
    public static final String ARCHIVE_EXTENSION = ".bcbk";
    public static final String ARCHIVE_PREFIX = "BookCatalogue-";

    /** File for this item */
    @NonNull
    private final File mFile;
    /** The BackupInfo we use when displaying the object */
    @Nullable
    private BackupInfo mInfo;

    /**
     * Constructor
     */
    BackupFileDetails(final @NonNull File file) {
        mFile = file;
    }

    /**
     * Constructor, using a Parcel as source.
     */
    private BackupFileDetails(final @NonNull Parcel in) {
        // mFile = (File) in.readSerializable();
        mFile = new File(in.readString());
        // flag to indicate the Parcel has the info bundle
        byte infoFlag = in.readByte();
        if (infoFlag != (byte) 0) {
            // note we're only doing the Bundle, not the info object itself.
            mInfo = new BackupInfo(in.readBundle(getClass().getClassLoader()));
        } else {
            mInfo = null;
        }
    }

    public static boolean isArchive(final @NonNull File f) {
        return f.getName().toLowerCase().endsWith(ARCHIVE_EXTENSION);
    }

    /**
     * Accessor
     */
    public void setInfo(final @NonNull BackupInfo info) {
        mInfo = info;
    }

    @NonNull
    @Override
    public File getFile() {
        return mFile;
    }

    /**
     * Return the view we use.
     *
     * THIS SHOULD ALWAYS RETURN THE SAME VIEW. IT IS NOT A MULTI-TYPE LIST.
     */
    @Override
    public int getViewId() {
        return R.layout.backup_chooser_item;
    }

    /**
     * Fill in the details for the view we returned above.
     */
    @Override
    public void onGetView(final @NonNull View convertView, final @NonNull Context context) {

        // Set the basic data
        TextView filenameView = convertView.findViewById(R.id.name);
        filenameView.setText(mFile.getName());

        TextView dateView = convertView.findViewById(R.id.date);
        ImageView imageView = convertView.findViewById(R.id.icon);
        TextView detailsView = convertView.findViewById(R.id.details);

        // For directories, hide the extra data
        if (mFile.isDirectory()) {
            dateView.setVisibility(View.GONE);
            detailsView.setVisibility(View.GONE);
            imageView.setImageDrawable(context.getDrawable(R.drawable.ic_folder));
        } else {
            // Display date and backup details
            imageView.setImageDrawable(context.getDrawable(R.drawable.bc_archive));
            dateView.setVisibility(View.VISIBLE);
            String formattedFleSize = Utils.formatFileSize(mFile.length());
            Resources res = context.getResources();
            if (mInfo != null) {
                String books = res.getQuantityString(R.plurals.n_books, mInfo.getBookCount(), mInfo.getBookCount());
                if (mInfo.hasCoverCount()) {
                    String covers = res.getQuantityString(R.plurals.n_covers, mInfo.getCoverCount(), mInfo.getCoverCount());
                    detailsView.setText(res.getString(R.string.a_comma_b, books, covers));
                } else {
                    detailsView.setText(books);
                }
                //noinspection ConstantConditions
                dateView.setText(res.getString(R.string.a_comma_b, formattedFleSize,
                        DateUtils.toPrettyDateTime(mInfo.getCreateDate())));
                detailsView.setVisibility(View.VISIBLE);
            } else {
                dateView.setText(res.getString(R.string.a_comma_b, formattedFleSize,
                        DateUtils.toPrettyDateTime(new Date(mFile.lastModified()))));
                detailsView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * {@link Parcelable} INTERFACE.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Save all fields that must be persisted.
     */
    @Override
    public void writeToParcel(final @NonNull Parcel dest, final int flags) {
        dest.writeString(mFile.getAbsolutePath());
        // dest.writeSerializable(mFile);
        if (mInfo != null) {
            // flag to indicate the Parcel has the info bundle
            dest.writeByte((byte) 1);
            // note we're only doing the Bundle, not the info object itself.
            dest.writeBundle(mInfo.getBundle());
        } else {
            dest.writeByte((byte) 0);
        }
    }

}