uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D paper;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  mediump vec3 y;
  mediump vec3 g;
  mediump vec3 x;
  highp vec2 b;
  b = (texCoordTransformed + randomOffset);
  lowp vec3 z;
  z = texture2D (paper, b).xyz;
  x = z;
  lowp vec3 a;
  a = texture2D (tex, texCoord).xyz;
  g = a;
  lowp vec3 _;
  _ = texture2D (outline, texCoord).xyz;
  y = _;
  mediump float A;
  A = ((0.5 - (
    (max (max (g.x, g.y), g.z) + min (min (g.x, g.y), g.z))
   / 2.0)) * 2.0);
  mediump vec4 Y;
  Y.w = 1.0;
  Y.xyz = mix (outlineColor, (g + (
    (A - clamp (A, -0.5, 0.5))
   * 0.125)), y.y);
  highp vec2 R;
  highp vec2 X;
  mediump vec2 W;
  mediump vec2 Z;
  mediump float o;
  o = (Y.z * 63.0);
  Z.y = floor((floor(o) / 8.0));
  Z.x = (floor(o) - (Z.y * 8.0));
  W.y = floor((ceil(o) / 8.0));
  W.x = (ceil(o) - (W.y * 8.0));
  X = (((Z * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Y.xy));
  R = (((W * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Y.xy));
  lowp vec4 O;
  O = texture2D (lut, X);
  mediump vec4 I;
  I = O;
  lowp vec4 i;
  i = texture2D (lut, R);
  mediump vec4 l;
  l = i;
  mediump float S;
  S = clamp (fract(o), 0.0, 1.0);
  g = (((I * 
    (1.0 - S)
  ) + (l * S)).xyz * x);
  highp vec2 G;
  G = (texCoordTransformed - (randomOffset * 0.5));
  lowp vec3 B;
  B = (vec3(1.0, 1.0, 1.0) - texture2D (paper, G).xyz);
  r = B;
  mediump vec4 c;
  c.w = 1.0;
  c.xyz = (g + r);
  gl_FragColor = c;
}

