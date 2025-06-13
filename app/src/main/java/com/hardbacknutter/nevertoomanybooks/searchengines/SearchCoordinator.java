/*
 * @Copyright 2018-2025 HardBackNutter
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

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.core.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.core.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.core.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.core.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.util.logger.Logger;
import com.hardbacknutter.util.logger.LoggerFactory;

/**
 * Co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #activeTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#getEngineId()} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements Cancellable {

    private static final String ERROR_UNKNOWN_TASK = "Unknown task=";

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";
    /** The data returned from the search can contain this key with error messages. */
    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    private static final AtomicInteger TASK_ID = new AtomicInteger();

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    protected final MutableLiveData<LiveDataEvent<TaskProgress>>
            searchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<Book>>
            searchCoordinatorFinished = new MutableLiveData<>();


    /**
     * List of Tasks being managed by *this* object.
     * key: taskId
     */
    private final Map<Integer, SearchTask> activeTasks = new HashMap<>();

    /** Flag indicating we're shutting down. */
    private final AtomicBoolean cancelRequested = new AtomicBoolean();

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, SearchResult> resultsByEngineId = new EnumMap<>(EngineId.class);

    /** Accumulates any errors from <strong>individual</strong> search tasks. */
    private final Map<EngineId, Throwable> errorsByEngineId = new EnumMap<>(EngineId.class);

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    private final Map<EngineId, TaskProgress> progressByEngineId = new EnumMap<>(EngineId.class);

    /** Caches all created engines for reuse. */
    private final Map<EngineId, SearchEngine> engineCache = new EnumMap<>(EngineId.class);
    // There is a SINGLE/shared listener for ALL tasks!
    private final TaskListener<Book> searchTaskListener = new SearchTaskListener();
    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both active and disabled sites.
     */
    private List<Site> allSites;
    /** Base message for progress updates. */
    @Nullable
    private String baseMessage;
    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean waitingForIsbnOrCode;

    /** DEBUG timer. */
    private long searchStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksStartTime;
    /** DEBUG timer. */
    private Map<EngineId, Long> searchTasksEndTime;

    /** Cached string resource. */
    private String listElementPrefixString;

    private ResultsAccumulator resultsAccumulator;

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

        final SearchTask searchTask;

        // Remove the finished task from our list
        synchronized (activeTasks) {
            searchTask = activeTasks.remove(taskId);
        }
        Objects.requireNonNull(searchTask, () -> ERROR_UNKNOWN_TASK + taskId);

        final EngineId engineId = searchTask.getSearchEngine().getEngineId();

        if (BuildConfig.DEBUG && (DEBUG_SWITCHES.SEARCH_COORDINATOR
                                  || DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS)) {
            debugSingleSearchFinished(engineId);
        }


        // ALWAYS store, even when null!
        // Presence of the site/task id in the map is an indication that the site was processed
        synchronized (resultsByEngineId) {
            resultsByEngineId.put(engineId, new SearchResult(engineId, searchTask.getSearchBy(),
                                                             result));
        }

        // clear obsolete progress status
        synchronized (progressByEngineId) {
            progressByEngineId.remove(engineId);
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Start new search(es) as needed/allowed.
        boolean searchStarted = false;
        final SearchCoordinatorCriteria criteria = searchTask.getCriteria();

        if (!cancelRequested.get()) {
            //  update our listener with the current progress status
            synchronized (searchCoordinatorProgress) {
                searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
            }

            if (waitingForIsbnOrCode) {
                if (result != null && result.hasIsbn()) {
                    waitingForIsbnOrCode = false;

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                        LoggerFactory.getLogger().d(TAG, "onSearchTaskFinished",
                                                    "waitingForExactCode",
                                                    "isbn=" + result.getIsbn());
                    }

                    // Start the remaining searches, even if they have run before.
                    // They will redo the search WITH the ISBN/code.
                    // Replace the search text with the (we hope) exact ISBN/code we just found.
                    // Worst case, explicitly use an empty string.
                    criteria.setIsbnText(result.getIsbn());
                    searchStarted = startSearch(context, criteria);
                } else {
                    // sequentially start the next search which has not run yet.
                    searchStarted = startNextSearch(context, criteria);
                }
            }
        }

        // any searches still running or did we get cancelled?
        final boolean stopSearching;
        synchronized (activeTasks) {
            // if we didn't start a new search (which might not be active yet!),
            // and there are no previous searches still running
            // (or we got cancelled) then we are done.
            stopSearching = !searchStarted && (activeTasks.isEmpty() || cancelRequested.get());
        }

        if (stopSearching) {
            // debug: measure the time the searches took, don't include the post-processing
            final long processTime = System.nanoTime();

            final Book book = accumulateResults(context, criteria);
            final String searchErrors = accumulateErrors(context);
            if (searchErrors != null && !searchErrors.isEmpty()) {
                book.putString(BKEY_SEARCH_ERROR, searchErrors);
            }

            final LiveDataEvent<Book> message = LiveDataEvent.of(book);
            if (cancelRequested.get()) {
                searchCoordinatorCancelled.setValue(message);
            } else {
                searchCoordinatorFinished.setValue(message);
            }

            if (BuildConfig.DEBUG && (DEBUG_SWITCHES.SEARCH_COORDINATOR
                                      || DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS)) {
                debugAllSearchesFinished(processTime, searchErrors);
            }
        }
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskFailed(final int taskId,
                                                 @Nullable final Throwable e) {
        synchronized (errorsByEngineId) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(taskId),
                                                  () -> ERROR_UNKNOWN_TASK + taskId)
                                  .getSearchEngine().getEngineId();
            }
            // Always store, even if the Exception is null
            errorsByEngineId.put(engineId, e);
        }
        onSearchTaskFinished(taskId, null);
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    private synchronized void onSearchTaskProgress(@NonNull final TaskProgress message) {
        synchronized (progressByEngineId) {
            final EngineId engineId;
            synchronized (activeTasks) {
                engineId = Objects.requireNonNull(activeTasks.get(message.taskId),
                                                  () -> ERROR_UNKNOWN_TASK + message.taskId)
                                  .getSearchEngine().getEngineId();
            }
            progressByEngineId.put(engineId, message);
        }
        // forward the accumulated progress
        synchronized (searchCoordinatorProgress) {
            searchCoordinatorProgress.setValue(LiveDataEvent.of(accumulateProgress()));
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
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     *
     * @return book data
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchFinished() {
        return searchCoordinatorFinished;
    }

    /**
     * The result if the user cancelled the search.
     *
     * @return book data found so far
     */
    @NonNull
    public LiveData<LiveDataEvent<Book>> onSearchCancelled() {
        return searchCoordinatorCancelled;
    }

    /**
     * Cancel all searches.
     */
    public void cancel() {
        cancelRequested.set(true);
        synchronized (activeTasks) {
            activeTasks.values().forEach(SearchTask::cancel);
        }
    }

    @Override
    protected void onCleared() {
        cancel();
    }

    public void cancelTask(@SuppressWarnings("unused") @IdRes final int taskId) {
        // reminder: this object, the SearchCoordinator is a pseudo task
        // we're only using "cancelTask" to conform with other usage
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

        if (resultsAccumulator == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                searchTasksStartTime = new EnumMap<>(EngineId.class);
                searchTasksEndTime = new EnumMap<>(EngineId.class);
            }

            allSites = Site.Type.Data.getSites();

            final ServiceLocator serviceLocator = ServiceLocator.getInstance();

            resultsAccumulator = new ResultsAccumulator(context, serviceLocator::getLanguages);

            listElementPrefixString = context.getString(R.string.list_element);
        }
    }

    /**
     * Called when all is said and done. Accumulate data from {@link #allSites}.
     *
     * @param context  Current context
     * @param criteria as used for the search
     *
     * @return the accumulated book data bundle
     */
    @NonNull
    private Book accumulateResults(@NonNull final Context context,
                                   @NonNull final SearchCoordinatorCriteria criteria) {

        final Book book = new Book();
        final List<EngineId> sitesInOrder;

        // Determine the set of sites for which we have results in the order the search completed
        final Set<EngineId> completedOrder = determineCompletedOrder();

        // Now convert the 'completed' order to the 'best' order
        if (criteria.hasValidIsbn()) {
            // When searching by ISBN, determine the best order use the site-data found.
            sitesInOrder = determineBestOrder(completedOrder, criteria);
            // Add the ISBN we initially searched for.
            // This avoids overwriting with a potentially different isbn from the sites
            book.setIsbn(criteria.getIsbnText());
        } else {
            // We did not have an ISBN as a search criteria; use the default order
            sitesInOrder = new ArrayList<>(completedOrder);
        }

        // Filter the results so we end up with the non-null and 'present' results only,
        // and convert the list to an ORDERED map with SearchEngine/Data pairs
        //noinspection DataFlowIssue
        final List<Pair<Locale, Book>> results = sitesInOrder
                .stream()
                .map(resultsByEngineId::get)
                .filter(Objects::nonNull)
                .filter(result -> result.getResult().isPresent())
                .map(searchResult -> new Pair<>(
                        engineCache.get(searchResult.getEngineId()).getLocale(context),
                        searchResult.getResult().get()))
                .collect(Collectors.toList());

        // Merge the data we have in the order as decided upon above.
        // no synchronized needed, at this point all other threads have finished.
        resultsAccumulator.process(context, results, book);

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbnStr = book.getString(DBKey.ISBN, null);
        if (isbnStr == null || isbnStr.isEmpty()) {
            book.setIsbn(criteria.getIsbnText());
        }

        // If we did not get a title, use the one we originally searched for.
        final String title = book.getString(DBKey.TITLE, null);
        if (title == null || title.isEmpty()) {
            book.setTitle(criteria.getTitle());
        }

        return book;
    }

    /**
     * Determine the set of sites for which we have results.
     *
     * @return sites ordered by completion time.
     */
    @NonNull
    private Set<EngineId> determineCompletedOrder() {
        final Set<EngineId> completedOrder = allSites
                .stream()
                .filter(Site::isActive)
                .map(Site::getEngineId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2023-04-21: bugfix: We MUST merge with engine keys for which results were found.
        // We need to do this when the user was for example searching by native-web-id
        // on a site which was NOT on the active list.
        // We use this somewhat convoluted method to make sure the priority order is kept.
        // Keep in mind that the results are NOT ordered by 'best order' YET, but in the order
        // in which the engines completed the searches.
        completedOrder.addAll(resultsByEngineId.keySet());
        return completedOrder;
    }

    /**
     * Determine the order in which to apply the results from the list of sites.
     * <p>
     * The order will be based on the site order as set by the user AND the actual results.
     *
     * @param activeEngines all engines we searched
     * @param criteria      as used for the search
     *
     * @return the list of sites in the 'best' order for further processing
     */
    @NonNull
    private List<EngineId> determineBestOrder(@NonNull final Set<EngineId> activeEngines,
                                              @NonNull final SearchCoordinatorCriteria criteria) {
        final List<EngineId> sitesInOrder = new ArrayList<>();
        final Collection<EngineId> sitesWithoutIsbn = new ArrayList<>();

        final boolean strictIsbn = criteria.isStrictIsbn();
        final Optional<ISBN> oIsbn = criteria.getIsbn();

        activeEngines.forEach(engineId -> {
            // no synchronized needed, at this point all other threads have finished.
            final SearchResult siteData = resultsByEngineId.get(engineId);
            if (siteData != null) {
                siteData.getResult().ifPresent(result -> {

                    if (siteData.getSearchBy() == SearchEngine.SearchBy.ExternalId
                        && !strictIsbn) {
                        // We searched by website id and didn't insist on an exact ISBN
                        // so we SHOULD be pretty sure about the data...
                        sitesInOrder.add(engineId);

                    } else if (result.hasIsbn()) {
                        // We did a general search with an ISBN; check if it matches
                        final String isbnFoundStr = result.getIsbn();
                        if (!isbnFoundStr.isEmpty()
                            && oIsbn.isPresent()
                            && oIsbn.get().equals(new ISBN(isbnFoundStr, strictIsbn))) {
                            sitesInOrder.add(engineId);
                        } else {
                            // The ISBN found does not match the ISBN we searched for;
                            // 2023-05-30: don't just skip; add it to the less reliables
                            sitesWithoutIsbn.add(engineId);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                                LoggerFactory.getLogger().d(TAG, "accumulateResults",
                                                            "isbn=" + oIsbn.orElse(null),
                                                            "isbnFound=" + isbnFoundStr);
                            }
                        }
                    } else {
                        // The result did not have an ISBN at all.
                        sitesWithoutIsbn.add(engineId);
                    }
                });
            }
        });

        // finally add the less reliable ones at the end of the list.
        sitesInOrder.addAll(sitesWithoutIsbn);
        return sitesInOrder;
    }

    @Override
    public boolean isCancelled() {
        return cancelRequested.get();
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

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     *
     * @param criteria to search for
     *
     * @throws IllegalStateException    if the network is not already checked/available
     * @throws IllegalArgumentException if there are no criteria set
     */
    private void prepareSearch(@NonNull final SearchCoordinatorCriteria criteria) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!ServiceLocator.getInstance().getNetworkChecker().isNetworkAvailable()) {
                throw new IllegalStateException("Network should be checked before starting search");
            }

            if (isSearchActive()) {
                throw new IllegalStateException("a search is already running");
            }

            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("Nothing to search for");
            }
        }

        // reset flags
        waitingForIsbnOrCode = false;
        cancelRequested.set(false);

        // no synchronized needed, at this point there are no other threads
        resultsByEngineId.clear();
        errorsByEngineId.clear();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            LoggerFactory.getLogger().d(TAG, "prepareSearch|criteria=" + criteria);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.clear();
            searchTasksEndTime.clear();
        }
    }

    /**
     * Check if a search task is already running.
     *
     * @return {@code true} if there is
     */
    public boolean isSearchActive() {
        synchronized (activeTasks) {
            return !activeTasks.isEmpty();
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with all sites <strong>active and disabled</strong>
     */
    @NonNull
    public List<Site> getSiteList() {
        return allSites;
    }

    /**
     * Override the initial list. Can contain active and disabled equally.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final List<Site> sites) {
        allSites = sites;
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
     * @return {@code true} if at least one search was started.
     *
     * @see #startSearch(Context, EngineId, SearchCoordinatorCriteria)
     */
    public boolean search(final SearchCoordinatorCriteria criteria) {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        prepareSearch(criteria);

        // If we have one or more SID's or we have a valid ISBN
        if (criteria.hasSids() || criteria.hasValidIsbn()) {
            // then start a concurrent search
            waitingForIsbnOrCode = false;
            return startSearch(context, criteria);

        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            waitingForIsbnOrCode = true;
            return startNextSearch(context, criteria);
        }
    }

    /**
     * Search a single search on the given engine for the site specific book id (sid).
     *
     * @param engineId to use
     * @param criteria to search for
     *
     * @return {@code true} if the search was started.
     *
     * @throws IllegalArgumentException if #sid was invalid
     * @see #startSearch(Context, EngineId, SearchCoordinatorCriteria)
     */
    public boolean searchByExternalId(@NonNull final EngineId engineId,
                                      @NonNull final SearchCoordinatorCriteria criteria) {
        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        prepareSearch(criteria);

        return startSearch(context, engineId, criteria);
    }

    /**
     * Start <strong>all</strong>> searches, which have not been run yet, in parallel.
     *
     * @param context  Current context
     * @param criteria to search for
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     *
     * @see #startSearch(Context, EngineId, SearchCoordinatorCriteria)
     */
    private boolean startSearch(@NonNull final Context context,
                                @NonNull final SearchCoordinatorCriteria criteria) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        boolean atLeastOneStarted = false;
        final List<EngineId> activeEngines = allSites.stream()
                                                     .filter(Site::isActive)
                                                     .map(Site::getEngineId)
                                                     .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            synchronized (resultsByEngineId) {
                if (!resultsByEngineId.containsKey(engineId)) {
                    if (startSearch(context, engineId, criteria)) {
                        atLeastOneStarted = true;
                    }
                }
            }
        }
        return atLeastOneStarted;
    }

    /**
     * Start a single search on the next engine in the queue.
     *
     * @param context  Current context
     * @param criteria to search for
     *
     * @return {@code true} if a search was started, {@code false} if not
     *
     * @see #startSearch(Context, EngineId, SearchCoordinatorCriteria)
     */
    private boolean startNextSearch(@NonNull final Context context,
                                    @NonNull final SearchCoordinatorCriteria criteria) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final List<EngineId> activeEngines = allSites.stream()
                                                     .filter(Site::isActive)
                                                     .map(Site::getEngineId)
                                                     .collect(Collectors.toList());
        for (final EngineId engineId : activeEngines) {
            // If the site has not been searched yet, search it
            synchronized (resultsByEngineId) {
                if (!resultsByEngineId.containsKey(engineId)) {
                    final boolean started = startSearch(context, engineId, criteria);
                    if (started) {
                        return true;
                    }
                    // else, loop to next site
                }
            }
        }
        return false;
    }

    /**
     * Start the specified site search. This is where a search-task is actually started.
     * <p>
     * <strong>synchronized</strong> to make sure we start tasks in a serial manner.
     *
     * @param context  Current context
     * @param engineId to search
     * @param criteria to use
     *
     * @return {@code true} if the search was started.
     */
    private synchronized boolean startSearch(@NonNull final Context context,
                                             @NonNull final EngineId engineId,
                                             @NonNull final SearchCoordinatorCriteria criteria) {
        // refuse new searches if we're shutting down.
        if (cancelRequested.get()) {
            return false;
        }

        final SearchEngineConfig config = engineId.getConfig();
        // Sanity check; should not happen when we get here... flw
        if (config == null) {
            return false;
        }

        SearchEngine searchEngine = engineCache.get(engineId);
        if (searchEngine == null) {
            searchEngine = engineId.createSearchEngine(context);
            engineCache.put(engineId, searchEngine);
        } else {
            searchEngine.reset();
        }

        @Nullable
        final SearchTask task = SearchTask.createSearchTask(context, TASK_ID.getAndIncrement(),
                                                            searchEngine,
                                                            criteria,
                                                            searchTaskListener);

        if (task == null) {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        synchronized (activeTasks) {
            activeTasks.put(task.getTaskId(), task);
        }

        if (BuildConfig.DEBUG) {
            if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                LoggerFactory.getLogger().d(TAG, "startSearch|new-task=" + task.getTaskId(),
                                            "searchEngine=" + engineId.name(),
                                            "waitingForIsbnOrCode=" + waitingForIsbnOrCode);
            }
            if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                searchTasksStartTime.put(task.getSearchEngine().getEngineId(), System.nanoTime());
            }
        }

        task.startSearch();
        return true;
    }

    /**
     * Called when all is said and done. Collects all individual website errors (if any)
     * into a single user-formatted message.
     *
     * @param context Current context
     *
     * @return the error message
     */
    @Nullable
    private String accumulateErrors(@NonNull final Context context) {
        // no synchronized needed, at this point all other threads have finished.
        if (!errorsByEngineId.isEmpty()) {
            final String msg = errorsByEngineId
                    .values()
                    .stream()
                    .map(exception -> ExMsg
                            .map(context, exception)
                            .orElseGet(() -> {
                                // generic network related IOException message
                                if (exception instanceof IOException) {
                                    return context.getString(R.string.error_search_failed_network);
                                }
                                // generic unknown message
                                return context.getString(R.string.error_unexpected);
                            }))
                    .collect(Collectors.joining("\n"));

            errorsByEngineId.clear();
            return msg;
        }
        return null;
    }

    /**
     * A single search finished.
     *
     * @param engineId which finished
     */
    private void debugSingleSearchFinished(@NonNull final EngineId engineId) {
        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksEndTime.put(engineId, System.nanoTime());
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            final Logger logger = LoggerFactory.getLogger();
            logger.d(TAG, "onSearchTaskFinished|finished=" + engineId.name());

            synchronized (activeTasks) {
                for (final SearchTask task : activeTasks.values()) {
                    logger.d(TAG, "onSearchTaskFinished|running=" + task.getSearchEngine()
                                                                        .getEngineId().name());
                }
            }
        }
    }

    private void debugAllSearchesFinished(final long processTime,
                                          @Nullable final String searchErrors) {
        final Logger logger = LoggerFactory.getLogger();

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            logger.d(TAG, "onSearchTaskFinished",
                     "cancelled=" + cancelRequested.get(),
                     "searchErrors=" + searchErrors);
        }

        if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            for (final Map.Entry<EngineId, Long> entry : searchTasksStartTime.entrySet()) {
                final EngineId engineId = entry.getKey();
                final String engineName = engineId.name();

                final long start = entry.getValue();
                final Long end = searchTasksEndTime.get(engineId);

                if (end != null) {
                    logger.d(TAG, "onSearchTaskFinished",
                             String.format(Locale.ENGLISH, "engine=%20s:%10d ms",
                                           engineName,
                                           (end - start) / NANO_TO_MILLIS));
                } else {
                    logger.d(TAG, "onSearchTaskFinished",
                             String.format(Locale.ENGLISH, "engine=%20s|never finished",
                                           engineName));
                }
            }

            logger.d(TAG, "onSearchTaskFinished",
                     String.format(Locale.ENGLISH, "total search time: %10d ms",
                                   (processTime - searchStartTime)
                                   / NANO_TO_MILLIS));

            logger.d(TAG, "onSearchTaskFinished",
                     String.format(Locale.ENGLISH, "processing time: %10d ms",
                                   (System.nanoTime() - processTime)
                                   / NANO_TO_MILLIS));
        }
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
        this.baseMessage = baseMessage;
    }

    /**
     * Value class encapsulating
     * where a result came from + how the search was done + the result itself.
     * The result itself can be {@code null} if nothing was found.
     */
    static class SearchResult {

        @Nullable
        private final Book result;
        @NonNull
        private final EngineId engineId;
        @NonNull
        private final SearchEngine.SearchBy searchBy;

        SearchResult(@NonNull final EngineId engineId,
                     @NonNull final SearchEngine.SearchBy searchBy,
                     @Nullable final Book result) {
            this.engineId = engineId;
            this.searchBy = searchBy;
            this.result = result;
        }

        @NonNull
        EngineId getEngineId() {
            return engineId;
        }

        @NonNull
        SearchEngine.SearchBy getSearchBy() {
            return searchBy;
        }

        @NonNull
        Optional<Book> getResult() {
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        }
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
