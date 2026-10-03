package local.gdbridge;
import com.google.gson.*;
import net.minecraft.util.math.BlockPos;

/** The same bounded slice drives import, editing, native compilation and rendering. */
public record GameplayRegion(int minX,int maxX,int minY,int maxY) {
    public static final GameplayRegion DEFAULT=new GameplayRegion(0,512,67,100);
    public GameplayRegion {if(minX< -16||maxX>4095||minX>maxX||minY<50||maxY>255||minY>maxY)throw new IllegalArgumentException("Authoring region outside safe Minecraft slice");}
    public boolean contains(BlockPos pos){return pos.getZ()==0&&pos.getX()>=minX&&pos.getX()<=maxX&&pos.getY()>=minY&&pos.getY()<=maxY;}
    public boolean containsGD(double x,double y){return x>=minX*30.0&&x<(maxX+1)*30.0&&y>=(minY-64)*30.0&&y<(maxY+1-64)*30.0;}
    public long cells(){return (maxX-minX+1L)*(maxY-minY+1L);}
    public JsonObject json(){JsonObject value=new JsonObject();value.addProperty("minX",minX);value.addProperty("maxX",maxX);value.addProperty("minY",minY);value.addProperty("maxY",maxY);return value;}
    public String label(){return "z=0, x="+minX+".."+maxX+", y="+minY+".."+maxY;}
    public static GameplayRegion from(JsonObject source){
        if(source.has("authoringRegion")){JsonObject saved=source.getAsJsonObject("authoringRegion");return new GameplayRegion(saved.get("minX").getAsInt(),saved.get("maxX").getAsInt(),saved.get("minY").getAsInt(),saved.get("maxY").getAsInt());}
        if(source.has("worldGeometryAuthoritative")&&source.get("worldGeometryAuthoritative").getAsBoolean())return DEFAULT;
        double lowX=0,highX=0,lowY=90,highY=1080;
        if(source.has("length")){double length=source.get("length").getAsDouble();if(Double.isFinite(length))highX=Math.max(0,length);}
        if(source.has("objects"))for(JsonElement entry:source.getAsJsonArray("objects"))if(entry.isJsonObject()){JsonObject object=entry.getAsJsonObject();try{double x=object.get("x").getAsDouble(),y=object.get("y").getAsDouble();if(Double.isFinite(x)&&Double.isFinite(y)){lowX=Math.min(lowX,x);highX=Math.max(highX,x);lowY=Math.min(lowY,y);highY=Math.max(highY,y);}}catch(RuntimeException ignored){}}
        return new GameplayRegion(Math.max(-16,(int)Math.floor(lowX/30)-2),Math.min(4095,(int)Math.ceil(highX/30)+8),Math.max(50,(int)Math.floor(64+lowY/30)-2),Math.min(255,(int)Math.ceil(64+highY/30)+4));
    }
}
