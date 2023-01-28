package fivecc.tools.ifo;

import android.content.ComponentName;
import android.content.IntentFilter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IFOConfig {
    private static final String TAG_ACTIVITY = "activity";

    public Map<ComponentName, OverrideForActivity> overrides;

    public static final class OverrideForActivity {
        private static final String TAG_INTENT_FILTER = "intent-filter";
        private static final String TAG_ADD = "add";
        private static final String TAG_REMOVE = "remove";
        private static final String ATTRIBUTE_NAME = "name";

        public ComponentName activity;
        public List<IntentFilter> adds;
        public List<IntentFilter> removes;

        public OverrideForActivity() {
            adds = new ArrayList<>();
            removes = new ArrayList<>();
        }

        public void readFromXml(XmlPullParser parser) throws XmlPullParserException, IOException {
            String name = parser.getAttributeValue(null, ATTRIBUTE_NAME);
            activity = ComponentName.unflattenFromString(name);

            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG
                        || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (TAG_ADD.equals(tagName)) {
                    readIntentFiltersFromXml(adds, parser);
                } else if (TAG_REMOVE.equals(tagName)) {
                    readIntentFiltersFromXml(removes, parser);
                }
            }
        }

        private static void readIntentFiltersFromXml(List<IntentFilter> l, XmlPullParser parser) throws XmlPullParserException, IOException {
            int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG
                        || type == XmlPullParser.TEXT) {
                    continue;
                }

                String tagName = parser.getName();
                if (TAG_INTENT_FILTER.equals(tagName)) {
                    IntentFilter intent = new IntentFilter();
                    intent.readFromXml(parser);
                    l.add(intent);
                }
            }
        }
    }

    public IFOConfig() {
        overrides = new HashMap<>();
    }

    public void readFromXml(XmlPullParser parser) throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG
                    || type == XmlPullParser.TEXT) {
                continue;
            }

            String tagName = parser.getName();
            if (TAG_ACTIVITY.equals(tagName)) {
                var o = new OverrideForActivity();
                o.readFromXml(parser);
                overrides.put(o.activity, o);
            }
        }
    }
}
