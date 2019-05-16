package com.eleybourn.bookcatalogue.dialogs;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.RadioGroupRecyclerAdapter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;

public class StylePickerDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = StylePickerDialogFragment.class.getSimpleName();

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";
    private static final String BKEY_STYLE_LIST = TAG + ":styleList";

    private boolean mShowAllStyles;
    private BooklistStyle mCurrentStyle;

    private ArrayList<BooklistStyle> mList;
    private WeakReference<StyleChangedListener> mListener;

    /**
     * Constructor.
     *
     * @param all if {@code true} show all styles, otherwise only the preferred ones.
     */
    public static void newInstance(@NonNull final FragmentManager fm,
                                   ArrayList<BooklistStyle> list,
                                   @NonNull final BooklistStyle currentStyle,
                                   final boolean all) {


        StylePickerDialogFragment smf = new StylePickerDialogFragment();
        Bundle args = new Bundle();
        args.putParcelableArrayList(BKEY_STYLE_LIST,list);
        args.putParcelable(UniqueId.BKEY_STYLE, currentStyle);
        args.putBoolean(BKEY_SHOW_ALL_STYLES, all);
        smf.setArguments(args);
        smf.show(fm, TAG);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(final StyleChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        Bundle args = requireArguments();
        mCurrentStyle = args.getParcelable(UniqueId.BKEY_STYLE);
        mShowAllStyles = args.getBoolean(BKEY_SHOW_ALL_STYLES, false);
        mList = args.getParcelableArrayList(BKEY_STYLE_LIST);

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater()
                                 .inflate(R.layout.dialog_styles_menu, null);

        RecyclerView stylesListView = root.findViewById(R.id.styles);
        stylesListView.setHasFixedSize(true);
        stylesListView.setLayoutManager(new LinearLayoutManager(getContext()));

        //noinspection ConstantConditions
        RadioGroupRecyclerAdapter<BooklistStyle> adapter =
                new RadioGroupRecyclerAdapter<>(getContext(), mList, mCurrentStyle, v -> {
            BooklistStyle style = (BooklistStyle) v.getTag(R.id.TAG_ITEM);
            if (mListener.get() != null) {
                mListener.get().onStyleChanged(style);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onStyleChanged",
                                 "WeakReference to listener was dead");
                }
            }

            dismiss();
        });

        stylesListView.setAdapter(adapter);

        @StringRes
        int moreOrLess = mShowAllStyles ? R.string.menu_show_fewer_ellipsis
                                        : R.string.menu_show_more_ellipsis;

        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.title_select_style)
                .setView(root)
                .setNeutralButton(R.string.menu_customize_ellipsis, (d1, which1) -> {
                    Intent intent = new Intent(getContext(), PreferredStylesActivity.class);
                    startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                })
                .setPositiveButton(moreOrLess, (d, which) -> {
                    //noinspection ConstantConditions
                    StylePickerDialogFragment.newInstance(getFragmentManager(), mList,
                                                          mCurrentStyle, !mShowAllStyles);
                })
                .create();
    }

    public interface StyleChangedListener {

        void onStyleChanged(@NonNull final BooklistStyle style);
    }
}
