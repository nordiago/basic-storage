package com.khazoda.basicstorage.util;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class NumberFormatter {

  public static String toFormattedNumber(double value) {
    DecimalFormat df = new DecimalFormat("###,###,###,###");
    return df.format(value);
  }

  private static final NavigableMap<Long, String> suffixes = new TreeMap<>();

  static {
    suffixes.put(1_000L, "K");
    suffixes.put(1_000_000L, "M");
    suffixes.put(1_000_000_000L, "B");
  }

  public static String format(long value) {
    if (value == Long.MIN_VALUE) return format(Long.MIN_VALUE + 1);
    if (value < 0) return "-" + format(-value);
    if (value < 100000) return toFormattedNumber(value);

    Map.Entry<Long, String> e = suffixes.floorEntry(value);
    Long divideBy = e.getKey();
    String suffix = e.getValue();

    long truncated = value / (divideBy / 10);
    boolean hasDecimal = truncated < 100 && (truncated / 10d) != ((double) truncated / 10);
    return hasDecimal ? (truncated / 10d) + suffix : (truncated / 10) + suffix;
  }
}
