package fivecc.tools.ifo;

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
    static Class<?> classParsedActivity;
    static Field intentsField;

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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Logger.d("inject...");
        if (!lpparam.packageName.equals("android") || !lpparam.processName.equals("android")) {
            Logger.e("not target (" + lpparam.packageName + "/" + lpparam.processName + ")");
            return;
        }
        classParsedIntentInfo = XposedHelpers.findClass("android.content.pm.parsing.component.ParsedIntentInfo", lpparam.classLoader);
        intentsField = XposedHelpers.findField(
                XposedHelpers.findClass("android.content.pm.parsing.component.ParsedActivity", lpparam.classLoader),
                "intents"
        );
        classParsedActivity = XposedHelpers.findClass("android.content.pm.parsing.component.ParsedActivity", lpparam.classLoader);
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
        var classComponentResolver = XposedHelpers.findClass("com.android.server.pm.ComponentResolver", lpparam.classLoader);
        XposedBridge.hookAllMethods(
                classComponentResolver,
                "addActivitiesLocked",
                new MethodHook() {
                    @Override
                    protected void before(MethodHookParam param) throws Throwable {
                        // AndroidPackage
                        var p = param.args[0];
                        if (p == null) return;
                        // List<ParsedActivity>
                        var activities = (List<?>) XposedHelpers.callMethod(p, "getActivities");
                        if (activities == null || activities.isEmpty()) return;
                        var list = IFOManager.getInstance().overrideForPackage(
                                (String) XposedHelpers.callMethod(p, "getPackageName"),
                                activities,
                                false
                        );
                        if (list != null) {
                            var handler = new AndroidPackageProxy(p, list);
                            param.args[0] = Proxy.newProxyInstance(lpparam.classLoader, new Class<?>[] { XposedHelpers.findClass("com.android.server.pm.parsing.pkg.AndroidPackage", lpparam.classLoader) }, handler);
                        }
                    }
                }
        );
        XposedBridge.hookAllMethods(
                classComponentResolver,
                "removeAllComponentsLocked",
                new MethodHook() {
                    @Override
                    protected void before(MethodHookParam param) throws Throwable {
                        // AndroidPackage
                        var p = param.args[0];
                        if (p == null) return;
                        // List<ParsedActivity>
                        var activities = (List<?>) XposedHelpers.callMethod(p, "getActivities");
                        if (activities == null || activities.isEmpty()) return;
                        var list = IFOManager.getInstance().overrideForPackage(
                                (String) XposedHelpers.callMethod(p, "getPackageName"),
                                activities,
                                true
                        );
                        if (list != null) {
                            var handler = new AndroidPackageProxy(p, list);
                            param.args[0] = Proxy.newProxyInstance(lpparam.classLoader, new Class<?>[] { XposedHelpers.findClass("com.android.server.pm.parsing.pkg.AndroidPackage", lpparam.classLoader) }, handler);
                        }
                    }
                }
        );
    }
}
