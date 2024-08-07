package io.github.a13e300.tools.ifo;

import android.content.ComponentName;
import android.content.Intent;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArraySet;
import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

public class IFOManager {
    private static final String CONFIG_PATH = "/data/system/ifo";
    private IFOManager() {}

    private static final IFOManager sInstance;

    private IFOConfig mConfig;

    private boolean beforeCalled = false;
    private boolean afterCalled = false;

    private Object mPMS;
    private Object mPMSLock;
    private Map mPMSPackages;
    private Object mComponentResolver;
    private RuleObserver mObserver;
    // on Android 14, ParsedActivity, ParsedIntentInfo, etc don't implement hashCode and equals
    // so we need to cache those result so that we can remove them properly
    private final Map<String, List> overrideCache = new HashMap<>();

    // https://android.googlesource.com/platform/frameworks/base/+/fe011e06b5f1caf35e87c43ce5306234914d5c8c/services/core/java/com/android/server/firewall/IntentFirewall.java
    private class RuleObserver extends FileObserver {
        private static final int MONITORED_EVENTS = FileObserver.CREATE|FileObserver.MOVED_TO|
                FileObserver.CLOSE_WRITE|FileObserver.DELETE|FileObserver.MOVED_FROM;

        private final IFOHandler mHandler;

        public RuleObserver(File monitoredDir, IFOHandler handler) {
            super(monitoredDir.getAbsolutePath(), MONITORED_EVENTS);
            mHandler = handler;
        }

        @Override
        public void onEvent(int event, String path) {
            if (path != null && path.endsWith(".xml")) {
                // we wait 250ms before taking any action on an event, in order to dedup multiple
                // events. E.g. a delete event followed by a create event followed by a subsequent
                // write+close event
                mHandler.removeMessages(0);
                mHandler.sendEmptyMessageDelayed(0, 250);
            }
        }
    }

    private class IFOThread extends HandlerThread {
        public IFOThread() {
            super("IFO");
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            Logger.d("starting file observer ...");
            mObserver = new RuleObserver(new File(CONFIG_PATH), new IFOHandler(getLooper()));
            mObserver.startWatching();
        }
    }

    private class IFOHandler extends Handler {
        IFOHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(@NonNull Message msg) {
            new Thread(IFOManager.this::updateConfig).start();
        }
    }

    static {
        sInstance = new IFOManager();
    }

    void beforePMSLoad(Object pms) {
        if (beforeCalled) throw new IllegalStateException("before has been called!");
        beforeCalled = true;
        mConfig = readConfigs();
        mPMS = pms;
    }

    void afterPMSLoad(Object pms) {
        if (afterCalled)  throw new IllegalStateException("after has been called!");
        afterCalled = true;
        mPMSLock = XposedHelpers.getObjectField(pms, "mLock");
        mPMSPackages = (Map) XposedHelpers.getObjectField(pms, "mPackages");
        mComponentResolver = XposedHelpers.getObjectField(pms, "mComponentResolver");
        new IFOThread().start();
    }

    public static IFOManager getInstance() {
        return sInstance;
    }

