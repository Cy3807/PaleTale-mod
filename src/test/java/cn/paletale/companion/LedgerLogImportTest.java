package cn.paletale.companion;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
/** Synthetic logs only; never touches the player's ledger or live logs. */
final class LedgerLogImportTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{run();System.out.println("Ledger import merge checks passed");}
 static void run()throws Exception{
  Path dir=Files.createTempDirectory("paletale-ledger-import-");try{
   String log="[12:00:00] [Render thread/INFO]: Setting user: Alice\n[12:00:01] [Render thread/INFO]: Connecting to play.simmc.cn, 25565\n[12:01:00] [Render thread/INFO]: [CHAT] $10已被发送到Bob。\n[12:01:01] [Render thread/INFO]: [CHAT] [G] Fake : $50已被发送到Bob。\n[12:01:01] [Render thread/INFO]: [CHAT] [私聊] Fake : Alice收到了 $50。\n[12:01:02] [Render thread/INFO]: Connecting to other.example, 25565\n[12:01:03] [Render thread/INFO]: [CHAT] $99已被发送到Bob。\n";
   try(var gz=new GZIPOutputStream(Files.newOutputStream(dir.resolve("2026-10-25-1.log.gz")))){gz.write(log.getBytes(StandardCharsets.UTF_8));}
   var result=LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),ZoneId.of("Asia/Shanghai"));
   check(result.entries().size()==1&&result.entries().getFirst().amount()==10,"only SIMMC system-looking receipt imported");check(result.entries().getFirst().account().equals("account"),"account retained");
   var entry=result.entries().getFirst();check(LedgerImportMerge.additions(List.of(entry),result.entries(),"account",20000).isEmpty(),"repeat import skipped");
   var live=new LedgerEntry("live",entry.time()+500,entry.kind(),entry.currency(),entry.amount(),entry.counterparty(),entry.item(),entry.quantity(),null,null,null,"","","",entry.account(),entry.accountName(),entry.raw());
   check(LedgerImportMerge.additions(List.of(live),result.entries(),"account",20000).isEmpty(),"live entry skipped");
   var reclassified=new LedgerEntry("reclassified",entry.time()+500,LedgerEntry.Kind.OFFICIAL_OTHER,entry.currency(),entry.amount(),entry.counterparty(),entry.item(),entry.quantity(),null,null,null,"","","",entry.account(),entry.accountName(),entry.raw());
   check(LedgerImportMerge.additions(List.of(reclassified),result.entries(),"account",20000).isEmpty(),"identical raw receipt is deduplicated even when parser classification changes");

   var old=new LedgerEntry("legacy",entry.time()+500,entry.kind(),entry.currency(),entry.amount(),entry.counterparty(),entry.item(),entry.quantity(),null,null,null,"","","",entry.account(),entry.accountName(),"");
   check(LedgerImportMerge.additions(List.of(old),result.entries(),"account",20000).isEmpty(),"legacy entry without raw text also skipped when fields agree");

   check(LedgerImportMerge.additions(List.of(),result.entries(),"other",20000).isEmpty(),"other account cannot import");
   check(LedgerImportMerge.additions(List.of(entry),result.entries(),"account",1).isEmpty(),"capacity never evicts existing entries");
   var same1=new LedgerEntry("same1",entry.time()+100,entry.kind(),entry.currency(),entry.amount(),entry.counterparty(),entry.item(),entry.quantity(),null,null,null,"","","",entry.account(),entry.accountName(),entry.raw());
   var same2=new LedgerEntry("same2",entry.time()+200,entry.kind(),entry.currency(),entry.amount(),entry.counterparty(),entry.item(),entry.quantity(),null,null,null,"","","",entry.account(),entry.accountName(),entry.raw());
   check(LedgerImportMerge.additions(List.of(entry),List.of(same1,same2),"account",20000).size()==1,"one existing tip matches one of two identical tips");
   check(LedgerImportMerge.additions(List.of(entry,same2),List.of(same1,same2),"account",20000).isEmpty(),"two identical tips replay without additions");
   var repeat=LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),ZoneId.of("Asia/Shanghai"));check(repeat.entries().getFirst().id().equals(result.entries().getFirst().id()),"stable replay identity");
   check(LedgerLogImport.read(dir,"account","Bob",LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),ZoneId.of("Asia/Shanghai")).entries().isEmpty(),"other account excluded");
   check(LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-26"),LocalDate.parse("2026-10-26"),ZoneId.of("Asia/Shanghai")).entries().isEmpty(),"date range enforced");
   String alt="[13:00:00] [Render thread/INFO]: Setting user: Alice\n[13:00:01] [Render thread/INFO]: Connecting to simmc.silkysoftware.com, 25565\n[13:01:00] [Render thread/INFO]: [CHAT] $21已被发送到Bob。\n[13:02:00] [Render thread/INFO]: Connecting to simmc.silkysoftware.com.evil.example, 25565\n[13:03:00] [Render thread/INFO]: [CHAT] $22已被发送到Bob。\n";
   try(var gz=new GZIPOutputStream(Files.newOutputStream(dir.resolve("2026-10-27-1.log.gz")))){gz.write(alt.getBytes(StandardCharsets.UTF_8));}
   var alternate=LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-27"),LocalDate.parse("2026-10-27"),ZoneId.of("Asia/Shanghai"));check(alternate.entries().size()==1&&alternate.entries().getFirst().amount()==21,"official alternative accepted, lookalike rejected");
   String midnight="[23:59:00] [Render thread/INFO]: Setting user: Alice\n[23:59:01] [Render thread/INFO]: Connecting to play.simmc.cn, 25565\n[23:59:10] [Render thread/INFO]: [CHAT] 商店信息\n[23:59:11] [Render thread/INFO]: [CHAT] 所有者：Bob\n[00:00:01] [Render thread/INFO]: loading world\n[00:01:00] [Render thread/INFO]: [CHAT] $3已被发送到Bob。\n[00:01:05] [Render thread/INFO]: [CHAT] 支付成功：1 小麦 共 $3\n";
   try(var gz=new GZIPOutputStream(Files.newOutputStream(dir.resolve("2026-10-25-3.log.gz")))){gz.write(midnight.getBytes(StandardCharsets.UTF_8));}
   var nextDay=LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-26"),LocalDate.parse("2026-10-26"),ZoneId.of("Asia/Shanghai"));check(nextDay.entries().size()==2&&nextDay.entries().getFirst().amount()==3&&nextDay.entries().getLast().counterparty().equals("Bob"),"midnight rollover and prior shop context retained");
   try(var gz=new GZIPOutputStream(Files.newOutputStream(dir.resolve("2026-10-25-2.log.gz")))){byte[] block=new byte[8192];Arrays.fill(block,(byte)'x');for(int i=0;i<2100;i++)gz.write(block);}
   LedgerLogImport.maxExpanded=16L*1024*1024;var bounded=LedgerLogImport.read(dir,"account","Alice",LocalDate.parse("2026-10-25"),LocalDate.parse("2026-10-25"),ZoneId.of("Asia/Shanghai"));check(bounded.skipped()>=1&&bounded.entries().size()==1,"oversized compressed log is skipped without losing other files");LedgerLogImport.maxExpanded=512L*1024*1024;

  }finally{try(var files=Files.walk(dir)){for(Path path:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
