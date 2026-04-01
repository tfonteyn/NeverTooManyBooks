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

package com.hardbacknutter.prefslib;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.ArrayRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.hardbacknutter.prefslib.internal.DefaultDialogFactory;
import com.hardbacknutter.prefslib.internal.HeaderDivider;
import com.hardbacknutter.prefslib.internal.SettingViewHolder;
import com.hardbacknutter.prefslib.internal.SettingsAdapter;
import com.hardbacknutter.prefslib.internal.UICallback;
import com.hardbacknutter.prefslib.internal.VerticalSpaceItemDecoration;
import com.hardbacknutter.util.livedataevent.LiveDataEvent;

/**
 * Delegate to handle the {@code RecyclerView}, adapter and ViewModel.
 *
 * <ol>
 *     <li>The {@link SettingViewHolder}s have {@code onClickListener}s on their Views.</li>
 *     <li>Those listeners will call back to the
 *         {@link SettingsManagerViewModel} using {@link UICallback}.
 *     </li>
 *     <li>{@link SettingsManagerViewModel} provides {@code LiveData}
 *          which this class will observe</li>
 *     <li>Dialog request will be handled by using the {@link SettingsDialogFactory}</li>
 *     <li>Change and Click request are first send to the client listeners
 *         for approval and any special handling</li>
 *         <li>Once approved, the new value is set on on the
 *         {@link Setting} and sent to the {@link SettingsDataStore}</li>
 * </ol>
 */
public final class SettingsManager {

    @NonNull
    private final Fragment owner;
    @NonNull
    private final RecyclerView recyclerView;

    @NonNull
    private final SettingsManagerViewModel vm;
    @NonNull
    private final SettingsAdapter adapter;

    @NonNull
    private final SettingsDialogFactory dialogFactory;

    @NonNull
    private final Map<String, OnSettingClickListener> onClickCallbacks;
    @Nullable
    private final OnSettingClickListener onSettingClickListener;

    @NonNull
    private final Map<String, OnSettingChangeListener> onChangeCallbacks;
    @Nullable
    private final OnSettingChangeListener onSettingChangeListener;

    /**
     * Constructor.
     *
     * @param owner        hosting fragment
     * @param recyclerView view
     * @param builder      bob the
     */
    private SettingsManager(@NonNull final Fragment owner,
                            @NonNull final RecyclerView recyclerView,
                            @NonNull final Builder builder) {

        this.owner = owner;
        this.recyclerView = recyclerView;

        this.onClickCallbacks = builder.onClickCallbacks;
        this.onSettingClickListener = builder.clickListener;

        onChangeCallbacks = builder.onChangeCallbacks;
        this.onSettingChangeListener = builder.changedListener;

        this.dialogFactory = builder.dialogFactory;

        //noinspection DataFlowIssue
        vm = new ViewModelProvider(owner.getActivity()).get(SettingsManagerViewModel.class);
        vm.init(builder.dataStore, builder.settings);

        final LifecycleOwner viewLifecycleOwner = owner.getViewLifecycleOwner();
        vm.onShowDialog().observe(viewLifecycleOwner, this::onShowDialog);
        vm.onClick().observe(viewLifecycleOwner, this::onClick);
        vm.onChanged().observe(viewLifecycleOwner, this::onChange);

        adapter = new SettingsAdapter(vm.getSettings(), builder.sortRoot, vm);

        this.recyclerView.setAdapter(adapter);

        final Context context = recyclerView.getContext();
        this.recyclerView.addItemDecoration(new HeaderDivider(context));
        this.recyclerView.addItemDecoration(new VerticalSpaceItemDecoration(
                context.getResources().getDimensionPixelOffset(R.dimen.vertical_spacing)));
        this.recyclerView.setHasFixedSize(true);
    }

