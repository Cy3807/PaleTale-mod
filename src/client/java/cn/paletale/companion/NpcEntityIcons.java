package cn.paletale.companion;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector2f;

/** Non-player NPCs use the game's entity renderer and active pack, never a player-head placeholder. */
final class NpcEntityIcons {
    private static Object level;
    private static final Map<String,LivingEntity> previews = new HashMap<>();
    private NpcEntityIcons() {}
    static void clear() { previews.clear();level=null; }
    static LivingEntity preview(CityCatalog.Place place, Entity actual) {
        var client=Minecraft.getInstance();
        if(client.level==null||place.entityType()==null||place.entityType().equals("minecraft:player"))return null;
        if(actual instanceof LivingEntity living&&actual.level()==client.level&&actual.isAlive())return living;
        if(level!=client.level){level=client.level;previews.clear();}
        return previews.computeIfAbsent(place.entityType(),type->{
            var entry=BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(type));
            if(entry.isEmpty())return null;
            var entity=entry.get().create(client.level,EntitySpawnReason.LOAD);
            return entity instanceof LivingEntity living?living:null;
        });
    }
    static boolean draw(GuiGraphics graphics, CityCatalog.Place place, Entity actual, int x, int y, int size) {
        var entity=preview(place,actual);if(entity==null)return false;
        // The 1.21.11 entity PIP API takes absolute GUI coordinates and does not apply the window pose itself.
        var a=graphics.pose().transformPosition(new Vector2f(x,y));
        var b=graphics.pose().transformPosition(new Vector2f(x+size,y+size));
        int left=(int)Math.floor(a.x),top=(int)Math.floor(a.y),right=(int)Math.ceil(b.x),bottom=(int)Math.ceil(b.y);
        float height=entity.getBbHeight()/entity.getScale();
        int scale=Math.max(1,(int)((bottom-top)*.82f/Math.max(.4f,height)));
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,left,top,right,bottom,scale,0,(left+right)/2f-7,(top+bottom)/2f,entity);
        return true;
    }
}
