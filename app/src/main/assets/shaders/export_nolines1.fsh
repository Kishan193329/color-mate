uniform sampler2D tex;
uniform sampler2D basetex;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  lowp vec4 g;
  g = texture2D (tex, texCoord);
  y = g;
  lowp vec4 x;
  highp vec2 b;
  b = (texCoord * 2.0);
  x = texture2D (basetex, b);
  r = x;
  gl_FragColor = (y * r);
}