    /**
     * Scroll the display to the row for the given key.
     *
     * @param key to show
     */
    public void scrollToKey(@NonNull final CharSequence key) {
        adapter.findPosition(key).ifPresent(position -> {
            //noinspection DataFlowIssue
            recyclerView.getLayoutManager().scrollToPosition(position);
        });
    }

    /**
     * Perform a click-action on the row of the given key.
     *
     * @param key to click
     */
    public void performClick(@NonNull final CharSequence key) {
        adapter.findPosition(key)
               .ifPresent(position -> {
                   final SettingViewHolder viewHolder = (SettingViewHolder)
                           recyclerView.findViewHolderForAdapterPosition(position);
                   // paranoia
                   if (viewHolder != null) {
                       viewHolder.performClick();
                   }
               });
    }

    /**
     * Get the full/original list of {@link Setting}s.
     * <p>
     * The order <strong>may</strong> be different from what is displayed.
     *
     * @return settings
     */
    @NonNull
    public List<Setting> getSettings() {
        return vm.getSettings();
    }

    /**
     * Get the {@link Setting} for the given key.
     *
     * @param key to get
     * @param <S> type of the {@link Setting}
     *
     * @return setting
     *
     * @throws IllegalArgumentException (debug) if the key is unknown
     */
    @NonNull
    public <S extends Setting> S requireSetting(@NonNull final CharSequence key) {
        return vm.requireSetting(key);
    }

    /**
     * Load of all {@link Setting}s from the data store.
     */
    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private void loadAll() {
        final Context context = recyclerView.getContext();
        vm.getSettings().forEach(s -> s.load(context, vm.getDataStore()));
    }

    /**
     * Reload the given list of {@link Setting}s and force a UI refresh.
     *
     * @param context Current context
     * @param keys    the {@link Setting}s.
     */
    public void reload(@NonNull final Context context,
                       @NonNull final String... keys) {

        final Set<CharSequence> keySet = new HashSet<>(Arrays.asList(keys));
        for (final Setting p : vm.getSettings()) {
            if (keySet.contains(p.getKey())) {
                p.load(context, vm.getDataStore());
                adapter.findPosition(p.getKey())
                       .ifPresent(adapter::notifyItemChanged);
            }
        }
    }

    /**
     * Stores the new value, and updates the UI if applicable.
     *
     * @param p the {@link Setting}.
     */
    public void save(@NonNull final Setting p) {
        p.save(recyclerView.getContext(), vm.getDataStore());
        adapter.findPosition(p.getKey())
               .ifPresent(adapter::notifyItemChanged);
    }

    /**
     * Enable or disable a list of {@link Setting}s.
     * <p>
     * Silently ignores any key which is not present.
     *
     * @param enabled flag
     * @param keys    the {@link Setting}s to modify.
     */
    public void setEnabled(final boolean enabled,
                           @NonNull final String... keys) {
        setEnabled(enabled, Arrays.asList(keys));
    }

    /**
     * Enable or disable a list of {@link Setting}s.
     * <p>
     * Silently ignores any key which is not present.
     *
     * @param enabled flag
     * @param keys    the {@link Setting}s to modify.
     */
    public void setEnabled(final boolean enabled,
                           final List<String> keys) {
        adapter.setEnabled(enabled, keys);
    }

    /**
     * Set the visibility of a list of {@link Setting}s.
     * <p>
     * Silently ignores any key which is not present.
     *
     * @param keys the {@link Setting}s to modify, paired with the visibility flag.
     */
    public void setVisible(@NonNull final Map<String, Boolean> keys) {
        adapter.setVisible(keys);
    }

    /**
     * Get all visible children of the given header.
     * <p>
     * If no header for the given key is found, an empty list is returned.
     *
     * @param headerKey to check
     *
     * @return keys
     */
    @NonNull
    public List<String> getVisibleChildren(@NonNull final CharSequence headerKey) {
        return adapter.getVisibleChildren(headerKey);
    }

