package io.github.muntashirakon.AppManager.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import io.github.muntashirakon.AppManager.MainLoader;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbShell;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.fragments.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.types.ApplicationItem;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.types.ScrollSafeSwipeRefreshLayout;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

import static androidx.appcompat.app.ActionBar.LayoutParams;

public class MainActivity extends AppCompatActivity implements
        SearchView.OnQueryTextListener, LoaderManager.LoaderCallbacks<List<ApplicationItem>>,
        ScrollSafeSwipeRefreshLayout.OnRefreshListener {
    public static final String EXTRA_PACKAGE_LIST = "EXTRA_PACKAGE_LIST";
    public static final String EXTRA_LIST_NAME = "EXTRA_LIST_NAME";

    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";

    private static final String MIME_TSV = "text/tab-separated-values";

    private static final int REQUEST_CODE_BATCH_EXPORT = 441;

    /**
     * A list of packages separated by \r\n. Debug apps should have a * after their package names.
     */
    public static String packageList;
    /**
     * The name of this particular package list
     */
    public static String listName;

    private static Collator sCollator = Collator.getInstance();

    private static final int[] sSortMenuItemIdsMap = {R.id.action_sort_by_domain,
            R.id.action_sort_by_app_label, R.id.action_sort_by_package_name,
            R.id.action_sort_by_last_update, R.id.action_sort_by_shared_user_id,
            R.id.action_sort_by_app_size, R.id.action_sort_by_sha, R.id.action_sort_by_disabled_app,
            R.id.action_sort_by_blocked_components};

    @IntDef(value = {
            SORT_BY_DOMAIN,
            SORT_BY_APP_LABEL,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_LAST_UPDATE,
            SORT_BY_SHARED_ID,
            SORT_BY_APP_SIZE_OR_SDK,
            SORT_BY_SHA,
            SORT_BY_DISABLED_APP,
            SORT_BY_BLOCKED_COMPONENTS
    })
    public @interface SortOrder {}
    public static final int SORT_BY_DOMAIN = 0;  // User/system app
    public static final int SORT_BY_APP_LABEL = 1;
    public static final int SORT_BY_PACKAGE_NAME = 2;
    public static final int SORT_BY_LAST_UPDATE = 3;
    public static final int SORT_BY_SHARED_ID = 4;
    public static final int SORT_BY_APP_SIZE_OR_SDK = 5;  // App size/sdk
    public static final int SORT_BY_SHA = 6;  // Signature
    public static final int SORT_BY_DISABLED_APP = 7;
    public static final int SORT_BY_BLOCKED_COMPONENTS = 8;

    private MainActivity.MainRecyclerAdapter mAdapter;
    private List<ApplicationItem> mApplicationItems = new ArrayList<>();
    private int mItemSizeRetrievedCount;
    private SearchView mSearchView;
    private ProgressIndicator mProgressIndicator;
    private LoaderManager mLoaderManager;
    private ScrollSafeSwipeRefreshLayout mSwipeRefresh;
    private BottomAppBar mBottomAppBar;
    private MaterialTextView mBottomAppBarCounter;
    private LinearLayoutCompat mMainLayout;
    private static String mConstraint;
    private static @NonNull Set<String> mPackageNames = new HashSet<>();
    private static @NonNull Set<ApplicationItem> mSelectedApplicationItems = new HashSet<>();
    private CoordinatorLayout.LayoutParams mLayoutParamsSelection;
    private CoordinatorLayout.LayoutParams mLayoutParamsTypical;
    private MenuItem appUsageMenu;
    private MenuItem runningAppsMenu;
    private MenuItem sortByBlockedComponentMenu;
    private BatchOpsManager mBatchOpsManager;
    private @SortOrder int mSortBy;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode((int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT));
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setTitle(getString(R.string.loading));

            mSearchView = new SearchView(actionBar.getThemedContext());
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setQueryHint(getString(R.string.search));

            ((ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            actionBar.setCustomView(mSearchView, layoutParams);
        }
        packageList = getIntent().getStringExtra(EXTRA_PACKAGE_LIST);
        listName = getIntent().getStringExtra(EXTRA_LIST_NAME);
        if (listName == null) listName = "Onboard.packages";

        mProgressIndicator = findViewById(R.id.progress_linear);
        RecyclerView recyclerView = findViewById(R.id.item_list);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mBottomAppBar = findViewById(R.id.bottom_appbar);
        mBottomAppBarCounter = findViewById(R.id.bottom_appbar_counter);
        mMainLayout = findViewById(R.id.main_layout);

        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(this, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(this, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);

        int margin = Utils.dpToPx(this, 56);
        mLayoutParamsSelection = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT);
        mLayoutParamsSelection.setMargins(0, margin, 0, margin);
        mLayoutParamsTypical = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT);
        mLayoutParamsTypical.setMargins(0, margin, 0, 0);

        mAdapter = new MainActivity.MainRecyclerAdapter(MainActivity.this);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);

        mBatchOpsManager = new BatchOpsManager(this);

        Menu menu = mBottomAppBar.getMenu();
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        mBottomAppBar.setNavigationOnClickListener(v -> {
            mPackageNames.clear();
            handleSelection();
        });
        mBottomAppBar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_block_trackers:
                    handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS, R.string.alert_failed_to_disable_trackers);
                    return true;
                case R.id.action_clear_data:
                    handleBatchOp(BatchOpsManager.OP_CLEAR_DATA, R.string.alert_failed_to_clear_data);
                    return true;
                case R.id.action_disable:
                    handleBatchOp(BatchOpsManager.OP_DISABLE, R.string.alert_failed_to_disable);
                    return true;
                case R.id.action_disable_background:
                    handleBatchOp(BatchOpsManager.OP_DISABLE_BACKGROUND, R.string.alert_failed_to_disable_background);
                    return true;
                case R.id.action_export_blocking_rules:
                    @SuppressLint("SimpleDateFormat")
                    String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(MIME_TSV);
                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(intent, REQUEST_CODE_BATCH_EXPORT);
                    return true;
                case R.id.action_kill_process:
                    handleBatchOp(BatchOpsManager.OP_KILL, R.string.alert_failed_to_kill);
                    return true;
                case R.id.action_uninstall:
                    handleBatchOp(BatchOpsManager.OP_UNINSTALL, R.string.alert_failed_to_uninstall);
                    return true;
                case R.id.action_backup_apk:
                case R.id.action_backup_data:
                    Toast.makeText(this, "This operation is not supported yet.", Toast.LENGTH_LONG).show();
                    mPackageNames.clear();
                    handleSelection();
                    return true;
            }
            mPackageNames.clear();
            handleSelection();
            return false;
        });
        handleSelection();

        mLoaderManager = LoaderManager.getInstance(this);
        mLoaderManager.initLoader(0, null, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_CODE_BATCH_EXPORT) {
                if (data != null) {
                    RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                    Bundle args = new Bundle();
                    args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                    args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, data.getData());
                    args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, new ArrayList<>(mPackageNames));
                    dialogFragment.setArguments(args);
                    dialogFragment.show(getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT, mSortBy);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        appUsageMenu = menu.findItem(R.id.action_app_usage);
        if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
            appUsageMenu.setVisible(true);
        } else appUsageMenu.setVisible(false);
        runningAppsMenu = menu.findItem(R.id.action_running_apps);
        sortByBlockedComponentMenu = menu.findItem(R.id.action_sort_by_blocked_components);
        if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
            runningAppsMenu.setVisible(true);
            sortByBlockedComponentMenu.setVisible(true);
        } else {
            runningAppsMenu.setVisible(false);
            sortByBlockedComponentMenu.setVisible(false);
        }
        MenuItem apkUpdaterMenu = menu.findItem(R.id.action_apk_updater);
        try {
            if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            apkUpdaterMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            apkUpdaterMenu.setVisible(false);
        }
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
        return true;
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_instructions:
                new FullscreenDialog(this)
                        .setTitle(R.string.instructions)
                        .setView(R.layout.dialog_instructions)
                        .show();
                return true;
            case R.id.action_refresh:
                if (mSortBy == SORT_BY_APP_SIZE_OR_SDK && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                    Toast t = Toast.makeText(this, getString(R.string.refresh) + " & " + getString(R.string.sort) + "/" + getString(R.string.sort_by_app_size)
                            + "\n" + getString(R.string.unsupported), Toast.LENGTH_LONG);
                    t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
                    t.show();
                    return true;
                }
                mLoaderManager.restartLoader(0, null, this);
                return true;
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_sort_by_app_label:
                setSortBy(SORT_BY_APP_LABEL);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_package_name:
                setSortBy(SORT_BY_PACKAGE_NAME);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_domain:
                setSortBy(SORT_BY_DOMAIN);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_last_update:
                setSortBy(SORT_BY_LAST_UPDATE);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_shared_user_id:
                setSortBy(SORT_BY_SHARED_ID);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_sha:
                setSortBy(SORT_BY_SHA);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_app_size:
                setSortBy(SORT_BY_APP_SIZE_OR_SDK);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_disabled_app:
                setSortBy(SORT_BY_DISABLED_APP);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_blocked_components:
                setSortBy(SORT_BY_BLOCKED_COMPONENTS);
                item.setChecked(true);
                return true;
            case R.id.action_app_usage:
                Intent usageIntent = new Intent(this, AppUsageActivity.class);
                startActivity(usageIntent);
                return true;
            case R.id.action_apk_updater:
                try {
                    if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                        throw new PackageManager.NameNotFoundException();
                    Intent intent = new Intent();
                    intent.setClassName(PACKAGE_NAME_APK_UPDATER, ACTIVITY_NAME_APK_UPDATER);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception ignored) {}
                } catch (PackageManager.NameNotFoundException ignored) {}
                return true;
            case R.id.action_running_apps:
                Intent runningAppsIntent = new Intent(this, RunningAppsActivity.class);
                startActivity(runningAppsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @NonNull
    @Override
    public Loader<List<ApplicationItem>> onCreateLoader(int id, @Nullable Bundle args) {
        showProgressIndicator(true);
        return new MainLoader(this);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<ApplicationItem>> loader, List<ApplicationItem> applicationItems) {
        mApplicationItems = applicationItems;
        sortApplicationList(mSortBy);
        mAdapter.setDefaultList(mApplicationItems);
        // Set title and subtitle
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(MainActivity.listName.substring(0,
                    MainActivity.listName.lastIndexOf(".")));
            actionBar.setSubtitle(MainActivity.listName.substring(
                    MainActivity.listName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT));
        }
        if (Build.VERSION.SDK_INT <= 25) {
            startRetrievingPackagesSize();
        }
        showProgressIndicator(false);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<ApplicationItem>> loader) {
        mApplicationItems = null;
        mAdapter.setDefaultList(null);
        showProgressIndicator(false);
    }

    @Override
    public void onRefresh() {
        if (mSortBy == SORT_BY_APP_SIZE_OR_SDK && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            Toast t = Toast.makeText(this, getString(R.string.refresh) + " & " + getString(R.string.sort) + "/" + getString(R.string.sort_by_app_size)
                    + "\n" + getString(R.string.unsupported), Toast.LENGTH_LONG);
            t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
            t.show();
        } else {
            mLoaderManager.restartLoader(0, null, this);
        }
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check root
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
        if (!Utils.isRootGiven()) {
            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
            // Check for adb
            new Thread(() -> {
                try {
                    AdbShell.CommandResult result = AdbShell.run("id");
                    if (!result.isSuccessful()) throw new IOException("Adb not available");
                    AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
                    runOnUiThread(() -> Toast.makeText(this, "Working on ADB mode", Toast.LENGTH_SHORT).show());
                } catch (Exception ignored) {}
            }).start();
        }
        // Set filter
        if (mAdapter != null && mSearchView != null && !TextUtils.isEmpty(mConstraint)) {
            mSearchView.setIconified(false);
            mSearchView.setQuery(mConstraint, false);
        }
        // Show/hide app usage menu
        if (appUsageMenu != null) {
            if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL))
                appUsageMenu.setVisible(true);
            else appUsageMenu.setVisible(false);
        }
        // Set sort by
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
            if (runningAppsMenu != null) runningAppsMenu.setVisible(true);
            if (sortByBlockedComponentMenu != null) sortByBlockedComponentMenu.setVisible(true);
        } else {
            if (mSortBy == SORT_BY_BLOCKED_COMPONENTS) mSortBy = SORT_BY_APP_LABEL;
            if (runningAppsMenu != null) runningAppsMenu.setVisible(false);
            if (sortByBlockedComponentMenu != null) sortByBlockedComponentMenu.setVisible(false);
        }
    }

    private void handleSelection() {
        if (mPackageNames.size() == 0) {
            mBottomAppBar.setVisibility(View.GONE);
            mMainLayout.setLayoutParams(mLayoutParamsTypical);
            mAdapter.clearSelection();
        } else {
            mBottomAppBar.setVisibility(View.VISIBLE);
            mBottomAppBarCounter.setText(String.format(getString(R.string.some_items_selected), mPackageNames.size()));
            mMainLayout.setLayoutParams(mLayoutParamsSelection);
        }
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op, @StringRes int msg) {
        showProgressIndicator(true);
        new Thread(() -> {
            if (!mBatchOpsManager.performOp(op, new ArrayList<>(mPackageNames)).isSuccessful()) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this, R.style.AppTheme_AlertDialog)
                        .setTitle(msg)
                        .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                                mBatchOpsManager.getLastResult().failedPackages()), null)
                        .setNegativeButton(android.R.string.ok, null)
                        .show());
            } else {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.the_operation_was_successful, Toast.LENGTH_LONG).show());
            }
            mPackageNames.clear();
            runOnUiThread(() -> {
                handleSelection();
                showProgressIndicator(false);
            });
        }).start();
    }

    private void showProgressIndicator(boolean show) {
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    /**
     * Sort main list if provided value is valid.
     *
     * @param sort Must be one of SORT_*
     */
    private void setSortBy(@SortOrder int sort) {
        mSortBy = sort;
        sortApplicationList(mSortBy);
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
    }

    private void sortApplicationList(@SortOrder int sortBy) {
        final Boolean isRootEnabled = AppPref.isRootEnabled();
        if (sortBy != SORT_BY_APP_LABEL) sortApplicationList(SORT_BY_APP_LABEL);
        Collections.sort(mApplicationItems, (o1, o2) -> {
            switch (sortBy) {
                case SORT_BY_APP_LABEL:
                    return sCollator.compare(o1.label, o2.label);
                case SORT_BY_PACKAGE_NAME:
                    return o1.applicationInfo.packageName.compareTo(o2.applicationInfo.packageName);
                case SORT_BY_DOMAIN:
                    boolean isSystem1 = (o1.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean isSystem2 = (o2.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    return Utils.compareBooleans(isSystem1, isSystem2);
                case SORT_BY_LAST_UPDATE:
                    // Sort in decreasing order
                    return -o1.date.compareTo(o2.date);
                case SORT_BY_APP_SIZE_OR_SDK:
                    return -o1.size.compareTo(o2.size);
                case SORT_BY_SHARED_ID:
                    return o2.applicationInfo.uid - o1.applicationInfo.uid;
                case SORT_BY_SHA:
                    try {
                        return o1.sha.compareTo(o2.sha);
                    } catch (NullPointerException ignored) {}
                    break;
                case SORT_BY_BLOCKED_COMPONENTS:
                    if (isRootEnabled)
                        return -o1.blockedCount.compareTo(o2.blockedCount);
                    break;
                case SORT_BY_DISABLED_APP:
                    return Utils.compareBooleans(o1.applicationInfo.enabled, o2.applicationInfo.enabled);
            }
            return 0;
        });
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mConstraint = s;
        if (mAdapter != null)
            mAdapter.getFilter().filter(mConstraint);
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    private void startRetrievingPackagesSize() {
        for (ApplicationItem item : mApplicationItems)
            getItemSize(item);
    }

    private void getItemSize(@NonNull final ApplicationItem item) {
        try {
            @SuppressWarnings("JavaReflectionMemberAccess")
            Method getPackageSizeInfo = PackageManager.class.getMethod(
                    "getPackageSizeInfo", String.class, IPackageStatsObserver.class);

            getPackageSizeInfo.invoke(this.getPackageManager(), item.applicationInfo.packageName, new IPackageStatsObserver.Stub() {
                @Override
                public void onGetStatsCompleted(final PackageStats pStats, final boolean succeeded) {
                    MainActivity.this.runOnUiThread(() -> {
                        if (succeeded)
                            item.size = pStats.codeSize + pStats.cacheSize + pStats.dataSize
                                    + pStats.externalCodeSize + pStats.externalCacheSize + pStats.externalDataSize
                                    + pStats.externalMediaSize + pStats.externalObbSize;
                        else
                            item.size = -1L;

                        incrementItemSizeRetrievedCount();
                    });
                }
            });
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            incrementItemSizeRetrievedCount();
        }
    }

    private void incrementItemSizeRetrievedCount() {
        mItemSizeRetrievedCount++;

        if (mItemSizeRetrievedCount == mApplicationItems.size())
            mAdapter.notifyDataSetChanged();
    }

    static class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.ViewHolder>
            implements SectionIndexer, Filterable {
        static final String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        @SuppressLint("SimpleDateFormat")
        static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy"); // hh:mm:ss");

        private MainActivity mActivity;
        private static PackageManager mPackageManager;
        private Filter mFilter;
        private String mConstraint;
        private List<ApplicationItem> mDefaultList;
        private List<ApplicationItem> mAdapterList;

        private static int mColorTransparent;
        private static int mColorSemiTransparent;
        private static int mColorHighlight;
        private static int mColorOrange;
        private static int mColorPrimary;
        private static int mColorSecondary;
        private static int mColorRed;

        MainRecyclerAdapter(@NonNull MainActivity activity) {
            mActivity = activity;
            mPackageManager = activity.getPackageManager();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(mActivity, R.color.semi_transparent);
            mColorHighlight = ContextCompat.getColor(mActivity, R.color.highlight);
            mColorOrange = ContextCompat.getColor(mActivity, R.color.orange);
            mColorPrimary = Utils.getThemeColor(mActivity, android.R.attr.textColorPrimary);
            mColorSecondary = Utils.getThemeColor(mActivity, android.R.attr.textColorSecondary);
            mColorRed = ContextCompat.getColor(mActivity, R.color.red);
        }

        void setDefaultList(List<ApplicationItem> list) {
            mDefaultList = list;
            mAdapterList = list;
            if(!TextUtils.isEmpty(MainActivity.mConstraint)) {
                getFilter().filter(MainActivity.mConstraint);
            }
            notifyDataSetChanged();
        }

        void clearSelection() {
            mPackageNames.clear();
            int itemId;
            for (ApplicationItem applicationItem: mSelectedApplicationItems) {
                itemId = mAdapterList.indexOf(applicationItem);
                if (itemId != -1) notifyItemChanged(itemId);
            }
            mSelectedApplicationItems.clear();
        }

        @Override
        public Filter getFilter() {
            if (mFilter == null)
                mFilter = new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence charSequence) {
                        String constraint = charSequence.toString().toLowerCase(Locale.ROOT);
                        mConstraint = constraint;
                        FilterResults filterResults = new FilterResults();
                        if (constraint.length() == 0 || mDefaultList == null) {
                            filterResults.count = 0;
                            filterResults.values = null;
                            return filterResults;
                        }

                        List<ApplicationItem> list = new ArrayList<>(mDefaultList.size());
                        for (ApplicationItem item : mDefaultList) {
                            if (item.label.toLowerCase(Locale.ROOT).contains(constraint) ||
                                    item.applicationInfo.packageName.toLowerCase(Locale.ROOT).contains(constraint))
                                list.add(item);
                        }

                        filterResults.count = list.size();
                        filterResults.values = list;
                        return filterResults;
                    }

                    @Override
                    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                        if (filterResults.values == null) {
                            mAdapterList = mDefaultList;
                        } else {
                            //noinspection unchecked
                            mAdapterList = (List<ApplicationItem>) filterResults.values;
                        }
                        notifyDataSetChanged();
                    }
                };
            return mFilter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_main, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Cancel an existing icon loading operation
            if (holder.iconLoader != null) holder.iconLoader.cancel(true);
            final ApplicationItem item = mAdapterList.get(position);
            final ApplicationInfo info = item.applicationInfo;
            // Add click listeners
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(mActivity, AppInfoActivity.class);
                intent.putExtra(AppInfoActivity.EXTRA_PACKAGE_NAME, info.packageName);
                mActivity.startActivity(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, info.packageName);
                mActivity.startActivity(appDetailsIntent);
                return true;
            });
            // Alternate background colors: selected > disabled > regular
            if (mPackageNames.contains(info.packageName))
                holder.mainView.setBackgroundColor(mColorHighlight);
            else if (!info.enabled)
                holder.mainView.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.disabled_user));
            else holder.mainView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            // Add yellow star if the app is in debug mode
            holder.favorite_icon.setVisibility(item.star ? View.VISIBLE : View.INVISIBLE);
            try {
                PackageInfo packageInfo = mPackageManager.getPackageInfo(info.packageName, 0);
                // Set version name
                holder.version.setText(packageInfo.versionName);
                // Set date and (if available,) days between first install and last update
                String lastUpdateDate = sSimpleDateFormat.format(new Date(packageInfo.lastUpdateTime));
                if (packageInfo.firstInstallTime == packageInfo.lastUpdateTime)
                    holder.date.setText(lastUpdateDate);
                else {
                    long days = TimeUnit.DAYS.convert(packageInfo.lastUpdateTime
                            - packageInfo.firstInstallTime, TimeUnit.MILLISECONDS);
                    SpannableString ssDate = new SpannableString(
                            String.format(mActivity.getString(R.string.main_list_date_days),
                                    lastUpdateDate, days));
                    ssDate.setSpan(new RelativeSizeSpan(.8f), 10, ssDate.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.date.setText(ssDate);
                }
                // Set date color to orange if app can read logs (and accepted)
                if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS,info.packageName)
                        == PackageManager.PERMISSION_GRANTED)
                    holder.date.setTextColor(mColorOrange);
                else holder.date.setTextColor(mColorSecondary);
                // Set kernel user ID
                holder.sharedId.setText(String.format(Locale.getDefault(),"%d", info.uid));
                // Set kernel user ID text color to orange if the package is shared
                if (packageInfo.sharedUserId != null) holder.sharedId.setTextColor(mColorOrange);
                else holder.sharedId.setTextColor(mColorSecondary);
                // Set issuer
                String issuer;
                try {
                    issuer = "CN=" + (item.sha.getFirst()).split("CN=", 2)[1];
                } catch (ArrayIndexOutOfBoundsException e){
                    issuer = item.sha.getFirst();
                }
                holder.issuer.setText(issuer);
                // Set signature type
                holder.sha.setText(item.sha.getSecond());
            } catch (PackageManager.NameNotFoundException | NullPointerException ignored) {}
            // Load app icon
            holder.iconLoader = new IconAsyncTask(holder.icon, info);
            holder.iconLoader.execute();
            // Set app label
            if (!TextUtils.isEmpty(mConstraint) && item.label.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.label.setText(Utils.getHighlightedText(item.label, mConstraint, mColorRed));
            } else holder.label.setText(item.label);
            // Set app label color to red if clearing user data not allowed
            if ((info.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0)
                holder.label.setTextColor(Color.RED);
            else holder.label.setTextColor(mColorPrimary);
            // Set package name
            if (!TextUtils.isEmpty(mConstraint) && info.packageName.toLowerCase(Locale.ROOT).contains(mConstraint)) {
                // Highlight searched query
                holder.packageName.setText(Utils.getHighlightedText(info.packageName, mConstraint, mColorRed));
            } else holder.packageName.setText(info.packageName);
            // Set package name color to blue if the app is in stopped/force closed state
            if ((info.flags & ApplicationInfo.FLAG_STOPPED) != 0)
                holder.packageName.setTextColor(ContextCompat.getColor(mActivity, R.color.stopped));
            else holder.packageName.setTextColor(mColorSecondary);
            // Set version (along with HW accelerated, debug and test only flags)
            CharSequence version = holder.version.getText();
            if ((info.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0) version = "_" + version;
            if ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) version = "debug" + version;
            if ((info.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) version = "~" + version;
            holder.version.setText(version);
            // Set version color to green if the app is inactive
            if (Build.VERSION.SDK_INT >= 23) {
                UsageStatsManager mUsageStats;
                mUsageStats = mActivity.getSystemService(UsageStatsManager.class);
                if (mUsageStats != null && mUsageStats.isAppInactive(info.packageName))
                    holder.version.setTextColor(ContextCompat.getColor(mActivity, R.color.stopped));
                else holder.version.setTextColor(mColorSecondary);
            }
            // Set app type: system or user app (along with large heap, suspended, multi-arch,
            // has code, vm safe mode)
            String isSystemApp;
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) isSystemApp = mActivity.getString(R.string.system);
            else isSystemApp = mActivity.getString(R.string.user);
            if ((info.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) isSystemApp += "#";
            if ((info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) isSystemApp += "°";
            if ((info.flags & ApplicationInfo.FLAG_MULTIARCH) != 0) isSystemApp += "X";
            if ((info.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) isSystemApp += "0";
            if ((info.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) isSystemApp += "?";
            holder.isSystemApp.setText(isSystemApp);
            // Set app type text color to magenta if the app is persistent
            if ((info.flags & ApplicationInfo.FLAG_PERSISTENT) != 0)
                holder.isSystemApp.setTextColor(Color.MAGENTA);
            else holder.isSystemApp.setTextColor(mColorSecondary);
            // Set SDK
            if (Build.VERSION.SDK_INT >= 26) {
                holder.size.setText(String.format(Locale.getDefault(), "SDK %d", -item.size));
            } else if (item.size != -1L) {
                holder.size.setText(Formatter.formatFileSize(mActivity, item.size));
            }
            // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
            if ((info.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) !=0)
                holder.size.setTextColor(mColorOrange);
            else holder.size.setTextColor(mColorSecondary);
            holder.icon.setOnClickListener(v -> {
                if (MainActivity.mPackageNames.contains(info.packageName)) {
                    MainActivity.mPackageNames.remove(info.packageName);
                    MainActivity.mSelectedApplicationItems.remove(item);
                } else {
                    MainActivity.mPackageNames.add(info.packageName);
                    MainActivity.mSelectedApplicationItems.add(item);
                }
                notifyItemChanged(position);
                mActivity.handleSelection();
            });
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getItemCount() {
            return mAdapterList == null ? 0 : mAdapterList.size();
        }

        @Override
        public int getPositionForSection(int section) {
            for (int i = 0; i < getItemCount(); i++) {
                String item = mAdapterList.get(i).label;
                if (item.length() > 0) {
                    if (item.charAt(0) == sections.charAt(section))
                        return i;
                }
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int i) {
            return 0;
        }

        @Override
        public Object[] getSections() {
            String[] sectionsArr = new String[sections.length()];
            for (int i = 0; i < sections.length(); i++)
                sectionsArr[i] = "" + sections.charAt(i);

            return sectionsArr;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View mainView;
            ImageView icon;
            ImageView favorite_icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            TextView sharedId;
            TextView issuer;
            TextView sha;
            MainRecyclerAdapter.IconAsyncTask iconLoader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                mainView = itemView.findViewById(R.id.main_view);
                icon = itemView.findViewById(R.id.icon);
                favorite_icon = itemView.findViewById(R.id.favorite_icon);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.packageName);
                version = itemView.findViewById(R.id.version);
                isSystemApp = itemView.findViewById(R.id.isSystem);
                date = itemView.findViewById(R.id.date);
                size = itemView.findViewById(R.id.size);
                sharedId = itemView.findViewById(R.id.shareid);
                issuer = itemView.findViewById(R.id.issuer);
                sha = itemView.findViewById(R.id.sha);
            }
        }

        private static class IconAsyncTask extends AsyncTask<Void, Integer, Drawable> {
            private WeakReference<ImageView> imageView = null;
            ApplicationInfo info;

            private IconAsyncTask(ImageView pImageViewWeakReference,ApplicationInfo info) {
                link(pImageViewWeakReference);
                this.info = info;
            }

            private void link(ImageView pImageViewWeakReference) {
                imageView = new WeakReference<>(pImageViewWeakReference);
            }


            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                if (imageView.get()!=null)
                    imageView.get().setVisibility(View.INVISIBLE);
            }

            @Override
            protected Drawable doInBackground(Void... voids) {
                if (!isCancelled())
                    return info.loadIcon(mPackageManager);
                return null;
            }

            @Override
            protected void onPostExecute(Drawable drawable) {
                super.onPostExecute(drawable);
                if (imageView.get()!=null){
                    imageView.get().setImageDrawable(drawable);
                    imageView.get().setVisibility(View.VISIBLE);
                }
            }
        }
    }

}
