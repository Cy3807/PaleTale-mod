package cn.paletale.companion;

import com.mojang.blaze3d.platform.NativeImage;

/** Render a fixed Minecraft skin's face and hat locally; the result shares the website's 80px canvas. */
final class NpcPortrait {
    private NpcPortrait() {}
    static NativeImage fromSkin(byte[] bytes) throws java.io.IOException {
        if(bytes==null||bytes.length<24||bytes.length>128*1024||java.nio.ByteBuffer.wrap(bytes).getLong()!=0x89504e470d0a1a0aL)return null;
        var header=java.nio.ByteBuffer.wrap(bytes);int width=header.getInt(16),height=header.getInt(20);
        if(width<64||width>1024||width%64!=0||(height!=width&&height!=width/2))return null;
        try(var skin=NativeImage.read(bytes)) {
            int scale=width/64,edge=8*scale;boolean hat=height==width;
            for(int y=0;y<32*scale&&!hat;y++)for(int x=32*scale;x<width;x++)if((skin.getPixel(x,y)>>>24)<128){hat=true;break;}
            var out=new NativeImage(80,80,true);out.fillRect(0,0,80,80,0);
            for(int y=0;y<64;y++)for(int x=0;x<64;x++)out.setPixel(x+8,y+8,skin.getPixel(edge+x*edge/64,edge+y*edge/64)|0xff000000);
            if(hat)for(int y=0;y<72;y++)for(int x=0;x<72;x++)out.setPixel(x+4,y+4,over(skin.getPixel(40*scale+x*edge/72,edge+y*edge/72),out.getPixel(x+4,y+4)));
            return out;
        }
    }
    private static int over(int foreground,int background) {
        int a=foreground>>>24,b=background>>>24,alpha=a+b*(255-a)/255;if(alpha==0)return 0;
        int color=alpha<<24;
        for(int shift:new int[]{16,8,0})color|=((((foreground>>>shift)&255)*a+((background>>>shift)&255)*b*(255-a)/255)/alpha)<<shift;
        return color;
    }
}
