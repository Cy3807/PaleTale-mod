package cn.paletale.companion;
import java.time.*;
/** Beijing calendar shortcuts shared by filtering and jump-to-time. */
public final class ChatDates {
 public static LocalDate parse(String input,LocalDate now){String s=input.strip();String[] parts=s.split("-",-1);return switch(parts.length){case 1->now.withDayOfMonth(Integer.parseInt(s));case 2->LocalDate.of(now.getYear(),Integer.parseInt(parts[0]),Integer.parseInt(parts[1]));case 3->LocalDate.of(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));default->throw new IllegalArgumentException();};}
 public static long jump(String input,ZoneId zone){String value=input.strip().replace("年","-").replace("月","-").replace("日"," ").replace("时",":").replace("分",":").replace("秒","").replace("T"," ").strip();if(value.matches("[0-9]{1,2}:[0-9]{2}(:[0-9]{2})?"))value=LocalDate.now(zone)+" "+value;String[] p=value.split("\\s+",2);LocalDate day=parse(p[0],LocalDate.now(zone));LocalTime time=p.length==1?LocalTime.MIDNIGHT:LocalTime.parse(p[1]);return day.atTime(time).atZone(zone).toInstant().toEpochMilli();}
 private ChatDates(){}
}
