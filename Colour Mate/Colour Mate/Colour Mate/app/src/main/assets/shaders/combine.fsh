uniform sampler2D tex;
uniform sampler2D outline;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  lowp vec4 g;
  g = texture2D (tex, texCoord);
  y = g;
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  r = x;
  mediump vec4 b;
  b.xyz = y.xyz;
  b.w = r.y;
  gl_FragColor = b;
}

