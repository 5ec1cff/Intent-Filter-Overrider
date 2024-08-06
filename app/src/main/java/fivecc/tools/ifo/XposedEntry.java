package fivecc.tools.ifo;

import android.content.IntentFilter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedEntry implements IXposedHookLoadPackage {
    static class AndroidPackageProxy implements InvocationHandler {
        Object mActivities;
        Object mOrig;
        AndroidPackageProxy(Object orig, Object newActivities) {
            mOrig = orig;
            mActivities = newActivities;
        }
        @Override
        public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            if ("getActivities".equals(method.getName())) {
                return mActivities;
            }
            return method.invoke(mOrig, objects);
        }
    }

    static Class<?> classParsedIntentInfo;
    static boolean classParsedIntentInfoExtendsIntentFilter = false;
    static Class<?> classParsedActivity;
    static Class<?> classAndroidPackage;
    static Field intentsField;
    static ClassLoader classLoader;
    static Method methodAddAllComponents;
    static boolean newMethodAddAllComponents;

    static abstract class MethodHook extends XC_MethodHook {
        @Override
        protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
            try {
                before(param);
            } catch (Throwable t) {
                Logger.e("error on hook before " + param.method.getName(), t);
            }
        }

        @Override
        protected final void afterHookedMethod(MethodHookParam param) throws Throwable {
            try {
                after(param);
            } catch (Throwable t) {
                Logger.e("error on hook after " + param.method.getName(), t);
            }
        }

        protected void before(MethodHookParam param) throws Throwable {}
        protected void after(MethodHookParam param) throws Throwable {}
    }

    private static Class<?> findClass(String ...names) throws ClassNotFoundException {
        for (var name: names) {
            try {
                return XposedHelpers.findClass(name, classLoader);
            } catch (XposedHelpers.ClassNotFoundError ignored) {

            }
        }
        throw new ClassNotFoundException("class not found: " + String.join(",", names));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android") || !lpparam.processName.equals("android")) {
            // Logger.e("not target (" + lpparam.packageName + "/" + lpparam.processName + ")");
            return;
        }
        Logger.i("IFO inject into android");
        classLoader = lpparam.classLoader;
        classParsedIntentInfo = findClass("com.android.internal.pm.pkg.component.ParsedIntentInfoImpl", "com.android.server.pm.pkg.component.ParsedIntentInfoImpl", "android.content.pm.parsing.component.ParsedIntentInfo");
        classParsedIntentInfoExtendsIntentFilter = IntentFilter.class.isAssignableFrom(classParsedIntentInfo);
        classParsedActivity = findClass("com.android.internal.pm.pkg.component.ParsedActivityImpl", "com.android.server.pm.pkg.component.ParsedActivityImpl", "android.content.pm.parsing.component.ParsedActivity");
        intentsField = XposedHelpers.findField(classParsedActivity, "intents");
        classAndroidPackage = findClass("com.android.server.pm.pkg.AndroidPackage", "com.android.server.pm.parsing.pkg.AndroidPackage");
        var classComponentResolver = XposedHelpers.findClass("com.android.server.pm.resolution.ComponentResolver", lpparam.classLoader);
        XposedBridge.hookAllConstructors(
                XposedHelpers.findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader),
                new MethodHook() {
                    @Override
                    protected void before(MethodHookParam param) throws Throwable {
                        IFOManager.getInstance().beforePMSLoad(param.thisObject);
                    }

                    @Override
                    protected void after(MethodHookParam param) throws Throwable {
                        IFOManager.getInstance().afterPMSLoad(param.thisObject);
                    }
                }
        );
        Method addActivitiesLocked = null;
        Method removeAllComponentsLocked = null;
        int addActivitiesLockedPackageIdx = -1;
        int removeAllComponentsLockedPackageIdx = -1;
        for (var m: classComponentResolver.getDeclaredMethods()) {
            if ("addActivitiesLocked".equals(m.getName())) {
                addActivitiesLocked = m;
                var types = m.getParameterTypes();
                for (var t: types) {
                    addActivitiesLockedPackageIdx++;
                    if (t.equals(classAndroidPackage)) break;
                }
            } else if ("removeAllComponentsLocked".equals(m.getName())) {
                removeAllComponentsLocked = m;
                var types = m.getParameterTypes();
                for (var t: types) {
                    removeAllComponentsLockedPackageIdx++;
                    if (t.equals(classAndroidPackage)) break;
                }
            } else if ("addAllComponents".equals(m.getName())) {
                methodAddAllComponents = m;
                newMethodAddAllComponents = m.getParameterCount() == 4;
            }
        }
        int finalAddActivitiesLockedPackageIdx = addActivitiesLockedPackageIdx;
        XposedBridge.hookMethod(
                addActivitiesLocked,
                new MethodHook() {
                    @Override
                    protected void before(MethodHookParam param) throws Throwable {
                        // AndroidPackage
                        var p = param.args[finalAddActivitiesLockedPackageIdx];
                        if (p == null) return;
                        // List<ParsedActivity>
                        var activities = (List<?>) XposedHelpers.callMethod(p, "getActivities");
                        if (activities == null || activities.isEmpty()) return;
                        var packageName = (String) XposedHelpers.callMethod(p, "getPackageName");
                        var list = IFOManager.getInstance().overrideForPackage(
                                packageName,
                                activities,
                                false
                        );
                        if (list != null) {
                            var handler = new AndroidPackageProxy(p, list);
                            param.args[finalAddActivitiesLockedPackageIdx] = Proxy.newProxyInstance(lpparam.classLoader, new Class<?>[] { classAndroidPackage }, handler);
                        }
                    }
                }
        );
        int finalRemoveAllComponentsLockedPackageIdx = removeAllComponentsLockedPackageIdx;
        XposedBridge.hookMethod(
                removeAllComponentsLocked,
                new MethodHook() {
                    @Override
                    protected void before(MethodHookParam param) throws Throwable {
                        // AndroidPackage
                        var p = param.args[finalRemoveAllComponentsLockedPackageIdx];
                        if (p == null) return;
                        // List<ParsedActivity>
                        var activities = (List<?>) XposedHelpers.callMethod(p, "getActivities");
                        if (activities == null || activities.isEmpty()) return;
                        var packageName = (String) XposedHelpers.callMethod(p, "getPackageName");
                        var list = IFOManager.getInstance().overrideForPackage(
                                packageName,
                                activities,
                                true
                        );
                        if (list != null) {
                            var handler = new AndroidPackageProxy(p, list);
                            param.args[finalRemoveAllComponentsLockedPackageIdx] = Proxy.newProxyInstance(lpparam.classLoader, new Class<?>[] { classAndroidPackage }, handler);
                        }
                    }
                }
        );
    }

    public static void callAddAllComponents(Object self, Object p, Object pms) throws Throwable {
        if (newMethodAddAllComponents) {
            methodAddAllComponents.invoke(self, p, false, XposedHelpers.getObjectField(pms, "mSetupWizardPackage"), XposedHelpers.callMethod(pms, "snapshotComputer"));
        } else {
            methodAddAllComponents.invoke(self, p, false);
        }
    }

    public static IntentFilter getIntentFilterForInfo(Object info) {
        if (classParsedIntentInfoExtendsIntentFilter) return (IntentFilter) info;
        else return (IntentFilter) XposedHelpers.getObjectField(info, "mIntentFilter");
    }

    public static void setIntentInfoHasDefault(Object info, boolean has) {
        if (classParsedIntentInfoExtendsIntentFilter) return;
        XposedHelpers.callMethod(info, "setHasDefault", has);
    }
}
