package io.github.a13e300.tools.ifo;

import android.content.IntentFilter;
import android.os.PatternMatcher;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.BiFunction;

public class Utils {
    private static <T> boolean isInclude(
            @Nullable Iterator<T> from,
            @Nullable Iterator<T> to,
            @Nullable BiFunction<T, T, Boolean> equalsFn
    ) {
        if (from == null || !from.hasNext()) return true;
        if (to == null || !to.hasNext()) return false;
        var s = new ArrayList<T>();
        to.forEachRemaining(s::add);
        while (from.hasNext()) {
            boolean exists = false;
            var e1 = from.next();
            for (var e2 : s) {
                if ((equalsFn != null && equalsFn.apply(e1, e2)) || (equalsFn == null && e1 != null && e1.equals(e2))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) return false;
        }
        return true;
    }

    private static final BiFunction<PatternMatcher, PatternMatcher, Boolean> PATTERN_MATCHER_EQUALS
            = (e1, e2) -> TextUtils.equals(e1.getPath(), e2.getPath()) && e1.getType() == e2.getType();

    public static boolean isIntentFilterMatch(@NonNull IntentFilter from, @NonNull IntentFilter to) {
        return isInclude(from.actionsIterator(), to.actionsIterator(), null)
                && isInclude(from.categoriesIterator(), to.categoriesIterator(), null)
                && isInclude(from.typesIterator(), to.typesIterator(), null)
                && isInclude(from.authoritiesIterator(), to.authoritiesIterator(), null)
                && isInclude(from.schemesIterator(), to.schemesIterator(), null)
                && isInclude(from.pathsIterator(), to.pathsIterator(), PATTERN_MATCHER_EQUALS)
                && isInclude(from.schemeSpecificPartsIterator(), to.schemeSpecificPartsIterator(), PATTERN_MATCHER_EQUALS);
    }

    public static void fillIntentFilter(IntentFilter dst, IntentFilter src) {
        dst.setPriority(src.getPriority());
        var actionsIterator = src.actionsIterator();
        if (actionsIterator != null) actionsIterator.forEachRemaining(dst::addAction);
        var categoriesIterator = src.categoriesIterator();
        if (categoriesIterator != null) categoriesIterator.forEachRemaining(dst::addCategory);
        var typesIterator = src.typesIterator();
        if (typesIterator != null) typesIterator.forEachRemaining(s -> {
            try {
                dst.addDataType(s);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                Logger.e("", e);
            }
        });
        var schemesIterator = src.schemesIterator();
        if (schemesIterator != null) schemesIterator.forEachRemaining(dst::addDataScheme);
        var authoritiesIterator = src.authoritiesIterator();
        if (authoritiesIterator != null) authoritiesIterator.forEachRemaining((e) -> dst.addDataAuthority(e.getHost(), String.valueOf(e.getPort())));
        var pathsIterator = src.pathsIterator();
        if (pathsIterator != null) pathsIterator.forEachRemaining((e) -> dst.addDataPath(e.getPath(), e.getType()));
        var schemeSpecificPartsIterator = src.schemeSpecificPartsIterator();
        if (schemeSpecificPartsIterator != null) schemeSpecificPartsIterator.forEachRemaining((e) -> dst.addDataSchemeSpecificPart(e.getPath(), e.getType()));
    }

    public static String dumpIntentFilter(IntentFilter src) {
        StringBuilder sb = new StringBuilder();
        sb.append("IntentFilter{");
        sb.append(src.hashCode());
        sb.append(",actions=[");
        var actionsIterator = src.actionsIterator();
        if (actionsIterator != null) actionsIterator.forEachRemaining(s -> sb.append(s).append(","));
        sb.append("],categories=[");
        var categoriesIterator = src.categoriesIterator();
        if (categoriesIterator != null) categoriesIterator.forEachRemaining(s -> sb.append(s).append(","));
        var typesIterator = src.typesIterator();
        sb.append("],types=[");
        if (typesIterator != null) typesIterator.forEachRemaining(s -> sb.append(s).append(","));
        sb.append("],schemes=[");
        var schemesIterator = src.schemesIterator();
        if (schemesIterator != null) schemesIterator.forEachRemaining(s -> sb.append(s).append(","));
        sb.append("],auths=[");
        var authoritiesIterator = src.authoritiesIterator();
        if (authoritiesIterator != null) authoritiesIterator.forEachRemaining((e) -> sb.append(e.getHost()).append(":").append(e.getPort()));
        var pathsIterator = src.pathsIterator();
        sb.append("],paths=[");
        if (pathsIterator != null) pathsIterator.forEachRemaining((e) -> sb.append(e.getPath()).append("(type=").append(e.getType()).append(")"));
        var schemeSpecificPartsIterator = src.schemeSpecificPartsIterator();
        sb.append("],schemeSpecificParts=[");
        if (schemeSpecificPartsIterator != null) schemeSpecificPartsIterator.forEachRemaining((e) -> sb.append(e.getPath()).append("(type=").append(e.getType()).append(")"));
        sb.append("]}");
        return sb.toString();
    }
}
