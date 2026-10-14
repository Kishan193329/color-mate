precision highp float;

 uniform sampler2D u_textureBase;
 uniform sampler2D u_textureBlueprint;
 uniform sampler2D u_texturePaint;
 uniform sampler2D u_textureColors;

 uniform int u_mode;
 uniform float showMistookColors;
 uniform float showHint;
 uniform float u_alpha;
 uniform vec2 u_selectedGroup;

 varying vec2 v_texCoord;

 void groupPainting() {
     vec2 coordsBlueprint = texture2D( u_textureBlueprint, v_texCoord ).st;
     vec2 coordsPaint = texture2D( u_texturePaint, v_texCoord ).st;
     vec4 finalColor = texture2D( u_textureColors, coordsPaint );
     if(u_selectedGroup != vec2(0.0,0.0)
        && abs(coordsBlueprint.x-u_selectedGroup.x) < 0.005
        && abs(coordsBlueprint.y-u_selectedGroup.y) < 0.005)
         finalColor.a = min(1.0, finalColor.a+0.4*showHint);
     if(abs(coordsBlueprint.x-coordsPaint.x) > 0.001 || abs(coordsBlueprint.y-coordsPaint.y) > 0.001)
         finalColor.a = max(0.0, finalColor.a*0.5*showMistookColors);
     gl_FragColor = finalColor;
 }

 void timelapsePainting() {
     vec2 coordsBlueprint = texture2D( u_textureBlueprint, v_texCoord ).st;
     vec2 coordsPaint = texture2D( u_texturePaint, v_texCoord ).st;
     vec4 finalColor = texture2D( u_textureColors, coordsPaint );
     if(abs(coordsBlueprint.x-coordsPaint.x) > 0.001 || abs(coordsBlueprint.y-coordsPaint.y) > 0.001) finalColor.a = 0.0;
     gl_FragColor = finalColor;
 }

 void regularTexture() {
     vec4 color = texture2D( u_textureBase, v_texCoord );
     color.a *= u_alpha;
     gl_FragColor = color;
 }

 void debugTexture() {
     vec4 color = texture2D( u_textureBase, v_texCoord );
     if(color != vec4(0.0,0.0,0.0,0.0))
     {
         gl_FragColor = vec4(0.0,1.0,0.0,1.0);
     }
     else
     {
         gl_FragColor = vec4(1.0,0.0,0.0,1.0);
     }
 }

 void main() {
     if ( u_mode == 0 )
     {
         groupPainting();
     }
     else if(u_mode == 2)
     {
         debugTexture();
     }
     else if(u_mode == 3)
     {
         timelapsePainting();
     }
     else
     {
         regularTexture();
     }
 }