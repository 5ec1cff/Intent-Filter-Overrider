package fivecc.tools.ifo;

import android.content.ComponentName;
import android.content.IntentFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {
        Logger.d("inject...");
        if (!param.packageName.equals("android") || !param.processName.equals("android")) {
            Logger.e("not target (" + param.packageName + "/" + param.processName + ")");
            return;
        }
        var classParsedIntentInfo = XposedHelpers.findClass("android.content.pm.parsing.component.ParsedIntentInfo", param.classLoader);
        var intentsField = XposedHelpers.findField(
                XposedHelpers.findClass("android.content.pm.parsing.component.ParsedActivity", param.classLoader),
                "intents"
        );
        XposedHelpers.findAndHookMethod(
                XposedHelpers.findClass("com.android.server.pm.parsing.PackageParser2", param.classLoader),
                "parsePackage",
                File.class,
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // ParsedPackage
                        var p = param.getResult();
                        if (p == null) return;
                        // List<ParsedActivity>
                        var activities = (List<?>) XposedHelpers.callMethod(p, "getActivities");
                        if (activities == null) return;
                        IFOConfig config = IFOManager.getInstance().getConfig();
                        for (var activity: activities) {
                            var cn = (ComponentName) XposedHelpers.callMethod(activity, "getComponentName");
                            var override = config.overrides.get(cn);
                            if (override == null) continue;
                            Logger.d("overriding for activity " + cn);
                            // List<ParsedIntentInfo>
                            var intents = (List) XposedHelpers.getObjectField(activity, "intents");
                            if (intents == null) intents = Collections.emptyList();
                            var newIntents = new ArrayList<>();
                            try {
                                var intentsToRemove = new ArrayList<>();
                                for (var remove : override.removes) {
                                    int j = 0;
                                    for (var i : intents) {
                                        if (Utils.isIntentFilterMatch(remove, (IntentFilter) i)) {
                                            Logger.d("removed:" + Utils.dumpIntentFilter((IntentFilter) i));
                                            intentsToRemove.add(i);
                                            j++;
                                        }
                                        if (j > 1) {
                                            Logger.d("warning: multiple intent-filters matched to remove: " + remove);
                                        }
                                    }
                                }
                                Logger.d("remove " + intentsToRemove.size() + " intents for activity " + cn);
                                for (var i : intents) {
                                    if (!intentsToRemove.contains(i)) newIntents.add(i);
                                }
                                int n = 0;
                                for (var add : override.adds) {
                                    var intentInfo = XposedHelpers.newInstance(classParsedIntentInfo);
                                    Utils.fillIntentFilter((IntentFilter) intentInfo, add);
                                    newIntents.add(intentInfo);
                                    n++;
                                }
                                Logger.d("added " + n + " intents for activity " + cn);
                            } catch (Throwable t) {
                                Logger.e("error occurred while overriding for " + cn, t);
                                return;
                            }
                            Logger.d("final intents:");
                            for (var i: newIntents) {
                                Logger.d(Utils.dumpIntentFilter((IntentFilter) i));
                            }
                            try {
                                intentsField.set(activity, newIntents);
                            } catch (Throwable t) {
                                Logger.e("failed to set", t);
                            }
                        }
                    }
                }
        );
    }
}
