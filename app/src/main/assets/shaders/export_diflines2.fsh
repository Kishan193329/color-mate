uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D basetex;
uniform sampler2D blurmap0;
uniform sampler2D noise;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  lowp vec4 z;
  highp vec2 a;
  a = (texCoord * 50.0);
  z = texture2D (noise, a);
  b = z;
  highp vec2 _;
  _ = (texCoord + ((b.xy - vec2(0.5, 0.5)) * 0.005));
  lowp vec4 A;
  A = texture2D (outline, _);
  x = A;
  lowp vec4 Y;
  Y = texture2D (tex, _);
  g = Y;
  lowp vec4 R;
  R = texture2D (blurmap0, _);
  y = R;
  mediump vec4 X;
  X = mix (g, (vec4(1.0, 1.0, 1.0, 1.0) - y), (1.0 - x.y));
  g = X;
  lowp vec4 W;
  W = texture2D (basetex, texCoord);
  r = W;
  mediump vec4 Z;
  Z.w = 1.0;
  Z.xyz = (X.xyz * r.xyz);
  gl_FragColor = Z;
}

