package io.github.muntashirakon.AppManager.batchops;

import android.content.Context;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import io.github.muntashirakon.AppManager.appops.AppOpsManager;
import io.github.muntashirakon.AppManager.storage.compontents.ComponentsBlocker;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.compontents.ExternalComponentsImporter;
import io.github.muntashirakon.AppManager.utils.RunnerUtils;

public class BatchOpsManager {
    @IntDef(value = {
            OP_BACKUP_APK,
            OP_BACKUP_DATA,
            OP_BLOCK_TRACKERS,
            OP_CLEAR_DATA,
            OP_DISABLE,
            OP_DISABLE_BACKGROUND,
            OP_EXPORT_RULES,
            OP_KILL,
            OP_UNINSTALL
    })
    public @interface OpType {}
    public static final int OP_BACKUP_APK = 0;
    public static final int OP_BACKUP_DATA = 1;
    public static final int OP_BLOCK_TRACKERS = 2;
    public static final int OP_CLEAR_DATA = 3;
    public static final int OP_DISABLE = 4;
    public static final int OP_DISABLE_BACKGROUND = 5;
    public static final int OP_EXPORT_RULES = 6;
    public static final int OP_KILL = 7;
    public static final int OP_UNINSTALL = 8;

    private Runner runner;
    private Context context;
    public BatchOpsManager(Context context) {
        this.context = context;
        this.runner = Runner.getInstance();
    }

    private List<String> packageNames;
    private Result lastResult;

    @NonNull
    public Result performOp(@OpType int op, List<String> packageNames) {
        this.runner.clear();
        this.packageNames = packageNames;
        switch (op) {
            case OP_BACKUP_APK:  // TODO
            case OP_BACKUP_DATA:  // TODO
                break;
            case OP_BLOCK_TRACKERS: return opBlockTrackers();
            case OP_CLEAR_DATA: return opClearData();
            case OP_DISABLE: return opDisable();
            case OP_DISABLE_BACKGROUND: return opDisableBackground();
            case OP_EXPORT_RULES: break;  // Done in the main activity
            case OP_KILL: return opKill();
            case OP_UNINSTALL: return opUninstall();
        }
        lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return false;
            }

            @Override
            public List<String> failedPackages() {
                return null;
            }
        };
        return lastResult;
    }

    public Result getLastResult() {
        return lastResult;
    }

    private Result opBlockTrackers() {
        final List<String> failedPkgList = ExternalComponentsImporter.applyFromTrackingComponents(context, packageNames);
        return lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return failedPkgList.size() == 0;
            }

            @Override
            public List<String> failedPackages() {
                return failedPkgList;
            }
        };
    }

    @NonNull
    private Result opClearData() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_CLEAR_PACKAGE_DATA, packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisable() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_DISABLE_PACKAGE, packageName));
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opDisableBackground() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_APP_OPS_SET_MODE_INT, packageName, 63, AppOpsManager.MODE_IGNORED));
        }
        Result result = runOpAndFetchResults();
        List<String> failedPackages = result.failedPackages();
        for (String packageName: packageNames) {
            if (!failedPackages.contains(packageName)) {
                try (ComponentsBlocker cb = ComponentsBlocker.getMutableInstance(context, packageName)) {
                    cb.setAppOp(String.valueOf(AppOpsManager.OP_RUN_IN_BACKGROUND), AppOpsManager.MODE_IGNORED);
                }
            }
        }
        return result;
    }

    @NonNull
    private Result opKill() {
        for (String packageName : packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_PID_PACKAGE, packageName), false);
        }
        Result result = runOpAndFetchResults();
        List<String> pidOrPackageNames = result.failedPackages();
        runner.clear();
        for (int i = 0; i<packageNames.size(); ++i) {
            if (!pidOrPackageNames.get(i).equals(packageNames.get(i))) {
                addCommand(packageNames.get(i), String.format(Locale.ROOT, RunnerUtils.CMD_KILL_SIG9, pidOrPackageNames.get(i)));
            }
        }
        return runOpAndFetchResults();
    }

    @NonNull
    private Result opUninstall() {
        for (String packageName: packageNames) {
            addCommand(packageName, String.format(Locale.ROOT, RunnerUtils.CMD_UNINSTALL_PACKAGE, packageName));
        }
        return runOpAndFetchResults();
    }

    private void addCommand(String packageName, String command) {
        addCommand(packageName, command, true);
    }

    private void addCommand(String packageName, String command, boolean isDevNull) {
        runner.addCommand(String.format(Locale.ROOT, "%s %s || echo %s", command, isDevNull ? "> /dev/null 2>&1" : "", packageName));
    }

    @NonNull
    private Result runOpAndFetchResults() {
        Runner.Result result = runner.runCommand();
        lastResult = new Result() {
            @Override
            public boolean isSuccessful() {
                return TextUtils.isEmpty(result.getOutput());
            }

            @Override
            public List<String> failedPackages() {
                return result.getOutputAsList();
            }
        };
        return lastResult;
    }

    public interface Result {
        boolean isSuccessful();
        List<String> failedPackages();
    }
}
