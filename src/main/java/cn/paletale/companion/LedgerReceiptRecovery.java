package cn.paletale.companion;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** One-time repair from dated SIMMC logs, only for exact receipts the old parser put in unmatched. */
public final class LedgerReceiptRecovery {
    private LedgerReceiptRecovery() {}
    private static final Pattern FILE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log\\.gz$");
    private static final Pattern LINE = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})] .*?\\[CHAT] (.*)$");
    public static List<LedgerEntry> read(Path logs, String account, String name, List<String> unmatched, List<LedgerEntry> existing, ZoneId clock, LocalDate today) throws IOException {
        if(!Files.isDirectory(logs)||name.isBlank())return List.of();
        Set<String> wanted=new HashSet<>();
        for(String raw:unmatched)if(LedgerOfficial.parse(raw,0,Map.of()).isPresent())wanted.add(LedgerParser.clean(raw));
        if(wanted.isEmpty())return List.of();
        List<LedgerEntry> recovered=new ArrayList<>();
        List<Path> files;
        try(var stream=Files.list(logs)){files=stream.filter(p->FILE.matcher(p.getFileName().toString()).matches()).sorted(Comparator.reverseOrder()).limit(40).toList();}
        for(var file:files){
            var match=FILE.matcher(file.getFileName().toString());if(!match.matches())continue;
            LocalDate date=LocalDate.parse(match.group(1));if(date.isBefore(today.minusDays(14))||date.isAfter(today)||Files.size(file)>8_000_000)continue;
            try(var reader=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)),java.nio.charset.StandardCharsets.UTF_8))){
                var rows=replay(reader,file.getFileName().toString(),date,clock,account,name,wanted,existing);
                recovered.addAll(rows);if(recovered.size()>20000)break;
            }catch(java.util.zip.ZipException ignored){}
        }
        recovered.sort(Comparator.comparingLong(LedgerEntry::time));return List.copyOf(recovered);
    }
    public static List<LedgerEntry> replay(BufferedReader reader,String file,LocalDate date,ZoneId clock,String account,String name,Set<String> wanted,List<LedgerEntry> existing) throws IOException {
        List<LedgerEntry> result=new ArrayList<>();boolean mine=false,simmc=false,transfer=false;String line;int index=0,lastSecond=-1;
        while((line=reader.readLine())!=null&&index++<500000){
            if(!line.contains("[CHAT]")&&line.contains("Setting user:")){mine=line.substring(line.indexOf("Setting user:")+13).strip().equalsIgnoreCase(name);simmc=false;}
            if(!line.contains("[CHAT]")&&simmc&&(line.contains("已转移至另一个服务器")||line.contains("Transferred to another server")))transfer=true;
            if(!line.contains("[CHAT]")&&!line.contains("[PlasmoVoice]")&&line.contains("Connecting to ")){simmc=transfer||line.matches(".*Connecting to (?:play\\.)?simmc\\.cn, .*" );transfer=false;}
            var m=LINE.matcher(line);if(!mine||!simmc||!m.matches())continue;
            var time=LocalTime.parse(m.group(1));if(lastSecond>=0&&time.toSecondOfDay()<lastSecond-3600)date=date.plusDays(1);lastSecond=time.toSecondOfDay();
            String raw=LedgerParser.clean(m.group(2));if(!wanted.contains(raw))continue;
            long at=LocalDateTime.of(date,time).atZone(clock).toInstant().toEpochMilli();
            var parsed=LedgerOfficial.parse(m.group(2),at,Map.of());if(parsed.isEmpty())continue;
            var e=parsed.get().withAccount(account,name).withRaw(raw);
            String id="recovered:"+UUID.nameUUIDFromBytes((file+":"+index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(existing.stream().anyMatch(old->old.id().equals(id)||old.account().equals(account)&&Math.abs(old.time()-at)<1000&&old.raw().equals(raw)))continue;
            if(result.size()>=20000)break;
            result.add(new LedgerEntry(id,e.time(),e.kind(),e.currency(),e.amount(),e.counterparty(),e.item(),e.quantity(),null,null,null,"","","",account,name,raw));
        }
        return result;
    }
}