    private void onShowDialog(@NonNull final LiveDataEvent<Setting> message) {
        message.process(setting -> dialogFactory
                .create(recyclerView.getContext(), setting, null)
                .show(owner.getChildFragmentManager(), "setting_dialog"));
    }

    private void onClick(@NonNull final LiveDataEvent<Setting> message) {
        message.process(setting -> {
            // Give the Setting specific listener a chance to handle it
            final OnSettingClickListener callback = onClickCallbacks.get(setting.getKey());
            if (callback != null && callback.onClick(setting)) {
                return;
            }

            // Give the global listener a chance to handle it
            if (onSettingClickListener != null && onSettingClickListener.onClick(setting)) {
                return;
            }

            // Take default action for some types
            switch (setting.getType()) {
                case Boolean: {
                    // Click emulates clicking the actual switch
                    final BooleanSetting bs = (BooleanSetting) setting;
                    change(bs, !bs.isChecked());
                    break;
                }
                case Fragment: {
                    // Click starts the Fragment
                    //noinspection DataFlowIssue
                    ((FragmentSetting) setting).start(owner.getParentFragmentManager(),
                                                      owner.getContext().getClassLoader());
                    break;
                }
                case Group: {
                    // Click enters the group
                    adapter.navigateTo((SettingsGroup) setting);
                    break;
                }

                case Action:
                case Header:
                case String:
                case Float:
                case SingleChoice:
                case MultiChoice:
                    // Not applicable
                    break;
            }
        });
    }

    private void onChange(@NonNull final LiveDataEvent<ValueUpdate<Setting, Object>> message) {
        message.process(update -> change(update.getSetting(), update.getNewValue()));
    }

