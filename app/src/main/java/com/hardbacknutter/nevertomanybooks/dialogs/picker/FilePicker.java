package com.hardbacknutter.nevertomanybooks.dialogs.picker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Present a list of files for selection.
 */
public class FilePicker
        extends ValuePicker {

    /**
     * Constructor.
     *
     * @param context Current context
     * @param title   for the dialog
     * @param message optional message
     * @param files   list to choose from
     * @param handler which will receive the selected row item
     */
    public FilePicker(@NonNull final Context context,
                      @Nullable final String title,
                      @Nullable final String message,
                      @NonNull final List<File> files,
                      @NonNull final PickListener<File> handler) {
        super(context, title, message, true);

        final FileItemListAdapter adapter = new FileItemListAdapter(context, files, item -> {
            dismiss();
            handler.onPicked(item);
        });
        setAdapter(adapter, 0);
    }

    private static class FileItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final Locale mLocale;
        @NonNull
        private final List<File> mList;
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final PickListener<File> mListener;

        FileItemListAdapter(@NonNull final Context context,
                            @NonNull final List<File> list,
                            @NonNull final PickListener<File> listener) {

            mInflater = LayoutInflater.from(context);
            mListener = listener;
            mLocale = LocaleUtils.from(context);
            mList = list;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View root = mInflater.inflate(R.layout.row_file_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Context context = mInflater.getContext();

            File item = mList.get(position);
            holder.nameView.setText(item.getName());
            holder.pathView.setText(item.getParent());
            holder.sizeView.setText(StorageUtils.formatFileSize(context, item.length()));
            holder.lastModDateView
                    .setText(DateUtils.toPrettyDateTime(mLocale, new Date(item.lastModified())));

            // onClick on the whole view.
            holder.itemView.setOnClickListener(v -> mListener.onPicked(item));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView pathView;
        @NonNull
        final TextView sizeView;
        @NonNull
        final TextView lastModDateView;


        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
            pathView = itemView.findViewById(R.id.path);
            sizeView = itemView.findViewById(R.id.size);
            lastModDateView = itemView.findViewById(R.id.date);
        }
    }
}
