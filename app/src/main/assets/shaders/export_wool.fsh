uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D wool;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
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
  highp vec2 a;
  a = (texCoordTransformed + randomOffset);
  z = texture2D (wool, a);
  r = z;
  mediump vec4 _;
  _ = clamp ((vec4(0.33, 0.33, 0.33, 0.33) + r), 0.0, 1.0);
  r = _;
  mediump vec4 A;
  A.w = 1.0;
  A.xyz = ((g.xyz * y.xyz) * _.xyz);
  gl_FragColor = A;
}