    private void change(@NonNull final Setting setting,
                        @Nullable final Object newObjectValue) {
        switch (setting.getType()) {
            case SingleChoice: {
                final SingleChoiceSetting p = (SingleChoiceSetting) setting;
                final CharSequence newValue = (CharSequence) newObjectValue;

                // If there was a change...
                if (!Objects.equals(p.getValue(), newValue)) {
                    // Give the Setting specific listener a chance to handle it
                    final OnSettingChangeListener callback = onChangeCallbacks.get(p.getKey());
                    if (callback != null && callback.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                        return;
                    }

                    // Give the global listener a chance to handle it
                    if (onSettingChangeListener != null
                        && onSettingChangeListener.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                    }
                }
                break;
            }
            case MultiChoice: {
                final MultiChoiceSetting p = (MultiChoiceSetting) setting;
                //noinspection unchecked
                final Set<String> newValue = (Set<String>) newObjectValue;

                // If there was a change
                if (!Objects.equals(p.getValue(), newValue)) {
                    // Give the Setting specific listener a chance to handle it
                    final OnSettingChangeListener callback = onChangeCallbacks.get(p.getKey());
                    if (callback != null && callback.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                        return;
                    }

                    // Give the global listener a chance to handle it
                    if (onSettingChangeListener != null
                        && onSettingChangeListener.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                    }
                }
                break;
            }
            case Float: {
                final FloatSetting p = (FloatSetting) setting;
                final float newValue = newObjectValue != null ? (float) newObjectValue : 0;
                // If there was a change
                if (!p.isValueEquals(newValue)) {
                    // Give the Setting specific listener a chance to handle it
                    final OnSettingChangeListener callback = onChangeCallbacks.get(p.getKey());
                    if (callback != null && callback.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                        return;
                    }

                    // Give the global listener a chance to handle it
                    if (onSettingChangeListener != null
                        && onSettingChangeListener.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                    }
                }
                break;
            }
            case String: {
                final StringSetting p = (StringSetting) setting;
                final String newValue = (String) newObjectValue;
                // If there was a change
                if (!Objects.equals(p.getValue(), newValue)) {
                    // Give the Setting specific listener a chance to handle it
                    final OnSettingChangeListener callback = onChangeCallbacks.get(p.getKey());
                    if (callback != null && callback.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                        return;
                    }

                    // Give the global listener a chance to handle it
                    if (onSettingChangeListener != null
                        && onSettingChangeListener.onChange(p, newValue)) {
                        // perform the update
                        p.setValue(newValue);
                        save(p);
                    }
                }
                break;
            }
            case Boolean: {
                final BooleanSetting p = (BooleanSetting) setting;
                final boolean newValue = newObjectValue != null && (boolean) newObjectValue;
                // If there was a change
                if (!Objects.equals(p.isChecked(), newObjectValue)) {
                    // Give the Setting specific listener a chance to handle it
                    final OnSettingChangeListener callback = onChangeCallbacks.get(p.getKey());
                    if (callback != null && callback.onChange(p, newValue)) {
                        // perform the update
                        p.setChecked(newValue);
                        save(p);
                        return;
                    }

                    // Give the global listener a chance to handle it
                    if (onSettingChangeListener != null
                        && onSettingChangeListener.onChange(p, newValue)) {
                        // perform the update
                        p.setChecked(newValue);
                        save(p);
                    }
                }
                break;
            }

            case Action:
            case Fragment:
            case Group:
            case Header:
                // Not applicable
                break;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static final class Builder {

        @NonNull
        private final Context context;
        private final List<Setting> settings = new ArrayList<>();
        @NonNull
        private final SettingsDataStore dataStore;
        private final Map<String, OnSettingClickListener> onClickCallbacks = new HashMap<>();
        private final Map<String, OnSettingChangeListener> onChangeCallbacks = new HashMap<>();
        private boolean sortRoot;
        private SettingsDialogFactory dialogFactory;
        @Nullable
        private OnSettingChangeListener changedListener;
        @Nullable
        private OnSettingClickListener clickListener;

        /**
         * Constructor.
         *
         * @param context   Current context
         * @param dataStore to use
         */
        public Builder(@NonNull final Context context,
                       @NonNull final SettingsDataStore dataStore) {
            this.context = context;
            this.dataStore = dataStore;
        }

        /**
         * Set the top sort flag.
         *
         * @param sortRoot Whether to sort the first category of settings
         *                 if they have no header.
         *                 Ignored if the first element is a header.
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setSortRoot(final boolean sortRoot) {
            this.sortRoot = sortRoot;
            return this;
        }

        /**
         * Set a custom {@link SettingsDialogFactory}.
         *
         * @param factory to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setDialogFactory(@NonNull final SettingsDialogFactory factory) {
            this.dialogFactory = factory;
            return this;
        }

        /**
         * Set the optional {@link OnSettingChangeListener} handler.
         * Triggered when an option is changed by the user.
         *
         * @param listener to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setChangedListener(@NonNull final OnSettingChangeListener listener) {
            this.changedListener = listener;
            return this;
        }

        /**
         * Set the optional global {@link OnSettingClickListener} handler.
         * This is the secondary callback if there is no specific/private setting callback
         * configured.
         * Triggered when an option is tapped by the user.
         *
         * @param listener to use
         *
         * @return {@code this} (for chaining)
         */
        @NonNull
        public Builder setClickListener(@NonNull final OnSettingClickListener listener) {
            this.clickListener = listener;
            return this;
        }

        /**
         * Add a setting.
         *
         * @param setting to add
         */
        private void add(@NonNull final Setting setting) {
            settings.add(setting);
        }

        /**
         * Add a setting.
         *
         * @param setting         to add
         * @param onClickListener (optional) callback when clicked
         */
        private void add(@NonNull final Setting setting,
                         @Nullable final OnSettingClickListener onClickListener) {
            settings.add(setting);
            if (onClickListener != null) {
                onClickCallbacks.put(setting.getKey(), onClickListener);
            }
        }

        /**
         * Add a setting.
         *
         * @param setting          to add
         * @param onChangeListener (optional) callback when the setting is changed
         */
        private void add(@NonNull final Setting setting,
                         @Nullable final OnSettingChangeListener onChangeListener) {
            settings.add(setting);
            if (onChangeListener != null) {
                onChangeCallbacks.put(setting.getKey(), onChangeListener);
            }
        }

        /**
         * Add a simple {@link HeaderSetting}.
         *
         * @param title string resource
         */
        public void header(@StringRes final int title) {
            header("", title, null);
        }

        /**
         * Add a simple {@link HeaderSetting}.
         *
         * @param key   for the setting
         * @param title string resource
         */
        public void header(@NonNull final String key,
                           @StringRes final int title) {
            header(key, title, null);
        }

        /**
         * Add a simple {@link HeaderSetting}.
         *
         * @param title string resource
         * @param p     (optional) callback for further customization
         */
        public void header(@StringRes final int title,
                           @Nullable final Consumer<HeaderSetting> p) {
            header("", title, p);
        }

        /**
         * Add a simple {@link HeaderSetting}.
         *
         * @param key   for the setting
         * @param title string resource
         * @param p     (optional) callback for further customization
         */
        public void header(@NonNull final String key,
                           @StringRes final int title,
                           @Nullable final Consumer<HeaderSetting> p) {
            final HeaderSetting s = new HeaderSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s);
        }

        /**
         * Create and add a {@link BooleanSetting}.
         * A convenience method for basic on/off settings.
         * <p>
         * The summaries for {@code false/true} have <strong>NO</strong>
         * default and will not be set.
         *
         * @param key      for the setting
         * @param title    string resource
         * @param defValue default value
         *
         * @see #bool(String, int, int, int, OnSettingChangeListener, Consumer)
         */
        public void bool(@NonNull final String key,
                         @StringRes final int title,
                         final boolean defValue) {

            final BooleanSetting s = new BooleanSetting(key);
            s.setTitle(context.getString(title));
            s.setChecked(defValue);
            add(s);
        }

        /**
         * Create and add a {@link BooleanSetting}.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         *
         * @see #bool(String, int, boolean)
         */
        public void bool(@NonNull final String key,
                         @StringRes final int title,
                         @Nullable final OnSettingChangeListener onChangeListener,
                         @Nullable final Consumer<BooleanSetting> p) {
            bool(key, title, 0, 0, onChangeListener, p);
        }

        /**
         * Create and add a {@link BooleanSetting}.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param summaryFalse     string resource for the {@code false} summary text
         * @param summaryTrue      string resource for the {@code true} summary text
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         *
         * @see #bool(String, int, boolean)
         */
        public void bool(@NonNull final String key,
                         @StringRes final int title,
                         @StringRes final int summaryFalse,
                         @StringRes final int summaryTrue,
                         @Nullable final OnSettingChangeListener onChangeListener,
                         @Nullable final Consumer<BooleanSetting> p) {
            final BooleanSetting s = new BooleanSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }
            if (summaryFalse != 0) {
                s.setSummaryFalse(context.getString(summaryFalse));
            }
            if (summaryTrue != 0) {
                s.setSummaryTrue(context.getString(summaryTrue));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a {@link FloatSetting}.
         * Use this also for {@code int} values and cast the result.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param valueFrom        lowest valid value
         * @param valueTo          highest valid value
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void floatRange(@NonNull final String key,
                               @StringRes final int title,
                               final float valueFrom,
                               final float valueTo,
                               @Nullable final OnSettingChangeListener onChangeListener,
                               @Nullable final Consumer<FloatSetting> p) {
            final FloatSetting s = new FloatSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }
            s.setValueFrom(valueFrom);
            s.setValueTo(valueTo);

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a {@link StringSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void text(@NonNull final String key,
                         @StringRes final int title,
                         @Nullable final OnSettingChangeListener onChangeListener,
                         @Nullable final Consumer<StringSetting> p) {
            text(key, title, 0, 0, 0,
                 onChangeListener, p);
        }

        /**
         * Create and add a {@link StringSetting}.
         *
         * @param key                for the setting
         * @param title              string resource
         * @param notSetSummary      string resource for the {@code not set} summary text
         * @param negativeButtonText string resource for the negative button of the dialog box
         * @param positiveButtonText string resource for the positive button of the dialog box
         * @param onChangeListener   (optional) callback when the setting is changed
         * @param p                  (optional) callback for further customization
         */
        public void text(@NonNull final String key,
                         @StringRes final int title,
                         @StringRes final int notSetSummary,
                         @StringRes final int negativeButtonText,
                         @StringRes final int positiveButtonText,
                         @Nullable final OnSettingChangeListener onChangeListener,
                         @Nullable final Consumer<StringSetting> p) {
            final StringSetting s = new StringSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (notSetSummary != 0) {
                s.setNotSetSummary(context.getString(notSetSummary));
            } else {
                s.setNotSetSummary(context.getString(R.string.preference_not_set));
            }
            if (negativeButtonText != 0) {
                s.setNegativeButtonText(context.getString(negativeButtonText));
            } else {
                s.setNegativeButtonText(context.getString(R.string.action_cancel));
            }
            if (positiveButtonText != 0) {
                s.setPositiveButtonText(context.getString(positiveButtonText));
            } else {
                s.setPositiveButtonText(context.getString(R.string.action_ok));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a {@link PasswordSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void password(@NonNull final String key,
                             @StringRes final int title,
                             @Nullable final OnSettingChangeListener onChangeListener,
                             @Nullable final Consumer<PasswordSetting> p) {
            password(key, title, 0, 0, 0,
                     onChangeListener, p);
        }

        /**
         * Create and add a {@link PasswordSetting}.
         * <p>
         * Passwords can by default be {@code not set} using the dialogs neutral button.
         * Override in the callback when not wanted by calling
         * {@link PasswordSetting#setEmptyIsNotSet(boolean)}.
         *
         * @param key                for the setting
         * @param title              string resource
         * @param notSetSummary      string resource for the {@code not set} text
         * @param negativeButtonText string resource for the negative button of the dialog box
         * @param positiveButtonText string resource for the positive button of the dialog box
         * @param onChangeListener   (optional) callback when the setting is changed
         * @param p                  (optional) callback for further customization
         */
        public void password(@NonNull final String key,
                             @StringRes final int title,
                             @StringRes final int notSetSummary,
                             @StringRes final int negativeButtonText,
                             @StringRes final int positiveButtonText,
                             @Nullable final OnSettingChangeListener onChangeListener,
                             @Nullable final Consumer<PasswordSetting> p) {
            final PasswordSetting s = new PasswordSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            s.setEmptyIsNotSet(false);
            s.setNotSetButtonText(context.getString(R.string.preference_not_set));

            if (notSetSummary != 0) {
                s.setNotSetSummary(context.getString(notSetSummary));
            } else {
                s.setNotSetSummary(context.getString(R.string.preference_not_set));
            }
            if (negativeButtonText != 0) {
                s.setNegativeButtonText(context.getString(negativeButtonText));
            } else {
                s.setNegativeButtonText(context.getString(R.string.action_cancel));
            }
            if (positiveButtonText != 0) {
                s.setPositiveButtonText(context.getString(positiveButtonText));
            } else {
                s.setPositiveButtonText(context.getString(R.string.action_ok));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a {@link SingleChoiceSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         * <p>
         * The {@code entries} and {@code entryValues} should be set during callback
         * or at a later time before displaying.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void singleChoice(@NonNull final String key,
                                 @StringRes final int title,
                                 @Nullable final OnSettingChangeListener onChangeListener,
                                 @Nullable final Consumer<SingleChoiceSetting> p) {
            singleChoice(key, title, 0, 0,
                         0, 0,
                         onChangeListener, p);
        }

        /**
         * Create and add a {@link SingleChoiceSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param entries          optional array of entries
         * @param entryValues      optional array of entry values
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void singleChoice(@NonNull final String key,
                                 @StringRes final int title,
                                 @ArrayRes final int entries,
                                 @ArrayRes final int entryValues,
                                 @Nullable final OnSettingChangeListener onChangeListener,
                                 @Nullable final Consumer<SingleChoiceSetting> p) {
            singleChoice(key, title, entries, entryValues,
                         0, 0,
                         onChangeListener, p);
        }

        /**
         * Create and add a {@link SingleChoiceSetting}.
         *
         * @param key                for the setting
         * @param title              string resource
         * @param entries            optional array of entries
         * @param entryValues        optional array of entry values
         * @param notSetSummary      string resource for the {@code not set} summary text
         * @param negativeButtonText string resource for the negative button of the dialog box
         * @param onChangeListener   (optional) callback when the setting is changed
         * @param p                  (optional) callback for further customization
         */
        public void singleChoice(@NonNull final String key,
                                 @StringRes final int title,
                                 @ArrayRes final int entries,
                                 @ArrayRes final int entryValues,
                                 @StringRes final int notSetSummary,
                                 @StringRes final int negativeButtonText,
                                 @Nullable final OnSettingChangeListener onChangeListener,
                                 @Nullable final Consumer<SingleChoiceSetting> p) {
            final SingleChoiceSetting s = new SingleChoiceSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (notSetSummary != 0) {
                s.setNotSetSummary(context.getString(notSetSummary));
            } else {
                s.setNotSetSummary(context.getString(R.string.preference_not_set));
            }
            if (negativeButtonText != 0) {
                s.setNegativeButtonText(context.getString(negativeButtonText));
            } else {
                s.setNegativeButtonText(context.getString(R.string.action_cancel));
            }

            final Resources res = context.getResources();
            if (entries != 0) {
                s.setEntries(res.getStringArray(entries));
            }
            if (entryValues != 0) {
                s.setEntryValues(res.getStringArray(entryValues));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a {@link MultiChoiceSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void multiChoice(@NonNull final String key,
                                @StringRes final int title,
                                @Nullable final OnSettingChangeListener onChangeListener,
                                @Nullable final Consumer<MultiChoiceSetting> p) {
            multiChoice(key, title, 0, 0,
                        0, 0, 0, 0,
                        onChangeListener, p);
        }

        /**
         * Create and add a {@link MultiChoiceSetting}.
         * <p>
         * The {@code not set} summary text and dialog buttons will use a default text.
         *
         * @param key              for the setting
         * @param title            string resource
         * @param entries          optional array of entries
         * @param entryValues      optional array of entry values
         * @param onChangeListener (optional) callback when the setting is changed
         * @param p                (optional) callback for further customization
         */
        public void multiChoice(@NonNull final String key,
                                @StringRes final int title,
                                @ArrayRes final int entries,
                                @ArrayRes final int entryValues,
                                @Nullable final OnSettingChangeListener onChangeListener,
                                @Nullable final Consumer<MultiChoiceSetting> p) {
            multiChoice(key, title, entries, entryValues,
                        0, 0, 0, 0,
                        onChangeListener, p);
        }

        /**
         * Create and add a {@link MultiChoiceSetting}.
         *
         * @param key                for the setting
         * @param title              string resource
         * @param entries            optional array of entries
         * @param entryValues        optional array of entry values
         * @param notSetSummary      string resource for the {@code not set} summary text
         * @param clearButtonText string resource for the neutral (clear) button of the dialog box
         * @param negativeButtonText string resource for the negative button of the dialog box
         * @param positiveButtonText string resource for the positive button of the dialog box
         * @param onChangeListener   (optional) callback when the setting is changed
         * @param p                  (optional) callback for further customization
         */
        public void multiChoice(@NonNull final String key,
                                @StringRes final int title,
                                @ArrayRes final int entries,
                                @ArrayRes final int entryValues,
                                @StringRes final int notSetSummary,
                                @StringRes final int clearButtonText,
                                @StringRes final int negativeButtonText,
                                @StringRes final int positiveButtonText,
                                @Nullable final OnSettingChangeListener onChangeListener,
                                @Nullable final Consumer<MultiChoiceSetting> p) {
            final MultiChoiceSetting s = new MultiChoiceSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (notSetSummary != 0) {
                s.setNotSetSummary(context.getString(notSetSummary));
            } else {
                s.setNotSetSummary(context.getString(R.string.preference_not_set));
            }
            if (clearButtonText != 0) {
                s.setClearButtonText(context.getString(clearButtonText));
            } else {
                s.setClearButtonText(context.getString(R.string.action_clear));
            }
            if (negativeButtonText != 0) {
                s.setNegativeButtonText(context.getString(negativeButtonText));
            } else {
                s.setNegativeButtonText(context.getString(R.string.action_cancel));
            }
            if (positiveButtonText != 0) {
                s.setPositiveButtonText(context.getString(positiveButtonText));
            } else {
                s.setPositiveButtonText(context.getString(R.string.action_ok));
            }

            final Resources res = context.getResources();
            if (entries != 0) {
                s.setEntries(res.getStringArray(entries));
            }
            if (entryValues != 0) {
                s.setEntryValues(res.getStringArray(entryValues));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onChangeListener);
        }

        /**
         * Create and add a generic {@link ActionSetting}.
         * This will trigger an {@link OnSettingClickListener} when tapped by the user.
         *
         * @param key             for the setting
         * @param title           string resource
         * @param onClickListener (optional) callback when clicked
         * @param p               (optional) callback for further customization
         */
        public void action(@NonNull final String key,
                           @StringRes final int title,
                           @Nullable final OnSettingClickListener onClickListener,
                           @Nullable final Consumer<ActionSetting> p) {
            final ActionSetting s = new ActionSetting(key);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s, onClickListener);
        }

        /**
         * Create and add a generic  {@link FragmentSetting}.
         * This will start the given fragment when tapped by the user.
         *
         * @param key       for the setting
         * @param title     string resource
         * @param className of the fragment
         * @param container where to load the new fragment into
         * @param p         (optional) callback for further customization
         */
        public void fragment(@NonNull final String key,
                             @StringRes final int title,
                             @NonNull final String className,
                             @IdRes final int container,
                             @Nullable final Consumer<FragmentSetting> p) {
            final FragmentSetting s = new FragmentSetting(key, className, container);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            if (p != null) {
                p.accept(s);
            }
            add(s);
        }

        public void group(@NonNull final String key,
                          @StringRes final int title,
                          @NonNull final List<Setting> subSettings) {
            final SettingsGroup s = new SettingsGroup(key, subSettings);
            if (title != 0) {
                s.setTitle(context.getString(title));
            }

            add(s);
        }

        /**
         * Create the fully configured {@link SettingsManager}.
         *
         * @param owner        The Fragment owning this manager.
         * @param recyclerView The view to use
         *
         * @return manager
         */
        @NonNull
        public SettingsManager build(@NonNull final Fragment owner,
                                     @NonNull final RecyclerView recyclerView) {

            if (changedListener == null) {
                // Default: accept changes
                changedListener = (setting, newValue) -> true;
            }
            if (clickListener == null) {
                // Default: not handled
                clickListener = setting -> false;
            }

            if (dialogFactory == null) {
                dialogFactory = new DefaultDialogFactory();
            }

            final SettingsManager settingsManager = new SettingsManager(owner, recyclerView, this);
            settingsManager.loadAll();
            return settingsManager;
        }
    }
}
