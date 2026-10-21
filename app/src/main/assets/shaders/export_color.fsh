uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
varying highp vec2 texCoord;
uniform highp vec4 color;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (tex, texCoord);
  g = x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  y = b;
  lowp vec4 z;
  z = texture2D (blurmap0, texCoord);
  r = z;
  mediump vec4 a;
  a.w = 1.0;
  a.xyz = mix ((g.xyz * mix (r.w, 1.0, 0.7)), color.xyz, (1.0 - y.x));
  gl_FragColor = a;
}