    private static IFOConfig readConfigs() {
        IFOConfig c = new IFOConfig();
        var p = new File(CONFIG_PATH);
        if (!p.exists() || !p.isDirectory()) {
            if (!p.mkdirs()) {
                Logger.e("failed to make dir for config path");
                return c;
            }
        }
        var files = p.listFiles();
        if (files == null) {
            Logger.e("config path does not exist");
            return c;
        }
        for (var f: files) {
            if (!f.isFile() || !f.canRead() || !f.getName().endsWith(".xml")) continue;
            var parser = Xml.newPullParser();
            var config = new IFOConfig();
            try {
                Logger.d("reading config: " + f);
                parser.setInput(new FileInputStream(f), "utf-8");
                int outerDepth = parser.getDepth();
                int type;
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                        && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }
                    if ("intent-filter-override".equals(parser.getName())) {
                        config.readFromXml(parser);
                    }
                }
            } catch (Throwable t) {
                Logger.e("failed to read " + f + ", skip", t);
                continue;
            }
            c.merge(config);
        }
        Logger.d("read " + c.overrides.size() + " rules");
        return c;
    }

    private void updateConfig() {
        synchronized (mPMSLock) {
            try {
                Logger.d("updating config");
                Logger.d("removing old overrides");
                var packagesToAdd = new ArraySet<String>();
                for (var name : new ArraySet<>(overrideCache.keySet())) {
                    var p = mPMSPackages.get(name);
                    packagesToAdd.add(name);
                    if (p == null) {
                        Logger.w(name + " does not exists, skip remove");
                        continue;
                    }
                    XposedHelpers.callMethod(mComponentResolver, "removeAllComponents", p, false);
                }
                overrideCache.clear();
                mConfig = readConfigs();
                for (var c : mConfig.overrides.keySet()) {
                    packagesToAdd.add(c.getPackageName());
                }
                Logger.d("adding new overrides for packages: " + packagesToAdd);
                for (var name : packagesToAdd) {
                    var p = mPMSPackages.get(name);
                    if (p == null) {
                        Logger.w(name + " does not exists, skip add");
                        continue;
                    }
                    XposedEntry.callAddAllComponents(mComponentResolver, p, mPMS);
                }
            } catch (Throwable t) {
                Logger.e("failed to update config", t);
            }
        }
    }

    List overrideForPackage(String packageName, List activities, boolean isRemove) {
        if (isRemove) {
            return overrideCache.remove(packageName);
        }
        var result = new ArrayList<>();
        int nOverride = 0;
        for (var activity : activities) {
            var cn = (ComponentName) XposedHelpers.callMethod(activity, "getComponentName");
            var override = mConfig.overrides.get(cn);
            if (override == null) {
                result.add(activity);
                continue;
            }
            Logger.d("overriding for activity " + cn + " (isRemove=" + isRemove + ")");
            // List<ParsedIntentInfo>
            var intents = (List) XposedHelpers.getObjectField(activity, "intents");
            if (intents == null) intents = Collections.emptyList();
            boolean useNewIntents = false;
            var newIntents = new ArrayList<>();
            try {
                var intentsToRemove = new ArrayList<>();
                for (var remove : override.removes) {
                    int j = 0;
                    for (var i : intents) {
                        var intentFilter = XposedEntry.getIntentFilterForInfo(i);
                        if (Utils.isIntentFilterMatch(remove, intentFilter)) {
                            Logger.d("removed:" + Utils.dumpIntentFilter(intentFilter));
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
                for (var add : override.adds) {
                    var intentInfo = XposedHelpers.newInstance(
                            XposedEntry.classParsedIntentInfo
                    );
                    Utils.fillIntentFilter(XposedEntry.getIntentFilterForInfo(intentInfo), add);
                    XposedEntry.setIntentInfoHasDefault(intentInfo, add.hasCategory(Intent.CATEGORY_DEFAULT));
                    newIntents.add(intentInfo);
                }
                Logger.d("added " + override.adds.size() + " intents for activity " + cn);
                if (!intentsToRemove.isEmpty() || !override.adds.isEmpty()) {
                    useNewIntents = true;
                    /*
                    Logger.d("final intents:");
                    for (var i : newIntents) {
                        Logger.d(Utils.dumpIntentFilter((IntentFilter) i));
                    }*/
                }
            } catch (Throwable t) {
                Logger.e("error occurred while overriding for " + cn, t);
                throw t;
            }
            if (useNewIntents) {
                nOverride++;
                var newActivity = XposedHelpers.newInstance(XposedEntry.classParsedActivity, activity);
                try {
                    XposedEntry.intentsField.set(newActivity, newIntents);
                } catch (Throwable t) {
                    Logger.e("failed to set", t);
                }
                result.add(newActivity);
            } else {
                result.add(activity);
            }
        }
        if (nOverride > 0) {
            overrideCache.put(packageName, result);
            return result;
        }
        return null;
    }
}
