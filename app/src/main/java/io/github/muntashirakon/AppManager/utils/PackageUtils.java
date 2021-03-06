package io.github.muntashirakon.AppManager.utils;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import dalvik.system.DexFile;
import io.github.muntashirakon.AppManager.AppManager;
import io.github.muntashirakon.AppManager.runner.Runner;
import io.github.muntashirakon.AppManager.storage.RulesStorageManager;

public final class PackageUtils {
    private static final Pattern SERVICE_REGEX = Pattern.compile("ServiceRecord\\{.*/([^\\}]+)\\}");

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> collectComponentClassNames(String packageName) {
        HashMap<String, RulesStorageManager.Type> componentClasses = new HashMap<>();
        PackageManager packageManager = AppManager.getContext().getPackageManager();
        PackageInfo packageInfo;
        try {
            int apiCompatFlags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                apiCompatFlags = PackageManager.MATCH_DISABLED_COMPONENTS;
            else apiCompatFlags = PackageManager.GET_DISABLED_COMPONENTS;
            packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES
                    | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS | apiCompatFlags
                    | PackageManager.GET_SERVICES | PackageManager.GET_URI_PERMISSION_PATTERNS);
            // Add activities
            if (packageInfo.activities != null) {
                for (ActivityInfo activityInfo : packageInfo.activities) {
                    if (activityInfo.targetActivity != null)
                        componentClasses.put(activityInfo.targetActivity, RulesStorageManager.Type.ACTIVITY);
                    else componentClasses.put(activityInfo.name, RulesStorageManager.Type.ACTIVITY);
                }
            }
            // Add others
            if (packageInfo.services != null) {
                for (ComponentInfo componentInfo : packageInfo.services)
                    componentClasses.put(componentInfo.name, RulesStorageManager.Type.SERVICE);
            }
            if (packageInfo.receivers != null) {
                for (ComponentInfo componentInfo : packageInfo.receivers)
                    componentClasses.put(componentInfo.name, RulesStorageManager.Type.RECEIVER);
            }
            if (packageInfo.providers != null) {
                for (ComponentInfo componentInfo : packageInfo.providers)
                    componentClasses.put(componentInfo.name, RulesStorageManager.Type.PROVIDER);
            }
        } catch (PackageManager.NameNotFoundException ignore) {}
        return componentClasses;
    }

    @NonNull
    public static List<String> getClassNames(byte[] bytes) {
        ArrayList<String> classNames = new ArrayList<>();
        File incomeFile = null;
        File optimizedFile = null;
        try {
            File cacheDir = AppManager.getContext().getCacheDir();
            incomeFile = File.createTempFile("classes_" + System.currentTimeMillis(), ".dex", cacheDir);
            IOUtils.bytesToFile(bytes, incomeFile);
            optimizedFile = File.createTempFile("opt_" + System.currentTimeMillis(), ".dex", cacheDir);
            DexFile dexFile = DexFile.loadDex(incomeFile.getPath(), optimizedFile.getPath(), 0);
            classNames = Collections.list(dexFile.entries());
            dexFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (incomeFile != null) //noinspection ResultOfMethodCallIgnored
                incomeFile.delete();
            if (optimizedFile != null) //noinspection ResultOfMethodCallIgnored
                optimizedFile.delete();
        }
        return classNames;
    }

    @NonNull
    public static HashMap<String, RulesStorageManager.Type> getUserDisabledComponentsForPackage(String packageName) {
        HashMap<String, RulesStorageManager.Type> componentClasses = collectComponentClassNames(packageName);
        HashMap<String, RulesStorageManager.Type> disabledComponents = new HashMap<>();
        PackageManager pm = AppManager.getContext().getPackageManager();
        for (String componentName: componentClasses.keySet()) {
            if (isComponentDisabledByUser(pm, packageName, componentName))
                disabledComponents.put(componentName, componentClasses.get(componentName));
        }
        // FIXME: get components disabled by IFW
        return disabledComponents;
    }

    public static boolean isComponentDisabledByUser(@NonNull PackageManager pm, @NonNull String packageName, @NonNull String componentClassName) {
        ComponentName componentName = new ComponentName(packageName, componentClassName);
        switch (pm.getComponentEnabledSetting(componentName)) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED: return true;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            default: return false;
        }
    }

    @NonNull
    public static List<String> getRunningServicesForPackage(String packageName) {
        List<String> runningServices = new ArrayList<>();
        Runner.runCommand("dumpsys activity services -p " + packageName);
        if (Runner.getLastResult().isSuccessful()) {
            List<String> serviceDump = Runner.getLastResult().getOutputAsList();
            Matcher matcher;
            String service, line;
            ListIterator<String> it = serviceDump.listIterator();
            if (it.hasNext()) {
                matcher = SERVICE_REGEX.matcher(it.next());
                while (it.hasNext()) {
                    if (matcher.find(0)) {
                        service = matcher.group(1);
                        line = it.next();
                        matcher = SERVICE_REGEX.matcher(line);
                        while (it.hasNext()) {
                            if (matcher.find(0)) break;
                            if (line.contains("app=ProcessRecord{")) {
                                if (service != null) {
                                    runningServices.add(service.startsWith(".") ? packageName + service : service);
                                }
                                break;
                            }
                            line = it.next();
                            matcher = SERVICE_REGEX.matcher(line);
                        }
                    } else matcher = SERVICE_REGEX.matcher(it.next());
                }
            }
        }
        return runningServices;
    }
}
