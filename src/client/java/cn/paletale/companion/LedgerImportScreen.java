package cn.paletale.companion;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.time.LocalDate;
import static cn.paletale.companion.MarketTheme.*;

/** Explicit date selection; disk scanning and backup never run on the render thread. */
final class LedgerImportScreen extends WindowScreen {
    private final Screen parent;private EditBox from,to;private String fromText=LocalDate.now(LedgerStore.BEIJING).minusDays(7).toString(),toText=LocalDate.now(LedgerStore.BEIJING).toString();
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();private volatile boolean running;private volatile LedgerLogImport.Result preview;private volatile String previewAccount="",previewFrom="",previewTo="";private volatile String status="选择北京时间日期范围，然后导入本机日志。";
    LedgerImportScreen(Screen parent){super(Component.literal("导入账本"));this.parent=parent;}
    @Override protected void init(){fitWindow();var layout=ArchiveLayout.of(width);int x=layout.contentX(),y=85,w=Math.min(200,layout.contentWidth()-12);addRenderableWidget(new Action("← 返回",layout.left(),height-36,layout.sidebar(),18,false,null,b->onClose()));
        from=addRenderableWidget(new EditBox(font,x,y,w,16,Component.literal("开始日期")));from.setBordered(false);from.setTextShadow(false);from.setTextColor(INK);from.setValue(fromText);from.setMaxLength(10);from.setResponder(v->fromText=v);
        to=addRenderableWidget(new EditBox(font,x,y+40,w,16,Component.literal("结束日期")));to.setBordered(false);to.setTextShadow(false);to.setTextColor(INK);to.setValue(toText);to.setMaxLength(10);to.setResponder(v->toText=v);
        var button=addRenderableWidget(new Action("预览",x,y+72,80,18,false,null,b->start()));button.active=!running;var confirm=addRenderableWidget(new Action("确认导入",x+86,y+72,90,18,false,null,b->commit()));confirm.active=!running&&preview!=null&&fromText.equals(previewFrom)&&toText.equals(previewTo);
    }
    private void start(){LocalDate a,b;try{a=LocalDate.parse(fromText);b=LocalDate.parse(toText);if(a.isAfter(b)||b.isAfter(LocalDate.now(LedgerStore.BEIJING)))throw new IllegalArgumentException();}catch(RuntimeException invalid){status="请输入有效日期（年-月-日），结束日期不能早于开始日期或晚于今天。";return;}
        if(running)return;String account=LedgerStore.account(),name=LedgerStore.accountName();if(account.isEmpty()||name.isEmpty()){status="无法确认当前游戏账号。";return;}if(!BUSY.compareAndSet(false,true)){status="已有导入任务正在运行。";return;}running=true;status="正在后台读取日志…";rebuildWidgets();
        String selectedFrom=fromText,selectedTo=toText;preview=null;Thread.ofVirtual().name("paletale-ledger-preview").start(()->{try{
            var result=LedgerLogImport.read(minecraft.gameDirectory.toPath().resolve("logs"),account,name,a,b,java.time.ZoneId.systemDefault());
            if(!LedgerStore.account().equals(account))throw new IllegalStateException("账号已切换");
            int eligible=LedgerImportMerge.additions(LedgerStore.all(),result.entries(),account,LedgerStore.LIMIT).size();preview=result;previewAccount=account;previewFrom=selectedFrom;previewTo=selectedTo;
            status="识别 "+result.entries().size()+" 条，预计导入 "+eligible+" 条；重复或容量限制 "+(result.entries().size()-eligible)+" 条；扫描 "+result.files()+" 个日志，跳过 "+result.skipped()+" 个。";
        }catch(Exception failure){status="预览未完成，请检查日志后重试。";}finally{running=false;BUSY.set(false);refreshUi();}});
    }
    private void commit(){var result=preview;String account=previewAccount;if(result==null||running||!fromText.equals(previewFrom)||!toText.equals(previewTo)||!LedgerStore.account().equals(account)){status="日期或账号已变更，请重新预览。";return;}if(!BUSY.compareAndSet(false,true)){status="已有导入任务正在运行。";return;}running=true;status="正在后台导入…";rebuildWidgets();
        Thread.ofVirtual().name("paletale-ledger-import").start(()->{try{
            if(!LedgerStore.account().equals(account))throw new IllegalStateException("账号已切换");int count=0;if(!LedgerImportMerge.additions(LedgerStore.all(),result.entries(),account,LedgerStore.LIMIT).isEmpty()){
                if(!LedgerStore.flushImport())throw new java.io.IOException("无法保存当前账本");var source=LedgerStore.folder().resolve("ledger.json");if(java.nio.file.Files.isRegularFile(source)){var dir=LedgerStore.folder().resolve("backups");java.nio.file.Files.createDirectories(dir);String version=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("paletale").orElseThrow().getMetadata().getVersion().getFriendlyString().replaceAll("[^A-Za-z0-9._-]","_");java.nio.file.Files.copy(source,dir.resolve("ledger-before-import-"+version+"-"+System.currentTimeMillis()+".json"));}
                count=LedgerStore.importBatch(account,result.entries());if(count>0&&!LedgerStore.flushImport()){status="已加入内存，但保存失败；请检查文件权限后重试。";return;}
            }preview=null;status="导入 "+count+" / 识别 "+result.entries().size()+" 条；扫描 "+result.files()+" 个日志"+(result.skipped()>0?"，跳过 "+result.skipped()+" 个":"")+"。";
        }catch(Exception failure){status="导入未完成，请检查日志和账本文件后重试。";}finally{running=false;BUSY.set(false);refreshUi();}});
    }
    private void refreshUi(){minecraft.execute(()->{if(minecraft.screen==this)rebuildWidgets();});}
    @Override protected void renderWindow(GuiGraphics g,int x,int y,float delta){var l=ArchiveLayout.of(width);l.draw(g,height);super.renderWindow(g,x,y,delta);text(g,"账本导入",l.left()+3,48,l.sidebar()-6,INK);g.renderOutline(from.getX()-3,from.getY()-3,from.getWidth()+6,22,LINE);g.renderOutline(to.getX()-3,to.getY()-3,to.getWidth()+6,22,LINE);int cx=l.contentX(),cw=l.contentWidth();text(g,"从本地日志导入账本",cx,42,cw,INK);text(g,"开始日期（北京时间）",cx,70,cw,MUTED);text(g,"结束日期（含当天）",cx,110,cw,MUTED);int line=190;for(var wrapped:font.split(Component.literal(status),cw)){g.drawString(font,wrapped,cx,line,INK,false);line+=12;}text(g,"仅导入当前账号的SIMMC日志；确认前不写入。",cx,line+10,cw,MUTED);text(g,"相邻一秒内相同记录可能按重复处理。",cx,line+24,cw,MUTED);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
