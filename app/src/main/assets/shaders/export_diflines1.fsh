uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D basetex;
uniform sampler2D blurmap3;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  x = b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  g = z;
  lowp vec4 a;
  a = texture2D (blurmap3, texCoord);
  y = a;
  mediump vec4 _;
  _ = mix (g, y, (1.0 - x.y));
  g = _;
  lowp vec4 A;
  highp vec2 Y;
  Y = ((texCoord * 2.0) + randomOffset);
  A = texture2D (basetex, Y);
  r = A;
  mediump vec4 R;
  R.w = 1.0;
  R.xyz = (_.xyz * r.xyz);
  gl_FragColor = R;
}

