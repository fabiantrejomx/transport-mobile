package com.bng.drivo.util;

import android.content.Context;
import android.util.TypedValue;

public final class ColorUtils {

    private ColorUtils() {
    }

    public static int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
}
