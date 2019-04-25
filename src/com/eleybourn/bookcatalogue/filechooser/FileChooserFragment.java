/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.filechooser;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.debug.MustImplementException;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Fragment to display a simple directory/file browser.
 *
 * @author pjw
 */
public class FileChooserFragment
        extends Fragment
        implements FileListerAsyncTask.FileListerListener {

    /** Fragment manager tag. */
    public static final String TAG = FileChooserFragment.class.getSimpleName();

    private static final String BKEY_ROOT_PATH = TAG + ":rootPath";
    private static final String BKEY_LIST = TAG + ":list";

    private File mRootPath;

    /** User clicks on the 'up' button. */
    private final OnClickListener onPathUpClickListener = (v) -> {
        String parent = mRootPath.getParent();
        if (parent == null) {
            UserMessage.showUserMessage(v, R.string.warning_no_parent_directory_found);
            return;
        }
        mRootPath = new File(parent);
        tellActivityPathChanged();
    };

    private EditText mFilenameView;
    private TextView mCurrentFolderView;

    /** Create an empty one in case we are rotated before generated. */
    @Nullable
    private ArrayList<FileDetails> mList = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param root     directory root to display
     * @param fileName default filename
     *
     * @return the instance
     */
    @NonNull
    public static FileChooserFragment newInstance(@NonNull final File root,
                                                  @NonNull final String fileName) {
        String path;
        // Turn the passed File into a directory
        if (root.isDirectory()) {
            path = root.getAbsolutePath();
        } else {
            path = root.getParent();
        }

        FileChooserFragment frag = new FileChooserFragment();
        Bundle args = new Bundle();
        args.putString(BKEY_ROOT_PATH, path);
        args.putString(UniqueId.BKEY_FILE_SPEC, fileName);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnPathChangedListener)) {
            throw new MustImplementException(context, OnPathChangedListener.class);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_chooser, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View view = requireView();
        mCurrentFolderView = view.findViewById(R.id.current_folder);
        mFilenameView = view.findViewById(R.id.file_name);

        if (savedInstanceState == null) {
            Bundle args = requireArguments();

            String path = args.getString(BKEY_ROOT_PATH);
            mRootPath = new File(Objects.requireNonNull(path));

            mCurrentFolderView.setText(mRootPath.getAbsolutePath());
            mFilenameView.setText(args.getString(UniqueId.BKEY_FILE_SPEC));
            tellActivityPathChanged();
        } else {
            String path = savedInstanceState.getString(BKEY_ROOT_PATH);
            mRootPath = new File(Objects.requireNonNull(path));

            ArrayList<FileDetails> list = savedInstanceState.getParcelableArrayList(BKEY_LIST);
            //noinspection ConstantConditions
            onGotFileList(mRootPath, list);
        }

        // 'up' directory
        view.findViewById(R.id.btn_path_up).setOnClickListener(onPathUpClickListener);
        mCurrentFolderView.setOnClickListener(onPathUpClickListener);

        requireActivity().getWindow()
                         .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                                                   | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    /**
     * Convenience method to tell our activity the path has changed.
     */
    private void tellActivityPathChanged() {
        ((OnPathChangedListener) requireActivity()).onPathChanged(mRootPath);
    }

    /**
     * Save our root path and list.
     */
    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(BKEY_ROOT_PATH, mRootPath.getAbsolutePath());
        outState.putParcelableArrayList(BKEY_LIST, mList);
    }

    /**
     * @return the selected file
     */
    @NonNull
    File getSelectedFile() {
        return new File(mRootPath.getAbsolutePath() +
                                File.separator + mFilenameView.getText().toString().trim());
    }

    /**
     * Display the list.
     *
     * @param root Root directory
     * @param list List of FileDetails
     */
    @Override
    public void onGotFileList(@NonNull final File root,
                              @NonNull final ArrayList<FileDetails> list) {
        mRootPath = root;
        mList = list;
        mCurrentFolderView.setText(mRootPath.getAbsolutePath());

        // Setup and display the list
        FileDetailsAdapter adapter = new FileDetailsAdapter(requireContext(), mList);
        RecyclerView listView = requireView().findViewById(android.R.id.list);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        listView.setHasFixedSize(true);
        listView.setAdapter(adapter);
    }


    /**
     * Interface that the containing Activity must implement. Called when user changes path.
     */
    public interface OnPathChangedListener {

        void onPathChanged(@NonNull File root);
    }

    /**
     * Interface for details of files in current directory.
     */
    public interface FileDetails
            extends Parcelable {

        /** Get the underlying File object. */
        @NonNull
        File getFile();

        void onBindViewHolder(@NonNull final FileDetailsHolder holder,
                              @NonNull Context context);
    }

    public static class FileDetailsHolder
            extends RecyclerView.ViewHolder {

        public final TextView filenameView;
        public final ImageView imageView;

        public final androidx.constraintlayout.widget.Group fileDetails;
        public final TextView fileContentView;
        public final TextView dateView;
        public final TextView sizeView;

        FileDetailsHolder(@NonNull final View convertView) {
            super(convertView);

            filenameView = convertView.findViewById(R.id.filename);
            imageView = convertView.findViewById(R.id.icon);

            fileDetails = convertView.findViewById(R.id.file_details);
            fileContentView = convertView.findViewById(R.id.file_content);
            dateView = convertView.findViewById(R.id.date);
            sizeView = convertView.findViewById(R.id.size);
        }
    }

    /**
     * List Adapter for FileDetails objects.
     */
    protected class FileDetailsAdapter
            extends RecyclerView.Adapter<FileDetailsHolder> {

        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final List<FileDetails> mList;

        FileDetailsAdapter(@NonNull final Context context,
                           @NonNull final List<FileDetails> items) {

            mInflater = LayoutInflater.from(context);
            mList = items;
        }

        @NonNull
        @Override
        public FileDetailsHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                    final int viewType) {
            View view = mInflater.inflate(R.layout.row_file_chooser, parent, false);
            return new FileDetailsHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final FileDetailsHolder holder,
                                     final int position) {

            final FileDetails item = mList.get(position);

            //noinspection ConstantConditions
            item.onBindViewHolder(holder, getContext());

            holder.itemView.setOnClickListener(v -> {
                if (item.getFile().isDirectory()) {
                    // go into the directory selected
                    mRootPath = item.getFile();
                    tellActivityPathChanged();
                } else {
                    // Put the name of the file into the filename field when clicked.
                    mFilenameView.setText(item.getFile().getName());
                }
            });
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }
}
