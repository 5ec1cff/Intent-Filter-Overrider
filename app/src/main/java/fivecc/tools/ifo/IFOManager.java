package fivecc.tools.ifo;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;

public class IFOManager {
    private static final String CONFIG_PATH = "/data/system/ifo.xml";
    private IFOManager() {
        mConfig = readConfig();
    }

    private static final IFOManager sInstance;

    private IFOConfig mConfig;

    static {
        sInstance = new IFOManager();
    }

    public static IFOManager getInstance() {
        return sInstance;
    }

    private IFOConfig readConfig() {
        var f = new File(CONFIG_PATH);
        IFOConfig c = new IFOConfig();
        if (!f.exists() || !f.isFile()) return c;
        var parser = Xml.newPullParser();
        try {
            parser.setInput(new FileInputStream(f), "utf-8");
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                if ("intent-filter-override".equals(parser.getName())) {
                    c.readFromXml(parser);
                }
            }
            return c;
        } catch (Throwable t) {
            Logger.e("failed to read", t);
        }
        return c;
    }

    public IFOConfig getConfig() {
        synchronized (this) {
            return mConfig;
        }
    }
}
