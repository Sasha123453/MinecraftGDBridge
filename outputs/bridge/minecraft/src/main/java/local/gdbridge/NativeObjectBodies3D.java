package local.gdbridge;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;

/** Pixel models sized and positioned by native GD; these models never alter physics. */
public final class NativeObjectBodies3D {
    private static final int FULL_LIGHT=0xF000F0;
    private NativeObjectBodies3D(){}
    public static boolean renderable(GDBridge.Obj o){return (o.visualColor()>=0||reference())&&Double.isFinite(o.bodyWidth())&&Double.isFinite(o.bodyHeight())&&o.bodyWidth()>0&&o.bodyHeight()>0&&o.bodyWidth()<=360&&o.bodyHeight()<=360;}
    public static boolean bodyOnly(GDBridge.Obj o){return renderable(o)&&(o.type().equals("portal")||o.type().equals("orb"));}
    private static boolean reference(){return WorldEditor.worldName.equals("GDBridge-Reference");}
    private static boolean isPad(int id){return id==35||id==67||id==140||id==1332||id==3004;}
    public static void render(MatrixStack m,VertexConsumerProvider buffers,GDBridge.Obj o,double ox,double oy,double oz,int light){
        if(!renderable(o)||!o.visualEnabled())return;
        boolean showcase=reference(),portal=o.type().equals("portal"),pad=isPad(o.objectId());
        int color=showcase&&portal&&o.objectId()==12?0x42E6F8:o.visualColor();
        if(color<0)color=0xFFD63D;
        double width=o.bodyWidth()/30,height=o.bodyHeight()/30;
        m.push();m.translate(o.bodyX()/30-ox,64+o.bodyY()/30-oy,.71-oz);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)-o.bodyRotation()));
        // A separate texture layer can flush Immediate's shared builder. Draw
        // the entire interior first, atlas geometry second, and additive last.
        if(portal)pixelDisc(m,PixelSpecialMaterials.interior(buffers),width*.405,height*.405,.245,0x185F69,light,24,portalRows(width,height));
        VertexConsumer white=PixelSpecialMaterials.block(buffers,"white_concrete");
        VertexConsumer stone=PixelSpecialMaterials.block(buffers,"stone_bricks");
        VertexConsumer obsidian=PixelSpecialMaterials.block(buffers,"obsidian");
        VertexConsumer gold=PixelSpecialMaterials.block(buffers,"gold_block");
        if(portal){
            int rows=portalRows(width,height);
            pixelRing(m,obsidian,obsidian,width*.48,height*.48,.76,-.27,.285,0xFFFFFF,0xFFFFFF,light,24,rows);
            pixelRing(m,white,obsidian,width*.465,height*.465,.83,-.12,.302,color,0xFFFFFF,light,24,rows);
            // Only the narrow pixel edge is emissive; obsidian and the broad
            // colored shoulder still receive real Minecraft lighting.
            pixelRing(m,white,white,width*.457,height*.457,.91,.292,.304,PixelSpecialMaterials.whiteMix(color,.66),color,FULL_LIGHT,24,rows);
        }else if(pad){
            pad(m,white,stone,gold,width,height,color,light);
        }else{
            boolean yellow=isYellow(color);
            VertexConsumer metal=yellow?gold:white;int metalColor=yellow?0xFFFFFF:color;
            pixelRing(m,metal,metal,width*.46,height*.46,.79,.18,.27,metalColor,metalColor,light,24,24);
            pixelRing(m,white,metal,width*.405,height*.405,.91,.264,.284,PixelSpecialMaterials.whiteMix(color,.65),metalColor,light,24,24);
            pixelCore(m,white,width*.315,height*.315,color,light);
        }
        if(showcase||o.nativeAdditive()){
            VertexConsumer glow=PixelSpecialMaterials.glow(buffers);
            if(portal){
                pixelRingFace(m,glow,width*.448,height*.448,.91,.308,PixelSpecialMaterials.tint(color,.55),24,portalRows(width,height));
                if(showcase)motes(m,glow,width,height,color);
            }else if(pad){
                double rx=width*.36,y=padTop(height);
                quad(m,glow,p(-rx,y,-.19),p(-rx,y,.297),p(rx,y,.297),p(rx,y,-.19),PixelSpecialMaterials.tint(color,.36),FULL_LIGHT);
                rect(m,glow,-rx,y-height*.17,rx,y,.301,PixelSpecialMaterials.tint(color,.34),FULL_LIGHT);
            }else{
                pixelRingFace(m,glow,width*.438,height*.438,.92,.291,PixelSpecialMaterials.tint(color,.27),24,24);
                pixelDisc(m,glow,width*.255,height*.255,.307,PixelSpecialMaterials.tint(color,.22),FULL_LIGHT,16,16);
            }
        }
        m.pop();
    }
    private static boolean isYellow(int c){return ((c>>16)&255)>180&&((c>>8)&255)>130&&(c&255)<160;}
    private static int portalRows(double w,double h){return Math.max(24,Math.min(80,(int)Math.round(24*h/w)));}
    /** Horizontal voxel runs make a staircase ellipse with real side faces. */
    private static void pixelRing(MatrixStack m,VertexConsumer face,VertexConsumer side,double rx,double ry,double inner,double back,double front,int color,int sideColor,int light,int columns,int rows){
        for(int row=0;row<rows;row++){
            int start=-1;
            for(int col=0;col<=columns;col++){
                boolean on=col<columns&&ringCell(col,row,columns,rows,inner);
                if(on&&start<0)start=col;
                if(!on&&start>=0){box(m,face,side,-rx+2*rx*start/columns,-ry+2*ry*row/rows,back,-rx+2*rx*col/columns,-ry+2*ry*(row+1)/rows,front,color,sideColor,light);start=-1;}
            }
        }
    }
    private static boolean ringCell(int x,int y,int columns,int rows,double inner){double u=(x+.5)*2/columns-1,v=(y+.5)*2/rows-1,d=u*u+v*v;return d<=1&&d>=inner*inner;}
    private static void pixelRingFace(MatrixStack m,VertexConsumer v,double rx,double ry,double inner,double z,int color,int columns,int rows){
        for(int row=0;row<rows;row++){int start=-1;for(int col=0;col<=columns;col++){boolean on=col<columns&&ringCell(col,row,columns,rows,inner);if(on&&start<0)start=col;if(!on&&start>=0){rect(m,v,-rx+2*rx*start/columns,-ry+2*ry*row/rows,-rx+2*rx*col/columns,-ry+2*ry*(row+1)/rows,z,color,FULL_LIGHT);start=-1;}}}
    }
    private static void pixelDisc(MatrixStack m,VertexConsumer v,double rx,double ry,double z,int color,int light,int columns,int rows){
        for(int row=0;row<rows;row++){
            double y=(row+.5)*2/rows-1;int half=(int)Math.floor(Math.sqrt(Math.max(0,1-y*y))*columns*.5);if(half==0)continue;
            double x0=-2*rx*half/columns,x1=-x0,y0=-ry+2*ry*row/rows,y1=-ry+2*ry*(row+1)/rows;
            quad(m,v,p(x0,y0,z),p(x1,y0,z),p(x1,y1,z),p(x0,y1,z),color,light,new float[][]{{(float)(.5+x0/(2*rx)),(float)(.5+y0/(2*ry))},{(float)(.5+x1/(2*rx)),(float)(.5+y0/(2*ry))},{(float)(.5+x1/(2*rx)),(float)(.5+y1/(2*ry))},{(float)(.5+x0/(2*rx)),(float)(.5+y1/(2*ry))}});
        }
    }
    private static void pixelCore(MatrixStack m,VertexConsumer v,double rx,double ry,int color,int light){
        for(int row=0;row<16;row++){
            double y=(row+.5)/8-1;int half=(int)Math.floor(Math.sqrt(Math.max(0,1-y*y))*8);if(half==0)continue;
            double x=rx*half/8,z=.26+.04*Math.sqrt(Math.max(0,1-y*y));int shade=PixelSpecialMaterials.whiteMix(color,.12+.10*(1-y));
            box(m,v,v,-x,-ry+2*ry*row/16,.19,x,-ry+2*ry*(row+1)/16,z,shade,PixelSpecialMaterials.tint(color,.72),light);
        }
    }
    private static void pad(MatrixStack m,VertexConsumer face,VertexConsumer stone,VertexConsumer gold,double width,double height,int color,int light){
        // Four-GD-unit pads still need a visible stone plinth. Its bottom stays
        // anchored to the native visual footprint; this is decorative geometry.
        double rx=width*.47,bottom=-height*.5,shoulder=padShoulder(height),top=padTop(height);
        box(m,stone,stone,-rx,bottom,-.30,rx,shoulder,.29,0xFFFFFF,0xFFFFFF,light);
        VertexConsumer edge=isYellow(color)?gold:face;int edgeColor=isYellow(color)?0xFFFFFF:PixelSpecialMaterials.tint(color,.78);
        box(m,edge,edge,-rx*.91,shoulder,-.25,rx*.91,shoulder+(top-shoulder)*.68,.298,edgeColor,edgeColor,light);
        box(m,face,edge,-rx*.76,shoulder+(top-shoulder)*.40,-.19,rx*.76,top,.30,PixelSpecialMaterials.whiteMix(color,.25),edgeColor,light);
    }
    private static double padShoulder(double height){return -height*.5+Math.max(.18,height*.58);}
    private static double padTop(double height){return padShoulder(height)+Math.max(.05,height*.28);}
    private static void motes(MatrixStack m,VertexConsumer v,double width,double height,int color){
        double[][] dots={{-.58,.23},{.58,-.13},{-.52,-.35},{.12,.56},{.55,.32}};double size=Math.min(width,height)*.035;
        for(double[] dot:dots)rect(m,v,width*dot[0]-size*.5,height*dot[1]-size*.5,width*dot[0]+size*.5,height*dot[1]+size*.5,.292,PixelSpecialMaterials.tint(color,.40),FULL_LIGHT);
    }
    private static double[] p(double x,double y,double z){return new double[]{x,y,z};}
    private static void rect(MatrixStack m,VertexConsumer v,double x0,double y0,double x1,double y1,double z,int color,int light){quad(m,v,p(x0,y0,z),p(x1,y0,z),p(x1,y1,z),p(x0,y1,z),color,light);}
    private static void box(MatrixStack m,VertexConsumer face,VertexConsumer side,double x0,double y0,double z0,double x1,double y1,double z1,int color,int sideColor,int light){
        rect(m,face,x0,y0,x1,y1,z1,color,light);
        quad(m,side,p(x1,y0,z0),p(x0,y0,z0),p(x0,y1,z0),p(x1,y1,z0),sideColor,light);
        quad(m,side,p(x0,y0,z0),p(x0,y0,z1),p(x0,y1,z1),p(x0,y1,z0),sideColor,light);
        quad(m,side,p(x1,y0,z1),p(x1,y0,z0),p(x1,y1,z0),p(x1,y1,z1),sideColor,light);
        quad(m,side,p(x0,y1,z0),p(x0,y1,z1),p(x1,y1,z1),p(x1,y1,z0),sideColor,light);
        quad(m,side,p(x0,y0,z1),p(x0,y0,z0),p(x1,y0,z0),p(x1,y0,z1),sideColor,light);
    }
    private static void quad(MatrixStack m,VertexConsumer v,double[] a,double[] b,double[] c,double[] d,int color,int light){
        double horizontal=Math.min(1,Math.sqrt(Math.pow(b[0]-a[0],2)+Math.pow(b[1]-a[1],2)+Math.pow(b[2]-a[2],2))),vertical=Math.min(1,Math.sqrt(Math.pow(d[0]-a[0],2)+Math.pow(d[1]-a[1],2)+Math.pow(d[2]-a[2],2)));
        quad(m,v,a,b,c,d,color,light,new float[][]{{0,(float)vertical},{(float)horizontal,(float)vertical},{(float)horizontal,0},{0,0}});
    }
    private static void quad(MatrixStack m,VertexConsumer consumer,double[] a,double[] b,double[] c,double[] d,int color,int light,float[][] uv){
        Vector3f normal=new Vector3f((float)(b[0]-a[0]),(float)(b[1]-a[1]),(float)(b[2]-a[2])).cross(new Vector3f((float)(c[0]-a[0]),(float)(c[1]-a[1]),(float)(c[2]-a[2])));
        if(normal.lengthSquared()<1e-10f)normal.set(0,0,1);else normal.normalize();
        double[][] vertices={a,b,c,d};
        for(int i=0;i<4;i++){
            // Keep calls on the sprite wrapper: chained calls bypass UV mapping.
            consumer.vertex(m.peek().getPositionMatrix(),(float)vertices[i][0],(float)vertices[i][1],(float)vertices[i][2]);
            consumer.color((color>>16)&255,(color>>8)&255,color&255,255);consumer.texture(uv[i][0],uv[i][1]);
            consumer.overlay(OverlayTexture.DEFAULT_UV);consumer.light(light);consumer.normal(m.peek().getNormalMatrix(),normal.x,normal.y,normal.z);consumer.next();
        }
    }
}
