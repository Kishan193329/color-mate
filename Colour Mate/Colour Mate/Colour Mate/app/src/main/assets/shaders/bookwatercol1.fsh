uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D blurmap1;
uniform sampler2D blurmap2;
uniform sampler2D hatch;
uniform sampler2D papertex;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
uniform highp vec2 timeStrength;
uniform highp vec2 randomOffset;
void main ()
{
  highp float r;
  mediump vec3 y;
  mediump float g;
  mediump float x;
  mediump float b;
  mediump vec4 z;
  highp vec2 a;
  mediump vec4 _;
  mediump float A;
  mediump vec4 Y;
  mediump vec4 R;
  lowp vec4 X;
  highp vec2 W;
  W = ((texCoord * 2.0) + randomOffset);
  X = texture2D (hatch, W);
  R = X;
  lowp vec4 Z;
  Z = texture2D (outline, texCoord);
  Y = Z;
  highp vec2 o;
  o = (texCoord - randomOffset);
  lowp float O;
  O = texture2D (papertex, o).x;
  A = O;
  lowp vec4 I;
  I = texture2D (tex, texCoord);
  _ = I;
  lowp vec2 i;
  i = texture2D (indexTex, texCoord).xy;
  a = i;
  highp float l;
  l = (((a.x * 256.0) + a.y) / 13.0);
  highp float S;
  S = cos(l);
  highp vec2 G;
  G.x = S;
  G.y = sin(l);
  highp vec2 B;
  B.x = G.y;
  B.y = -(S);
  lowp vec4 c;
  highp vec2 d;
  d = (((
    (texCoord.x - 0.5)
   * G) + (
    (texCoord.y - 0.5)
   * B)) - randomOffset);
  c = texture2D (hatch, d);
  z = c;
  mediump float e;
  e = (((1.0 - z.y) * 0.5) + 0.5);
  lowp float f;
  f = texture2D (blurmap1, texCoord).w;
  x = f;
  highp float h;
  mediump float j;
  j = ((1.0 - pow (x, 12.0)) * R.y);
  mediump float k;
  k = ((1.0 - pow (x, 22.0)) * clamp ((
    ((R.y - 0.5) * 3.0)
   + 0.5), 0.0, 1.0));
  h = mix (j, k, timeStrength.y);
  g = h;
  highp float m;
  m = mix (e, g, mix (0.5, (timeStrength.x * 0.5), timeStrength.y));
  b = m;
  mediump vec3 n;
  n = mix (vec3(1.0, 1.0, 1.0), _.xyz, b);
  lowp float p;
  p = texture2D (blurmap2, texCoord).w;
  r = p;
  highp vec3 q;
  q = mix (vec3(1.0, 1.0, 1.0), n, mix (1.0, clamp (
    (((timeStrength.x * 36.0) - ((r * r) * 15.0)) - ((z.y - 0.5) * 4.0))
  , 0.0, 1.0), timeStrength.y));
  y = q;
  mediump vec3 s;
  s = mix (mix (outlineColor, y, Y.y), vec3(1.0, 1.0, 1.0), (1.0 - Y.w));
  y = s;
  mediump float t;
  t = ((0.5 - (
    (max (max (s.x, s.y), s.z) + min (min (s.x, s.y), s.z))
   / 2.0)) * 2.0);
  mediump vec3 u;
  mediump vec4 v;
  v.w = 1.0;
  v.xyz = (s + ((t - 
    clamp (t, -0.5, 0.5)
  ) * 0.125));
  highp vec2 C;
  highp vec2 D;
  mediump vec2 E;
  mediump vec2 F;
  mediump float H;
  H = (v.z * 63.0);
  F.y = floor((floor(H) / 8.0));
  F.x = (floor(H) - (F.y * 8.0));
  E.y = floor((ceil(H) / 8.0));
  E.x = (ceil(H) - (E.y * 8.0));
  D = (((F * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * v.xy));
  C = (((E * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * v.xy));
  lowp vec4 J;
  J = texture2D (lut, D);
  mediump vec4 K;
  K = J;
  lowp vec4 L;
  L = texture2D (lut, C);
  mediump vec4 M;
  M = L;
  mediump float N;
  N = clamp (fract(H), 0.0, 1.0);
  u = ((K * (1.0 - N)) + (M * N)).xyz;
  mediump float P;
  if ((A < 0.5)) {
    P = max (((u.x + 
      (2.0 * A)
    ) - 1.0), 0.0);
  } else {
    P = min ((u.x + (2.0 * 
      (A - 0.5)
    )), 1.0);
  };
  mediump float Q;
  if ((A < 0.5)) {
    Q = max (((u.y + 
      (2.0 * A)
    ) - 1.0), 0.0);
  } else {
    Q = min ((u.y + (2.0 * 
      (A - 0.5)
    )), 1.0);
  };
  mediump float T;
  if ((A < 0.5)) {
    T = max (((u.z + 
      (2.0 * A)
    ) - 1.0), 0.0);
  } else {
    T = min ((u.z + (2.0 * 
      (A - 0.5)
    )), 1.0);
  };
  mediump vec3 U;
  U.x = P;
  U.y = Q;
  U.z = T;
  mediump vec4 V;
  V.w = 1.0;
  V.xyz = U;
  gl_FragColor = V;
}

