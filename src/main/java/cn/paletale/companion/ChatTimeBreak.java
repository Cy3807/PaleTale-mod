package cn.paletale.companion;
import java.time.*;
import java.time.format.DateTimeFormatter;
public final class ChatTimeBreak {
 public static boolean needed(long previous,long current){return previous>0&&current-previous>3_600_000L;}
 public static String label(long previous,long current,ZoneId zone){var before=Instant.ofEpochMilli(previous).atZone(zone);var now=Instant.ofEpochMilli(current).atZone(zone);return now.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));}
 public static String firstLabel(long current,ZoneId zone){return Instant.ofEpochMilli(current).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));}
 private ChatTimeBreak(){}
}
