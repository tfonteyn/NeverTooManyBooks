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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Co-ordinate multiple {@link BookSearch}.
 */
public class SearchCoordinator
        extends ViewModel
        implements Cancellable {

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";

    protected final MutableLiveData<LiveDataEvent<TaskProgress>>
            searchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<LiveDataEvent<Boolean>>
            searchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Boolean>>
            searchCoordinatorFinished = new MutableLiveData<>();

    private final Queue<BookSearchResult> searchCoordinatorFinishedQueue =
            new ConcurrentLinkedQueue<>();
    private final Queue<BookSearchResult> searchCoordinatorCancelledQueue =
            new ConcurrentLinkedQueue<>();

    /**
     * key: search_id.
     * Added when the first task for this search is started,
     * removed when the last tasks for this search finishes.
     */
    private final Map<Integer, BookSearch> activeSearches = new HashMap<>();
    private final Map<Integer, Integer> task2searchId = new ConcurrentHashMap<>();

    /** Flag indicating we're shutting down. */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, TaskProgress> progressByEngineId = new EnumMap<>(EngineId.class);

    /** Caches the locales for all created engines. */
    private final Map<EngineId, Locale> engineLocaleCache = new EnumMap<>(EngineId.class);
    // There is a SINGLE/shared listener for ALL tasks!
    private final TaskListener<Book> searchTaskListener = new SearchTaskListener();
    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both active and disabled sites.
     */
    private List<Site> sites;
    /** Base message for progress updates. */
    @Nullable
    private String baseMessage;

    /** Cached string resource. */
    private String listElementPrefixString;

    /**
     * Process the message and start another task if required.
     *
     * @param taskId of task; this is the engine id.
     * @param result of a search;
     *               Will never be {@code null} for successful searches.
     *               MAY be {@code null} for cancelled searches.
     *               WILL be {@code null} for failed searches.
     */
    private synchronized void onSearchTaskFinished(final int taskId,
                                                   @Nullable final Book result) {
        final BookSearch currentSearch = getBookSearch(taskId);
        if (currentSearch != null) {
            final SearchTask currentTask = currentSearch.removeTask(taskId);
            task2searchId.remove(taskId);
            onSearchTaskFinished(currentSearch, currentTask, result);
        }
    }

    private void onSearchTaskFinished(@NonNull final BookSearch currentSearch,
                                      @NonNull final SearchTask currentTask,
                                      @Nullable final Book result) {

        final EngineId engineId = currentTask.getSearchEngine().getEngineId();

        if (BuildConfig.DEBUG /* always */) {
            currentSearch.debugSearchTaskFinished(currentTask.getTaskId(), engineId);
        }

        // ALWAYS store, even when the result was null!
        // Presence of the site/task id in the map is an indication that the site was processed
        synchronized (activeSearches) {
            currentSearch.addResult(engineId, currentTask.getSearchBy(), result);
        }

        // clear obsolete progress status
        synchronized (progressByEngineId) {
            progressByEngineId.remove(engineId);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();
        boolean searchStarted = false;

        if (!cancelRequested.get()) {
            //  update our listener with the current progress status
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
            }

            // Start new search(es) as needed/allowed.
            if (currentSearch.isWaitingForIsbnOrCode()) {
                if (result != null && result.hasIsbn()) {
                    // Start the remaining searches, even if they have run before.
                    // They will redo the search WITH the ISBN/code.
                    // Replace the search text with the (we hope) exact ISBN/code we just found.
                    // Worst case, explicitly use an empty string.
                    currentSearch.getCriteria().setIsbnText(result.getIsbn());
                    searchStarted = startConcurrentSearch(context, currentSearch);
                } else {
                    // sequentially start the next search which has not run yet.
                    searchStarted = startNextSearch(context, currentSearch);
                }
            }
        }

        // Check if the search to which the finished task belonged, is fully done.
        final boolean currentIsDone;
        synchronized (activeSearches) {
            // If we didn't start a new search (which might not be active yet!),
            // and there are no previous searches still running (or we got cancelled)
            // then we are done.
            currentIsDone = !searchStarted && (!currentSearch.isActive() || cancelRequested.get());
            if (currentIsDone) {
                activeSearches.remove(currentSearch.getId());
            }
        }

        // it is, report back to the user
        if (currentIsDone) {
            final BookSearchResult data = currentSearch.finish(context, engineLocaleCache);
            if (cancelRequested.get()) {
                pushResultCanceled(data);
            } else {
                pushResultFinished(data);
            }
        }
    }

    /**
     * Lookup the {@link BookSearch} which the given task belongs to.
     *
     * @param taskId to lookup
     *
     * @return BookSearch, can be {@code null} if already removed
     */
    @Nullable
    private BookSearch getBookSearch(final int taskId) {
        synchronized (activeSearches) {
            final Integer searchId = task2searchId.get(taskId);
            if (searchId == null) {
                return null;
            }
            return activeSearches.get(searchId);
        }
    }

    protected void pushResultFinished(@NonNull final BookSearchResult data) {
        searchCoordinatorFinishedQueue.add(data);
        searchCoordinatorFinished.setValue(LiveDataEvent.of(true));

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger()
                         .d(TAG, "pushResultFinished",
                            "searchId=" + data.getSearchId(),
                            "searchErrors=" + data.getErrorMessage());
        }
    }

    protected void pushResultCanceled(@NonNull final BookSearchResult data) {
        searchCoordinatorCancelledQueue.add(data);
        searchCoordinatorCancelled.setValue(LiveDataEvent.of(true));

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger()
                         .d(TAG, "pushResultCanceled",
                            "searchId=" + data.getSearchId(),
                            "searchErrors=" + data.getErrorMessage());
        }
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskFailed(final int taskId,
                                                 @Nullable final Throwable e) {
        final BookSearch currentSearch = getBookSearch(taskId);
        if (currentSearch != null) {
            final SearchTask currentTask = currentSearch.removeTask(taskId);
            task2searchId.remove(taskId);

            synchronized (currentSearch) {
                // Always store, even if the Exception is null
                currentSearch.addError(currentTask.getSearchEngine().getEngineId(), e);
            }
            onSearchTaskFinished(currentSearch, currentTask, null);
        }
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskProgress(@NonNull final TaskProgress message) {
        synchronized (progressByEngineId) {
            final BookSearch currentSearch = getBookSearch(message.taskId);
            if (currentSearch != null) {
                final SearchTask currentTask = currentSearch.getTask(message.taskId);
                if (currentTask != null) {
                    final EngineId engineId = currentTask.getSearchEngine().getEngineId();
                    progressByEngineId.put(engineId, message);
                }
            }
            // forward the accumulated progress
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
            }
        }
    }

    /**
     * Observable to receive progress.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onSearchProgress() {
        return searchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     *
     * @return book data
     *
     * @see #pollFinishedQueue()
     * @see #retriggerSearchFinished()
     */
    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onSearchFinished() {
        return searchCoordinatorFinished;
    }

    @Nullable
    public BookSearchResult pollFinishedQueue() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger()
                         .d(TAG, "pollFinishedQueue",
                            "size=" + searchCoordinatorFinishedQueue.size());
        }
        return searchCoordinatorFinishedQueue.poll();
    }

    public void retriggerSearchFinished() {
        if (!searchCoordinatorFinishedQueue.isEmpty()) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger()
                             .d(TAG, "retriggerSearchFinished",
                                "size=" + searchCoordinatorFinishedQueue.size());
            }
            searchCoordinatorFinished.setValue(LiveDataEvent.of(true));
        }
    }

    /**
     * The result if the user cancelled the search.
     *
     * @return book data found so far
     *
     * @see #pollCancelledQueue()
     * @see #retriggerCancelledQueue()
     */
    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onSearchCancelled() {
        return searchCoordinatorCancelled;
    }

    @Nullable
    public BookSearchResult pollCancelledQueue() {
        return searchCoordinatorCancelledQueue.poll();
    }

    public void retriggerCancelledQueue() {
        if (!searchCoordinatorCancelledQueue.isEmpty()) {
            searchCoordinatorCancelled.setValue(LiveDataEvent.of(true));
        }
    }

    /**
     * Creates {@link TaskProgress} with the global/total progress of all tasks.
     *
     * @return instance
     */
    @NonNull
    private TaskProgress accumulateProgress() {

        int progressMax = 0;
        int progressCount = 0;

        final StringJoiner sb = new StringJoiner("\n");
        // Start with the base message if we have one.
        if (baseMessage != null && !baseMessage.isEmpty()) {
            sb.add(baseMessage);
        }

        synchronized (progressByEngineId) {
            if (!progressByEngineId.isEmpty()) {
                // Append each task message
                progressByEngineId
                        .values()
                        .stream()
                        .map(msg -> String.format(listElementPrefixString, msg.text))
                        .forEach(sb::add);

                // Accumulate the current & max values for each active task.
                for (final TaskProgress taskProgress : progressByEngineId.values()) {
                    progressMax += taskProgress.maxPosition;
                    progressCount += taskProgress.position;
                }
            }
        }

        return new TaskProgress(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                progressMax, progressCount, null);
    }


    @Override
    protected void onCleared() {
        cancel();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {
        if (sites == null) {
            sites = Site.Type.Data.getSites();
            listElementPrefixString = context.getString(R.string.list_element);
        }
    }

    /**
     * Cancel all searches.
     */
    @AnyThread
    public void cancel() {
        cancelRequested.set(true);
        synchronized (activeSearches) {
            activeSearches.values().forEach(BookSearch::cancel);
        }
    }

    /**
     * Cancel the given search.
     *
     * @param searchId to cancel
     */
    public void cancelSearch(final int searchId) {
        synchronized (activeSearches) {
            final BookSearch bookSearch = activeSearches.get(searchId);
            if (bookSearch != null) {
                bookSearch.cancel();
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelRequested.get();
    }

    /**
     * Check if a search is already running.
     *
     * @return {@code true} if there is at least one
     */
    public boolean isSearchActive() {
        synchronized (activeSearches) {
            return activeSearches.values().stream().anyMatch(BookSearch::isActive);
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with all sites <strong>active and disabled</strong>
     */
    @NonNull
    public List<Site> getSiteList() {
        return sites;
    }

    /**
     * Override the initial list. Can contain active and disabled equally.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final List<Site> sites) {
        this.sites.clear();
        this.sites.addAll(sites);
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional other criteria)
     * until we find an ISBN/code or until we searched all sites.
     * Once/if an ISBN/code is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN/code.
     *
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     *
     * @see #startSearch(Context, EngineId, BookSearch, boolean)
     */
    public int search(@NonNull final BookSearchCriteria criteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "search", criteria);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final BookSearch bookSearch = prepareSearch(context, criteria);

        if (criteria.hasSids() || criteria.hasValidIsbn()) {
            if (startConcurrentSearch(context, bookSearch)) {
                return bookSearch.getId();
            }
        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            if (startNextSearch(context, bookSearch)) {
                return bookSearch.getId();
            }
        }
        return 0;
    }

    /**
     * Search a single search on the given engine for the site specific book id (sid).
     *
     * @param engineId to use
     * @param criteria to search for
     *
     * @return the search-id, or {@code 0} if no search was started
     *
     * @throws IllegalArgumentException if #sid was invalid
     * @see #startSearch(Context, EngineId, BookSearch, boolean)
     */
    public int searchByExternalId(@NonNull final EngineId engineId,
                                  @NonNull final BookSearchCriteria criteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "searchByExternalId", criteria);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        final BookSearch bookSearch = prepareSearch(context, criteria);
        if (startSearch(context, engineId, bookSearch, false)) {
            return bookSearch.getId();
        }
        return 0;
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     *
     * @param context  Current context
     * @param criteria to search for
     *
     * @return new BookSearch instance
     *
     * @throws IllegalStateException    if the network is not already checked/available
     * @throws IllegalArgumentException if there are no criteria set
     */
    @NonNull
    private BookSearch prepareSearch(@NonNull final Context context,
                                     @NonNull final BookSearchCriteria criteria) {
        // reset flags
        cancelRequested.set(false);

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
                throw new IllegalStateException("Network should be checked before starting search");
            }

            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("Nothing to search for");
            }
        }

        return new BookSearch(sites, criteria, new ResultsAccumulator(
                context, ServiceLocator.getInstance()::getLanguages));
    }

    /**
     * Start <strong>all</strong>> searches, which have not been run yet, in parallel.
     *
     * @param context    Current context
     * @param bookSearch to search for
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     *
     * @see #startSearch(Context, EngineId, BookSearch, boolean)
     */
    private boolean startConcurrentSearch(@NonNull final Context context,
                                          @NonNull final BookSearch bookSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startConcurrentSearch: " + bookSearch.getId());
        }

        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        boolean atLeastOneStarted = false;
        final List<EngineId> activeEngines = bookSearch.getSites()
                                                       .stream()
                                                       .filter(Site::isActive)
                                                       .map(Site::getEngineId)
                                                       .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            if (!bookSearch.hasResult(engineId)) {
                if (startSearch(context, engineId, bookSearch, false)) {
                    atLeastOneStarted = true;
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start a single search on the next engine in the queue.
     *
     * @param context    Current context
     * @param bookSearch to search for
     *
     * @return {@code true} if a search was started, {@code false} if not
     *
     * @see #startSearch(Context, EngineId, BookSearch, boolean)
     */
    private boolean startNextSearch(@NonNull final Context context,
                                    @NonNull final BookSearch bookSearch) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startNextSearch: " + bookSearch.getId());
        }

        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final List<EngineId> activeEngines = bookSearch.getSites()
                                                       .stream()
                                                       .filter(Site::isActive)
                                                       .map(Site::getEngineId)
                                                       .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            if (!bookSearch.hasResult(engineId)) {
                final boolean started = startSearch(context, engineId, bookSearch, true);
                if (started) {
                    return true;
                }
                // else, loop to next site
            }
        }
        return false;
    }

    /**
     * Start the specified site search. This is where a search-task is actually started.
     * <p>
     * <strong>synchronized</strong> to make sure we start tasks in a serial manner.
     *
     * @param context           Current context
     * @param engineId          to search
     * @param bookSearch        to use
     * @param waitForIsbnOrCode flag
     *
     * @return {@code true} if the search was started.
     */
    private synchronized boolean startSearch(@NonNull final Context context,
                                             @NonNull final EngineId engineId,
                                             @NonNull final BookSearch bookSearch,
                                             final boolean waitForIsbnOrCode) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "startSearch",
                                        "bookSearch=" + bookSearch.getId(),
                                        "searchEngine=" + engineId.name(),
                                        "waitForIsbnOrCode=" + waitForIsbnOrCode);
        }
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final SearchEngineConfig config = engineId.getConfig();
        // Sanity check; should not happen when we get here... flw
        if (config == null) {
            return false;
        }

        final SearchEngine searchEngine = engineId.createSearchEngine(context);

        // Preserve the locales for use by the results-accumulator
        if (!engineLocaleCache.containsKey(engineId)) {
            engineLocaleCache.put(engineId, searchEngine.getLocale(context));
        }

        @Nullable
        final SearchTask task = SearchTask.createSearchTask(context,
                                                            bookSearch.getId(),
                                                            searchEngine,
                                                            bookSearch.getCriteria(),
                                                            searchTaskListener);

        if (task == null) {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        if (BuildConfig.DEBUG) {
            bookSearch.debugSearchTaskStarting(engineId, task.getTaskId(), waitForIsbnOrCode);
        }

        bookSearch.setWaitingForIsbnOrCode(waitForIsbnOrCode);

        synchronized (activeSearches) {
            activeSearches.put(bookSearch.getId(), bookSearch);
            task2searchId.put(task.getTaskId(), task.getSearchId());
            bookSearch.addTask(task);
        }

        task.startSearch();
        return true;
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
        this.baseMessage = baseMessage;
    }

    /**
     * Listener for <strong>individual</strong> search tasks.
     * There MUST only be ONE instance of this listener, shared between all tasks!
     * It serves as a translation layer between the standard TaskListener
     * and what the SearchCoordinator needs.
     * <p>
     * Dev. Note: and it's an inner class because if we simply create it as an anonymous class
     * as we normally would/should, or ...
     * 2024-04-20: Android Studio is completely [censored]ing up the code formatting in this class!
     * Each time we format the code, methods and variables jump around.
     * https://youtrack.jetbrains.com/issue/IDEA-311599/Poor-result-from-Rearrange-Code-for-Java
     */
    private final class SearchTaskListener
            implements TaskListener<Book> {

        @Override
        public void onProgress(@NonNull final TaskProgress message) {
            onSearchTaskProgress(message);
        }

        @Override
        public void onFinished(final int taskId,
                               @Nullable final Book result) {
            // The result MUST NOT be null
            onSearchTaskFinished(taskId, Objects.requireNonNull(result, "result"));
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final Book result) {
            onSearchTaskFinished(taskId, result);
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Throwable e) {
            onSearchTaskFailed(taskId, e);
        }
    }
}
