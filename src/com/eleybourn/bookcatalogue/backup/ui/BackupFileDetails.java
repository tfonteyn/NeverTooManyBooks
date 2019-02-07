package com.eleybourn.bookcatalogue.backup.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.archivebase.BackupInfo;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment.FileDetails;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implementation of {@link FileDetails} that collects data about backup files
 * in a background thread.
 *
 * @author pjw
 */
public class BackupFileDetails
        implements FileDetails, Parcelable {

    /** {@link Parcelable}. */
    public static final Parcelable.Creator<BackupFileDetails> CREATOR =
            new Parcelable.Creator<BackupFileDetails>() {
                public BackupFileDetails createFromParcel(@NonNull final Parcel source) {
                    return new BackupFileDetails(source);
                }

                public BackupFileDetails[] newArray(final int size) {
                    return new BackupFileDetails[size];
                }
            };

    /** @see #isArchive(File) */
    public static final String ARCHIVE_EXTENSION = ".bcbk";
    /** prefix. */
    public static final String ARCHIVE_PREFIX = "BookCatalogue-";

    /** File for this item. */
    @NonNull
    private final File mFile;
    /** The BackupInfo we use when displaying the object. */
    @Nullable
    private BackupInfo mInfo;

    /**
     * Constructor.
     */
    BackupFileDetails(@NonNull final File file) {
        mFile = file;
    }

    /**
     * Constructor, using a Parcel as source.
     */
    private BackupFileDetails(@NonNull final Parcel in) {
        mFile = new File(in.readString());
        // flag to indicate the Parcel has the info bundle
        boolean hasInfo = (in.readByte() != 0);
        if (hasInfo) {
            mInfo = in.readParcelable(getClass().getClassLoader());
        } else {
            mInfo = null;
        }
    }

    /**
     * Simple check on the file being an archive.
     *
     * @param file to check
     *
     * @return <tt>true</tt> if it's an archive
     */
    public static boolean isArchive(@NonNull final File file) {
        return file.getName().toLowerCase().endsWith(ARCHIVE_EXTENSION);
    }

    public void setInfo(@NonNull final BackupInfo info) {
        mInfo = info;
    }

    @NonNull
    @Override
    public File getFile() {
        return mFile;
    }

    /**
     * Fill in the details for the view we returned above.
     */
    @Override
    public void onGetView(@NonNull final View convertView,
                          @NonNull final Context context) {

        // Set the basic data
        TextView filenameView = convertView.findViewById(R.id.name);
        filenameView.setText(mFile.getName());

        ImageView imageView = convertView.findViewById(R.id.icon);
        View fileDetails = convertView.findViewById(R.id.file_details);

        // For directories, hide the extra data
        if (mFile.isDirectory()) {
            imageView.setImageDrawable(context.getDrawable(R.drawable.ic_folder));
            fileDetails.setVisibility(View.GONE);
        } else {
            // Display details
            imageView.setImageDrawable(context.getDrawable(R.drawable.bc_archive));
            fileDetails.setVisibility(View.VISIBLE);

            TextView fileContentView = convertView.findViewById(R.id.file_content);
            TextView dateView = convertView.findViewById(R.id.date);

            TextView sizeView = convertView.findViewById(R.id.size);
            sizeView.setText(Utils.formatFileSize(mFile.length()));

            Resources res = context.getResources();
            if (mInfo != null) {
                List<String> args = new ArrayList<>();
                if (mInfo.hasBookCount()) {
                    args.add(res.getQuantityString(R.plurals.n_books, mInfo.getBookCount(),
                                                   mInfo.getBookCount()));
                } else if (mInfo.hasBooks()) {
                    args.add(res.getString(R.string.lbl_books));
                }
                if (mInfo.hasCoverCount()) {
                    args.add(res.getQuantityString(R.plurals.n_covers, mInfo.getCoverCount(),
                                                   mInfo.getCoverCount()));
                } else if (mInfo.hasCovers()) {
                    args.add(res.getString(R.string.lbl_covers));
                }

                if (mInfo.hasPreferences()) {
                    args.add(res.getString(R.string.lbl_preferences));
                }
                if (mInfo.hasBooklistStyles()) {
                    args.add(res.getString(R.string.lbl_styles));
                }

                // needs RTL
                fileContentView.setText(TextUtils.join(", ", args));

                Date creationDate = mInfo.getCreationDate();
                if (creationDate != null) {
                    dateView.setText(DateUtils.toPrettyDateTime(creationDate));
                }
                fileContentView.setVisibility(View.VISIBLE);
            } else {
                dateView.setText(DateUtils.toPrettyDateTime(new Date(mFile.lastModified())));
                fileContentView.setVisibility(View.GONE);
            }
        }
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Save all fields that must be persisted.
     */
    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeString(mFile.getAbsolutePath());
        if (mInfo != null) {
            // flag to indicate the Parcel has the info bundle
            dest.writeByte((byte) 1);
            dest.writeParcelable(mInfo, flags);
        } else {
            dest.writeByte((byte) 0);
        }
    }
}
