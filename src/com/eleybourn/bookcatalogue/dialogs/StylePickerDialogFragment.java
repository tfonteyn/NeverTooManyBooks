package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
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
import com.eleybourn.bookcatalogue.booklist.BooklistStyles;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.PreferredStylesActivity;

public class StylePickerDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = StylePickerDialogFragment.class.getSimpleName();

    private static final String BKEY_SHOW_ALL_STYLES = TAG + ":showAllStyles";

    private boolean mShowAllStyles;
    private BooklistStyle mCurrentStyle;

    private WeakReference<StyleChangedListener> mListener;

    /**
     * Constructor.
     *
     * @param all if {@code true} show all styles, otherwise only the preferred ones.
     */
    public static void newInstance(@NonNull final FragmentManager fm,
                                   @NonNull final BooklistStyle currentStyle,
                                   final boolean all) {


        StylePickerDialogFragment smf = new StylePickerDialogFragment();
        Bundle args = new Bundle();
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

        ArrayList<BooklistStyle> list;

        try (DAO db = new DAO()) {
            list = new ArrayList<>(BooklistStyles.getStyles(db, mShowAllStyles).values());
        }

        @SuppressWarnings("ConstantConditions")
        View root = getActivity().getLayoutInflater()
                                 .inflate(R.layout.dialog_styles_menu, null);

        RecyclerView listView = root.findViewById(R.id.styles);
        listView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        listView.setLayoutManager(linearLayoutManager);
//        listView.addItemDecoration(
//                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        //noinspection ConstantConditions
        RadioGroupRecyclerAdapter<BooklistStyle> adapter =
                new RadioGroupRecyclerAdapter<>(getContext(), list, mCurrentStyle, this::onStyleSelected);

        listView.setAdapter(adapter);

        @StringRes
        int moreOrLess = mShowAllStyles ? R.string.menu_show_fewer_ellipsis
                                        : R.string.menu_show_more_ellipsis;

        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.title_select_style)
                .setView(root)
                .setNeutralButton(R.string.menu_customize_ellipsis, (d, which) -> {
                    Intent intent = new Intent(getContext(), PreferredStylesActivity.class);
                    startActivityForResult(intent, UniqueId.REQ_NAV_PANEL_EDIT_STYLES);
                })
                .setPositiveButton(moreOrLess, (d, which) -> {
                    // note: simply reloading the list would make more sense, but preventing
                    // the dialog from closing is headache inducing. This is easier.
                    //noinspection ConstantConditions
                    StylePickerDialogFragment.newInstance(getFragmentManager(),
                                                          mCurrentStyle, !mShowAllStyles);
                })
                .create();
    }

    private void onStyleSelected(View v) {
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
    }

    public interface StyleChangedListener {

        void onStyleChanged(@NonNull final BooklistStyle style);
    }
}
