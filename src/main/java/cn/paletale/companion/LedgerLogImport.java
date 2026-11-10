package cn.paletale.companion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Read-only bounded historical log scan. Never reads or writes the live ledger. */
public final class LedgerLogImport {
    private static final Pattern FILE=Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log\\.gz$");
    private static final Pattern LINE=Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})] .*?\\[CHAT] (.*)$");
    /** How much one log may expand to before it is cut off (tests lower it to stay quick). */
    static long maxExpanded=512L*1024*1024;
    private static final int[] TIME_DIGITS={1,2,4,5,7,8};
    public record Result(List<LedgerEntry> entries,int files,int skipped) {}
    private LedgerLogImport() {}
    public static Result read(Path logs,String account,String player,LocalDate from,LocalDate to,ZoneId logZone)throws IOException {
        if(account.isBlank()||player.isBlank()||from.isAfter(to)||!Files.isDirectory(logs))return new Result(List.of(),0,0);
        long start=from.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),end=to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        var files=new ArrayList<Path>();try(var stream=Files.list(logs)){stream.filter(p->FILE.matcher(p.getFileName().toString()).matches()||p.getFileName().toString().equals("latest.log")).limit(2000).forEach(files::add);}
        files.sort(Comparator.comparing(p->p.getFileName().toString()));var relevant=new ArrayList<Path>();int skipped=0;
        for(Path file:files){try{var m=FILE.matcher(file.getFileName().toString());LocalDate day=m.matches()?LocalDate.parse(m.group(1)):Files.getLastModifiedTime(file).toInstant().atZone(logZone).toLocalDate();if(!day.isBefore(from.minusDays(2))&&!day.isAfter(to.plusDays(2)))relevant.add(file);}catch(IOException|RuntimeException invalid){skipped++;}}
        files=relevant;var entries=new ArrayList<LedgerEntry>();int scanned=0;var budget=new Budget();
        if(files.size()>128){skipped+=files.size()-128;files=new ArrayList<>(files.subList(files.size()-128,files.size()));}
        // Newest first: if a cap ends the scan early, it is the oldest history that is left out, not last week's chat.
        java.util.Collections.reverse(files);
        for(Path file:files){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");String name=file.getFileName().toString();var matcher=FILE.matcher(name);LocalDate date;
            if(matcher.matches()){try{date=LocalDate.parse(matcher.group(1));}catch(RuntimeException invalid){skipped++;continue;}}else date=Files.getLastModifiedTime(file).toInstant().atZone(logZone).toLocalDate();
            if(date.isBefore(from.minusDays(2))||date.isAfter(to.plusDays(2)))continue;
            if(Files.size(file)>32_000_000){skipped++;continue;}
            var batch=new ArrayList<LedgerEntry>();
            try(InputStream input=Files.newInputStream(file);InputStream zipped=name.endsWith(".gz")?new GZIPInputStream(input):input;InputStream limited=new Limited(zipped,maxExpanded,budget);BufferedReader reader=new BufferedReader(new InputStreamReader(limited,LogCharset.of(file)))){
                replay(reader,name,date,logZone,account,player,start,end,batch,name.equals("latest.log")?Files.getLastModifiedTime(file).toInstant().atZone(logZone).toLocalTime().toSecondOfDay():-1);if(entries.size()+batch.size()>20000){skipped++;break;}entries.addAll(batch);scanned++;
            }catch(InterruptedIOException cancelled){throw cancelled;}catch(IOException|RuntimeException invalid){
                // A log that is too big or cut off in the middle (a crash, a game still writing it) still gives what was read before that point.
                skipped++;if(!batch.isEmpty()&&entries.size()+batch.size()<=20000){entries.addAll(batch);}
            }
            if(entries.size()>=20000){skipped++;break;}
        }
        entries.sort(Comparator.comparingLong(LedgerEntry::time));return new Result(List.copyOf(entries),scanned,skipped);
    }
    private static void replay(BufferedReader reader,String file,LocalDate date,ZoneId zone,String account,String name,long from,long to,List<LedgerEntry> out,int latestSecond)throws IOException{
        boolean mine=false,simmc=false,transfer=false,firstTime=true;String line;int lineNo=0,lastSecond=-1;var talk=new LedgerShopTalk();
        while((line=boundedLine(reader))!=null&&lineNo++<500000&&out.size()<20000){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");
            int lineSecond=timestamp(line);if(lineSecond>=0){if(firstTime){if(latestSecond>=0&&lineSecond>latestSecond+3600)date=date.minusDays(1);firstTime=false;}else if(lastSecond>=0&&lineSecond<lastSecond-3600)date=date.plusDays(1);lastSecond=lineSecond;}
            if(!line.contains("[CHAT]")&&line.contains("Setting user:")){mine=line.substring(line.indexOf("Setting user:")+13).strip().equalsIgnoreCase(name);simmc=false;}
            if(simmc&&(line.contains("[CHAT] 已转移至另一个服务器")||line.contains("[CHAT] Transferred to another server")||!line.contains("[CHAT]")&&(line.contains("已转移至另一个服务器")||line.contains("Transferred to another server"))))transfer=true;
            if(!line.contains("[CHAT]")&&!line.contains("[PlasmoVoice]")&&line.contains("Connecting to ")){simmc=transfer||line.matches("(?i).*Connecting to (?:play\\.simmc\\.cn|simmc\\.silkysoftware\\.com|simmc\\.cn)\\.?, .*" );transfer=false;talk=new LedgerShopTalk();}
            var m=LINE.matcher(line);if(!mine||!simmc||!m.matches())continue;
            if(lineSecond<0)continue;long at=LocalDateTime.of(date,LocalTime.ofSecondOfDay(lineSecond)).atZone(zone).toInstant().toEpochMilli();String raw=m.group(2);if(raw.length()>2000)continue;
            String clean=LedgerParser.clean(raw);if(clean.startsWith("[")||clean.startsWith("【")||clean.startsWith("<"))continue;
            if(at<from){if(at>=from-600_000L)try{talk.feed(raw,at);}catch(RuntimeException ignored){}continue;}if(at>=to)continue;
            Optional<LedgerEntry> found;try{found=LedgerParser.parse(raw,name,at);if(found.isEmpty())found=talk.feed(raw,at);}catch(RuntimeException malformed){continue;}
            if(found.isEmpty()||!found.get().recordable())continue;LedgerEntry e=found.get().withAccount(account,name);String id="log:"+UUID.nameUUIDFromBytes((file+":"+lineNo+":"+e.raw()).getBytes(StandardCharsets.UTF_8));
            out.add(new LedgerEntry(id,e.time(),e.kind(),e.currency(),e.amount(),e.counterparty(),e.item(),e.quantity(),e.x(),e.y(),e.z(),"","","",account,name,e.raw()));
        }
    }
    private static int timestamp(String line){
        if(line.length()<10||line.charAt(0)!='['||line.charAt(3)!=':'||line.charAt(6)!=':'||line.charAt(9)!=']')return -1;
        int value=0;for(int offset:TIME_DIGITS){char c=line.charAt(offset);if(c<'0'||c>'9')return -1;value=value*10+c-'0';}
        int hour=value/10000,minute=value/100%100,second=value%100;return hour<24&&minute<60&&second<60?hour*3600+minute*60+second:-1;
    }
    /** Discard overlong lines without allocating their full contents. */
    private static String boundedLine(BufferedReader reader)throws IOException {
        var line=new StringBuilder();boolean overflow=false;int ch;
        while((ch=reader.read())!=-1&&ch!='\n'){if(line.length()<8192)line.append((char)ch);else overflow=true;}
        if(ch==-1&&line.isEmpty()&&!overflow)return null;
        // Windows logs end their lines with CRLF; a stray CR would make every whole-line match fail.
        if(!line.isEmpty()&&line.charAt(line.length()-1)=='\r')line.setLength(line.length()-1);
        return overflow?"":line.toString();
    }
    private static final class Budget {long left=3L*1024*1024*1024;}
    private static final class Limited extends FilterInputStream {
        private long left;private final Budget budget;Limited(InputStream input,long maximum,Budget budget){super(input);left=maximum;this.budget=budget;}
        @Override public int read()throws IOException{if(left<=0||budget.left<=0)throw new IOException("log expansion limit");int x=super.read();if(x>=0){left--;budget.left--;}return x;}
        @Override public int read(byte[] b,int off,int len)throws IOException{if(left<=0||budget.left<=0)throw new IOException("log expansion limit");int n=super.read(b,off,(int)Math.min(len,Math.min(left,budget.left)));if(n>0){left-=n;budget.left-=n;}return n;}
    }
}
