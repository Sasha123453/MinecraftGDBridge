package local.gdbridge;
import com.google.gson.*;

/** Static native shape contract. Unknown objects remain stored, never guessed. */
public final class CompoundGeometry {
    private CompoundGeometry(){}
    public static double number(JsonObject object,String key,double fallback){try{return object.has(key)?object.get(key).getAsDouble():fallback;}catch(RuntimeException bad){return fallback;}}
    public static boolean flag(JsonObject object,String key){try{return object.has(key)&&object.get(key).getAsBoolean();}catch(RuntimeException bad){return false;}}
    public static String text(JsonObject object,String key,String fallback){try{return object.has(key)?object.get(key).getAsString():fallback;}catch(RuntimeException bad){return fallback;}}
    public static double raw(JsonObject object,String key,double fallback){String[] values=text(object,"data","").split(",");for(int i=0;i+1<values.length;i+=2)if(values[i].equals(key))try{return Double.parseDouble(values[i+1]);}catch(NumberFormatException bad){return fallback;}return fallback;}
    public static boolean special(JsonObject piece){String type=text(piece,"type","");return type.equals("portal")||type.equals("orb");}
    public static boolean visible(JsonObject piece){return !flag(piece,"invisible")&&!flag(piece,"disabled")&&(!piece.has("visualEnabled")||flag(piece,"visualEnabled"));}
    public static boolean physical(JsonObject piece){return !special(piece)&&!flag(piece,"noTouch")&&!flag(piece,"passable")&&!flag(piece,"disabled")&&raw(piece,"121",0)!=1&&(!piece.has("collisionEnabled")||flag(piece,"collisionEnabled"));}
    public static String shape(JsonObject piece){if(special(piece))return "special";String shape=text(piece,"shape",text(piece,"visualShape",""));if(shape.equals("solid-rect")||shape.equals("spike")||shape.equals("spike-strip"))return shape;if(shape.equals("native-slope")&&piece.has("triangleVertices")&&piece.getAsJsonArray("triangleVertices").size()==3)return "triangle";int id=(int)number(piece,"id",number(piece,"objectId",0));if(id==1)return "solid-rect";if(id==8)return "spike";return "unsupported";}
    public static boolean supported(JsonObject piece){return !shape(piece).equals("unsupported");}
}
