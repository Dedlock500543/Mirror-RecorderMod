package com.mirror.recorder.handler;
import com.mirror.recorder.config.RecorderConfig;import com.mirror.recorder.manager.RecorderManager;import com.mirror.recorder.model.Frame;import net.minecraft.client.Minecraft;import net.minecraft.client.entity.EntityPlayerSP;import net.minecraft.client.renderer.*;import net.minecraft.client.renderer.vertex.DefaultVertexFormats;import net.minecraftforge.client.event.RenderWorldLastEvent;import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;import net.minecraftforge.fml.relauncher.*;import org.lwjgl.opengl.GL11;
@SideOnly(Side.CLIENT) public class PathLineRenderer{
    private static final int MAX_POINTS=300;private final Minecraft mc=Minecraft.getMinecraft();private final RecorderManager manager;private final RecorderConfig config;private final PlaybackTrajectory trajectory;private long cachedRun=-1L;private int cachedIndex=-1,cachedCount=0;private final Frame[] cachedFrames=new Frame[MAX_POINTS];
    public PathLineRenderer(RecorderManager m,RecorderConfig c,PlaybackTrajectory t){manager=m;config=c;trajectory=t;}
    @SubscribeEvent public void onRenderWorldLast(RenderWorldLastEvent e){
        if(!manager.isPlaying()){cachedRun=-1L;cachedIndex=-1;cachedCount=0;return;}if(!config.isPathLineEnabled()||!trajectory.isReady())return;EntityPlayerSP p=mc.player;if(p==null)return;long run=manager.getPlaybackRunId();int index=manager.getCurrentFrameIndex();if(cachedRun!=run||cachedIndex!=index){cachedCount=manager.copyPlaybackFramesAhead(cachedFrames);cachedRun=run;cachedIndex=index;}if(cachedCount<2)return;
        net.minecraft.client.renderer.entity.RenderManager rm=mc.getRenderManager();double cx=rm.viewerPosX,cy=rm.viewerPosY,cz=rm.viewerPosZ;float r=config.getPathLineR()/255f,g=config.getPathLineG()/255f,b=config.getPathLineB()/255f,a=config.getPathLineAlpha()/255f;
        long now=System.currentTimeMillis();float breath=0.84f+0.16f*wave(now,1600L);float travel=wrap01(now/1800f);
        GlStateManager.pushMatrix();GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.disableLighting();GlStateManager.enableDepth();if(config.isPathLineThroughWalls())GlStateManager.disableDepth();GlStateManager.depthMask(false);
        int count=Math.min(cachedCount,Math.max(2,config.getPathLineLength()));int style=config.getPathLineStyle(),step=Math.max(1,config.getPathPointStep());double lift=config.getPathLineHeight(),ps=config.getPathPointSize();boolean fade=config.isPathLineFade();
        Tessellator tess=Tessellator.getInstance();BufferBuilder buf=tess.getBuffer();float width=Math.max(1.2f,config.getPathLineWidth());
        if(style!=2){
            additive();GL11.glLineWidth(width+3.6f);strokePath(buf,tess,count,cx,cy,cz,lift,r,g,b,a*0.16f*breath,fade,travel,0.10f);
            normalBlend();GL11.glLineWidth(width+1.4f);strokePath(buf,tess,count,cx,cy,cz,lift,r,g,b,a*0.28f*breath,fade,travel,0.16f);
            GL11.glLineWidth(width);strokePath(buf,tess,count,cx,cy,cz,lift,Math.min(1f,r+0.18f),Math.min(1f,g+0.18f),Math.min(1f,b+0.18f),a*breath,fade,travel,0.42f);
        }
        if(style!=0){
            additive();GL11.glLineWidth(Math.max(2.2f,width+1.2f));strokeMarks(buf,tess,count,step,cx,cy,cz,lift,ps*1.55d,r,g,b,a*0.20f*breath,fade,travel);
            normalBlend();GL11.glLineWidth(Math.max(1.2f,width));strokeMarks(buf,tess,count,step,cx,cy,cz,lift,ps,r,g,b,a*breath,fade,travel);
        }
        GL11.glLineWidth(1f);GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.disableLighting();GlStateManager.enableAlpha();GlStateManager.enableCull();GlStateManager.disableBlend();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }
    private void strokePath(BufferBuilder buf,Tessellator tess,int count,double cx,double cy,double cz,double lift,float r,float g,float b,float a,boolean fade,float travel,float boost){
        buf.begin(GL11.GL_LINE_STRIP,DefaultVertexFormats.POSITION_COLOR);
        buf.pos(0d,lift,0d).color(r,g,b,Math.min(1f,a+boost)).endVertex();
        for(int i=0;i<count;i++){
            Frame f=cachedFrames[i];float av=shade(a,i,count,fade,travel,boost);
            buf.pos(trajectory.expectedX(f)-cx,trajectory.expectedY(f)-cy+lift,trajectory.expectedZ(f)-cz).color(r,g,b,av).endVertex();
        }
        tess.draw();
    }
    private void strokeMarks(BufferBuilder buf,Tessellator tess,int count,int step,double cx,double cy,double cz,double lift,double ps,float r,float g,float b,float a,boolean fade,float travel){
        buf.begin(GL11.GL_LINES,DefaultVertexFormats.POSITION_COLOR);
        for(int i=0;i<count;i+=step){
            Frame f=cachedFrames[i];double px=trajectory.expectedX(f)-cx,py=trajectory.expectedY(f)-cy+lift,pz=trajectory.expectedZ(f)-cz;float av=shade(a,i,count,fade,travel,0.35f);
            buf.pos(px-ps,py,pz).color(r,g,b,av).endVertex();buf.pos(px+ps,py,pz).color(r,g,b,av).endVertex();
            buf.pos(px,py-ps,pz).color(r,g,b,av).endVertex();buf.pos(px,py+ps,pz).color(r,g,b,av).endVertex();
            buf.pos(px,py,pz-ps).color(r,g,b,av).endVertex();buf.pos(px,py,pz+ps).color(r,g,b,av).endVertex();
        }
        tess.draw();
    }
    private static float shade(float a,int i,int count,boolean fade,float travel,float boost){
        float av=fade?a*(1f-0.82f*i/(float)Math.max(1,count-1)):a;
        float u=i/(float)Math.max(1,count-1),d=Math.abs(u-travel);if(d>0.5f)d=1f-d;
        float hit=Math.max(0f,1f-d*7.5f);return Math.min(1f,av+hit*boost);
    }
    private static float wave(long now,long period){return (float)(0.5-0.5*Math.cos((now%period)/(double)period*Math.PI*2.0));}
    private static float wrap01(float v){return v-(float)Math.floor(v);}
    private static void additive(){GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);}
    private static void normalBlend(){GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);}
}
