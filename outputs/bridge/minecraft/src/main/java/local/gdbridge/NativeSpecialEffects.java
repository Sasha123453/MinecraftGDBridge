package local.gdbridge;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

/** Cosmetic pixel effects driven by native time and native special activation.
 * This does not change colliders, input, visibility or gameplay state. */
public final class NativeSpecialEffects {
    private static final int FULL_LIGHT=0xF000F0, MAX_SPECIALS=48, MAX_BURSTS=32;
    private record Burst(int id,double x,double y,double rotation,double width,double height,int color,boolean pad,double started) {}
    private static final Map<Integer,Boolean> ACTIVATED=new HashMap<>();
    private static final ArrayDeque<Burst> BURSTS=new ArrayDeque<>();
    private static long lastSeq=Long.MIN_VALUE;
    private static int lastLevel=Integer.MIN_VALUE,lastAttempt=Integer.MIN_VALUE;
    private static double nativeTime=Double.NaN, animationTime, lastX;
    private static boolean lastPaused=true,lastDead;
    private NativeSpecialEffects() {}

    /** Call once per world frame with the same frame used by the native models. */
    public static void render(MatrixStack matrices,VertexConsumerProvider buffers,GDBridge.Frame frame,double ox,double oy,double oz) {
        if(frame==null||frame.packet()==null||!BridgeVisualStyle.volume())return;
        update(frame);
        var mc=MinecraftClient.getInstance();if(mc.world==null)return;
        // Each material batch is completed before requesting a different layer:
        // Immediate may share and flush builders between layers.
        VertexConsumer glow=PixelSpecialMaterials.glow(buffers);
        int count=0;
        for(var object:frame.objects()) {
            if(count>=MAX_SPECIALS)break;
            if(!special(object)||!NativeObjectBodies3D.renderable(object)||!object.visualEnabled()||Math.abs(object.bodyX()-frame.x())>1050)continue;
            count++;
            matrices.push();transform(matrices,object,ox,oy,oz);
            double width=object.bodyWidth()/30,height=object.bodyHeight()/30,phase=animationTime+Math.floorMod(object.id(),31)*.19;
            int color=color(object);
            if(object.type().equals("portal")) {
                // Two narrow, translucent-looking additive bands. No opaque halo
                // card: depth testing retains Minecraft foreground occlusion.
                double pulse=1+.014*Math.sin(phase*2.4);
                ellipse(matrices,glow,width*.48*pulse,height*.48*pulse,.022,.307,color,.25);
                ellipse(matrices,glow,width*.51*pulse,height*.51*pulse,.026,.309,color,.055);
                for(int i=0;i<7;i++) {
                    double cycle=fract(phase*.22+i*.143),angle=i*2.399+phase*.18;
                    double radius=.50+cycle*.16,size=.024*(1-.45*cycle),gain=.30*Math.sin(Math.PI*cycle);
                    double x=Math.cos(angle)*width*radius,y=Math.sin(angle)*height*radius;
                    face(matrices,glow,x-size,y-size,x+size,y+size,.30+Math.sin(angle)*.025,color,gain,FULL_LIGHT);
                }
            } else if(pad(object.objectId())) {
                double y=-height*.5+Math.max(.18,height*.58)+Math.max(.05,height*.28);
                double pulse=.18+.06*Math.sin(phase*3);
                face(matrices,glow,-width*.34,y-.018,width*.34,y+.018,.31,color,pulse,FULL_LIGHT);
            } else {
                double pulse=1+.065*Math.sin(phase*3.1);
                ellipse(matrices,glow,width*.49*pulse,height*.49*pulse,.014,.316,color,.40);
                ellipse(matrices,glow,width*.535*pulse,height*.535*pulse,.018,.317,color,.065);
                // Orbiting pixel highlights make unactivated orbs visibly alive.
                for(int i=0;i<4;i++) {
                    double a=phase*1.3+i*Math.PI*.5,size=.020+.005*Math.sin(a*2);
                    double x=Math.cos(a)*width*.51,y=Math.sin(a)*height*.51;
                    face(matrices,glow,x-size,y-size,x+size,y+size,.318,color,.65,FULL_LIGHT);
                }
            }
            matrices.pop();
        }
        Map<Integer,GDBridge.Obj> visibleObjects=new HashMap<>();for(var object:frame.objects())visibleObjects.put(object.id(),object);
        for(Burst burst:BURSTS) {
            var source=visibleObjects.get(burst.id());if(source==null||!source.visualEnabled())continue;
            double age=animationTime-burst.started();if(age<0||age>.75)continue;
            matrices.push();matrices.translate(burst.x()/30-ox,64+burst.y()/30-oy,.71-oz);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-burst.rotation()));
            if(!burst.pad())ellipse(matrices,glow,burst.width()*(.43+age*.75),burst.height()*(.43+age*.75),.022*(1-age/.75),.33,burst.color(),.42*(1-age/.75));
            for(int i=0;i<9;i++) {
                double angle=burst.pad()?(.16+Math.PI*i/10):(i*Math.PI*2/9),speed=.60+Math.floorMod(i*17,7)*.045;
                double x=Math.cos(angle)*age*speed,y=Math.sin(angle)*age*speed-age*age*.25;
                double size=.032*(1-age/.75);
                face(matrices,glow,x-size,y-size,x+size,y+size,.34,burst.color(),.72*(1-age/.75),FULL_LIGHT);
            }
            matrices.pop();
        }
        // A few small lit, three-dimensional dust pixels add physical depth.
        VertexConsumer solid=PixelSpecialMaterials.block(buffers,"white_concrete");count=0;
        for(var object:frame.objects()) {
            if(count>=MAX_SPECIALS)break;
            if(!special(object)||!NativeObjectBodies3D.renderable(object)||!object.visualEnabled()||pad(object.objectId())||Math.abs(object.bodyX()-frame.x())>1050)continue;
            count++;matrices.push();transform(matrices,object,ox,oy,oz);
            double width=object.bodyWidth()/30,height=object.bodyHeight()/30,phase=animationTime+Math.floorMod(object.id(),31)*.19;
            int light=WorldRenderer.getLightmapCoordinates(mc.world,BlockPos.ofFloored(object.bodyX()/30,64+object.bodyY()/30,.8));
            for(int i=0;i<3;i++) {
                double a=phase*.65+i*2.094,cycle=fract(phase*.17+i*.333),size=.013*(.65+Math.sin(Math.PI*cycle)*.35);
                cube(matrices,solid,Math.cos(a)*width*.55,Math.sin(a)*height*.55,.27+Math.sin(a)*.09,size,color(object),light);
            }
            matrices.pop();
        }
    }

    private static void update(GDBridge.Frame frame) {
        if(frame.seq()==lastSeq)return;
        JsonObject packet=frame.packet();int level=integer(packet,"level",0),attempt=integer(packet,"attempts",0);
        double now=number(packet,"t",Double.NaN);
        boolean reset=level!=lastLevel||attempt!=lastAttempt||frame.x()<lastX-45||frame.dead()&&!lastDead||frame.seq()<lastSeq;
        if(reset){ACTIVATED.clear();BURSTS.clear();animationTime=0;}
        if(!reset&&!frame.paused()&&!lastPaused&&!frame.dead()&&Double.isFinite(now)&&Double.isFinite(nativeTime))animationTime+=Math.max(0,Math.min(.15,now-nativeTime));
        lastSeq=frame.seq();nativeTime=now;lastLevel=level;lastAttempt=attempt;lastX=frame.x();lastPaused=frame.paused();lastDead=frame.dead();
        while(!BURSTS.isEmpty()&&animationTime-BURSTS.peekFirst().started()>.75)BURSTS.removeFirst();
        if(frame.dead())return;
        JsonArray activations=array(packet,"objectActivations");if(activations==null)return;
        Map<Integer,GDBridge.Obj> objects=new HashMap<>();for(var o:frame.objects())objects.put(o.id(),o);
        for(JsonElement entry:activations) {
            if(!entry.isJsonObject())continue;JsonObject activation=entry.getAsJsonObject();int id=integer(activation,"id",-1);
            boolean active=bool(activation,"p1")||bool(activation,"p2");Boolean previous=ACTIVATED.put(id,active);
            if(!active||reset||Boolean.TRUE.equals(previous))continue;
            GDBridge.Obj object=objects.get(id);if(object==null||!object.visualEnabled()||!special(object))continue;
            if(BURSTS.size()>=MAX_BURSTS)BURSTS.removeFirst();
            BURSTS.addLast(new Burst(id,object.bodyX(),object.bodyY(),object.bodyRotation(),object.bodyWidth()/30,object.bodyHeight()/30,color(object),pad(object.objectId()),animationTime));
        }
        // Native caches can change with editor rebuilds; never grow indefinitely.
        if(ACTIVATED.size()>2048)ACTIVATED.keySet().retainAll(objects.keySet());
    }
    private static boolean special(GDBridge.Obj o){return o.type().equals("portal")||o.type().equals("orb");}
    private static boolean pad(int id){return id==35||id==67||id==140||id==1332||id==3004;}
    private static int color(GDBridge.Obj o){return WorldEditor.worldName.equals("GDBridge-Reference")&&o.type().equals("portal")&&o.objectId()==12?0x42E6F8:o.visualColor()<0?0xFFD63D:o.visualColor();}
    private static void transform(MatrixStack m,GDBridge.Obj o,double ox,double oy,double oz){m.translate(o.bodyX()/30-ox,64+o.bodyY()/30-oy,.71-oz);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-o.bodyRotation()));}
    private static double fract(double value){return value-Math.floor(value);}
    private static void ellipse(MatrixStack m,VertexConsumer v,double rx,double ry,double thickness,double z,int color,double gain) {
        for(int i=0;i<40;i++) {
            double a=i*Math.PI*2/40,b=(i+1)*Math.PI*2/40;
            quad(m,v,new double[]{Math.cos(a)*(rx-thickness),Math.sin(a)*(ry-thickness),z},new double[]{Math.cos(a)*rx,Math.sin(a)*ry,z},new double[]{Math.cos(b)*rx,Math.sin(b)*ry,z},new double[]{Math.cos(b)*(rx-thickness),Math.sin(b)*(ry-thickness),z},color,gain,FULL_LIGHT,0,0,1);
        }
    }
    private static void face(MatrixStack m,VertexConsumer v,double x0,double y0,double x1,double y1,double z,int c,double gain,int light){quad(m,v,new double[]{x0,y0,z},new double[]{x1,y0,z},new double[]{x1,y1,z},new double[]{x0,y1,z},c,gain,light,0,0,1);}
    private static void cube(MatrixStack m,VertexConsumer v,double x,double y,double z,double s,int c,int light) {
        face(m,v,x-s,y-s,x+s,y+s,z+s,c,1,light);
        quad(m,v,new double[]{x+s,y-s,z-s},new double[]{x-s,y-s,z-s},new double[]{x-s,y+s,z-s},new double[]{x+s,y+s,z-s},c,.65,light,0,0,-1);
        quad(m,v,new double[]{x-s,y-s,z-s},new double[]{x-s,y-s,z+s},new double[]{x-s,y+s,z+s},new double[]{x-s,y+s,z-s},c,.72,light,-1,0,0);
        quad(m,v,new double[]{x+s,y-s,z+s},new double[]{x+s,y-s,z-s},new double[]{x+s,y+s,z-s},new double[]{x+s,y+s,z+s},c,.80,light,1,0,0);
        quad(m,v,new double[]{x-s,y+s,z-s},new double[]{x-s,y+s,z+s},new double[]{x+s,y+s,z+s},new double[]{x+s,y+s,z-s},c,1,light,0,1,0);
        quad(m,v,new double[]{x-s,y-s,z+s},new double[]{x-s,y-s,z-s},new double[]{x+s,y-s,z-s},new double[]{x+s,y-s,z+s},c,.55,light,0,-1,0);
    }
    private static void quad(MatrixStack m,VertexConsumer consumer,double[] a,double[] b,double[] c,double[] d,int color,double gain,int light,float nx,float ny,float nz) {
        int shaded=PixelSpecialMaterials.tint(color,gain);double[][] vertices={a,b,c,d};float[][] uv={{0,1},{1,1},{1,0},{0,0}};
        for(int i=0;i<4;i++) {
            consumer.vertex(m.peek().getPositionMatrix(),(float)vertices[i][0],(float)vertices[i][1],(float)vertices[i][2]);
            consumer.color((shaded>>16)&255,(shaded>>8)&255,shaded&255,255);consumer.texture(uv[i][0],uv[i][1]);consumer.overlay(OverlayTexture.DEFAULT_UV);consumer.light(light);consumer.normal(m.peek().getNormalMatrix(),nx,ny,nz);consumer.next();
        }
    }
    private static JsonArray array(JsonObject o,String key){return o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):null;}
    private static int integer(JsonObject o,String key,int fallback){try{return o.has(key)?o.get(key).getAsInt():fallback;}catch(RuntimeException ex){return fallback;}}
    private static double number(JsonObject o,String key,double fallback){try{return o.has(key)?o.get(key).getAsDouble():fallback;}catch(RuntimeException ex){return fallback;}}
    private static boolean bool(JsonObject o,String key){try{return o.has(key)&&o.get(key).getAsBoolean();}catch(RuntimeException ex){return false;}}
}
