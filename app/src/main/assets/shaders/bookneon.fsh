uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D blurmap2;
uniform sampler2D lut;
uniform highp vec2 timeStrength;
uniform highp vec4 animationVec;
uniform mediump vec3 outlineColor;
varying highp vec2 texCoord;
void main ()
{
  highp vec3 r;
  highp vec3 y;
  highp vec4 g;
  highp vec2 x;
  x = (texCoord - vec2(0.5, 0.5));
  highp float b;
  b = ((0.5 - sqrt(
    dot (x, x)
  )) * 2.0);
  lowp vec4 z;
  z = texture2D (blurmap2, texCoord);
  g = z;
  highp vec3 a;
  highp float _;
  _ = (timeStrength.x * 12.0);
  a.x = (-4.0 + _);
  a.y = (-2.0 + _);
  a.z = _;
  y = (clamp (a, 0.0, 3.0) * mix (animationVec.xyz, vec3(1.0, 1.0, 1.0), (
    pow (timeStrength.x, 3.0)
   * 0.66)));
  highp vec3 A;
  A = clamp (mix (vec3(1.0, 1.0, 1.0), y, timeStrength.y), 0.0, 1.0);
  y = A;
  lowp vec4 Y;
  Y = texture2D (tex, texCoord);
  highp float R;
  highp float X;
  X = (((timeStrength.x - 0.5) + (b * 0.1)) * 2.0);
  R = ((clamp (
    (X * X)
  , 0.0, 1.0) * 0.5) * timeStrength.y);
  lowp float W;
  W = (1.0 - texture2D (blurmap0, texCoord).w);
  r = ((Y.xyz * A) * mix (W, 1.0, (R * R)));
  lowp vec4 Z;
  Z = texture2D (outline, texCoord);
  r = (mix (outlineColor, r, Z.y) + ((g.xyz * A) * mix (0.85, 
    (1.0 - (R * 0.5))
  , timeStrength.y)));
  highp vec3 o;
  o = clamp (((r * 
    (0.9 + (0.1 * b))
  ) + (b * 0.1)), 0.0, 1.0);
  r = o;
  mediump vec3 O;
  O = o;
  highp vec2 I;
  highp vec2 i;
  mediump vec2 l;
  mediump vec2 S;
  mediump float G;
  G = (O.z * 63.0);
  S.y = floor((floor(G) / 8.0));
  S.x = (floor(G) - (S.y * 8.0));
  l.y = floor((ceil(G) / 8.0));
  l.x = (ceil(G) - (l.y * 8.0));
  i = (((S * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * O.xy));
  I = (((l * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * O.xy));
  lowp vec4 B;
  B = texture2D (lut, i);
  mediump vec4 c;
  c = B;
  lowp vec4 d;
  d = texture2D (lut, I);
  mediump vec4 e;
  e = d;
  mediump float f;
  f = clamp (fract(G), 0.0, 1.0);
  mediump vec4 h;
  h.w = 1.0;
  h.xyz = ((c * (1.0 - f)) + (e * f)).xyz;
  gl_FragColor = h;
}

