package cn.paletale.companion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import static cn.paletale.companion.MarketTheme.*;
final class ChatSettingsScreen extends WindowScreen {
    private final Screen parent;
    ChatSettingsScreen(Screen parent){super(Component.literal("聊天辅助"));this.parent=parent;}
    @Override protected void init(){
        fitWindow();addRenderableWidget(new Action("返回",width-80,12,64,18,false,null,b->onClose()));
        String[] keys={"chatCoordinates","chatPlaces","chatPlayers","chatLinks","chatItems","chatMenu","chatMentions","chatDoNotDisturb"};
        String[] labels={"坐标快捷导航","传送点 / 领地关联","玩家页面关联","聊天链接","物品档案关联","右键交互菜单","@提及高亮","免打扰"};
        String[] help={"识别 X Z 或 X Y Z，点击导航到主世界坐标。","点击名称查看 SMC 页面；右键可导航。","点击玩家名查看 SMC 玩家页，不添加下划线。","打开完整 HTTP / HTTPS 链接，支持显示折行。","点击公开物品档案名称打开详情。","复制完整消息；特殊词条可私聊、预填转账或查看地图。","别人在全服、交易、领地或国家聊天中提到你时，显示为绿色 @你的ID 并叮一声；私聊和系统消息不识别。","开启后 @提及 仍然高亮，但不再响铃。单个会话可在聊天列表右键设置免打扰。"};
        boolean[] flags={ModSettings.chatCoordinates(),ModSettings.chatPlaces(),ModSettings.chatPlayers(),ModSettings.chatLinks(),ModSettings.chatItems(),ModSettings.chatMenu(),ModSettings.chatMentions(),ModSettings.chatDoNotDisturb()};
        for(int i=0;i<keys.length;i++){String key=keys[i];boolean on=flags[i];var button=addRenderableWidget(new Action(labels[i]+"："+(on?"开":"关"),18,38+i*22,Math.min(260,width-36),20,on,null,b->{ModSettings.setChat(key,!on);rebuildWidgets();}));button.setTooltip(Tooltip.create(Component.literal(help[i])));}
        // What a plain click on a name does, per kind of name: cycles through the choices that make sense for it.
        int cx=18+Math.min(260,width-36)+16,cw=Math.min(190,width-cx-12);
        if(cw>=140){var kinds=new ChatTokens.Kind[]{ChatTokens.Kind.PLAYER,ChatTokens.Kind.LAND,ChatTokens.Kind.NATION,ChatTokens.Kind.TRAFFIC};String[] names={"点击玩家","点击领地","点击国家","点击传送点"};
            for(int i=0;i<kinds.length;i++){var kind=kinds[i];var choices=ChatAssist.choices(kind);String now=ChatAssist.defaultOperation(kind);
                var button=addRenderableWidget(new Action(names[i]+"："+ChatAssist.choiceLabel(now),cx,38+i*22,cw,20,false,null,b->{ModSettings.setChatClickAction(kind.name(),choices.get((choices.indexOf(now)+1)%choices.size()));rebuildWidgets();}));
                button.setTooltip(Tooltip.create(Component.literal("聊天里点击这类名称时的默认动作，点击依次切换。右键仍可打开完整菜单。")));}}
    }
    @Override protected void windowBackground(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,PAPER);}
    @Override protected void renderWindow(GuiGraphics g,int x,int y,float delta){super.renderWindow(g,x,y,delta);text(g,"聊天辅助",18,16,200,INK);text(g,"目录在后台同步；开关变更后对新消息生效。",18,222,width-36,MUTED);text(g,ChatDirectory.status+" · "+ChatDirectory.index.size()+" 项",18,236,width-36,MUTED);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
