uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D style;
varying highp vec2 texCoord;
void main ()
{
  lowp vec4 r;
  r = texture2D (tex, texCoord);
  lowp vec4 y;
  y = texture2D (style, texCoord);
  lowp vec4 g;
  g.xyz = ((min (r.xyz, y.xyz) * 0.85) + (y.xyz * 0.15));
  g.w = (texture2D (outline, texCoord).w + r.w);
  gl_FragColor = g;
}

