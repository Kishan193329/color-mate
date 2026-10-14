uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D lut;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump float r;
  highp float y;
  mediump vec4 g;
  highp vec2 x;
  mediump vec4 b;
  mediump vec4 z;
  highp float a;
  highp vec2 _;
  _ = ((texCoord + randomOffset) * 2.0);
  highp vec3 A;
  highp vec3 Y;
  highp vec4 R;
  highp vec2 X;
  X = floor((_ + dot (_, vec2(0.3660254, 0.3660254))));
  highp vec2 W;
  W = ((_ - X) + dot (X, vec2(0.2113249, 0.2113249)));
  highp vec2 Z;
  if ((W.x > W.y)) {
    Z = vec2(1.0, 0.0);
  } else {
    Z = vec2(0.0, 1.0);
  };
  highp vec4 o;
  o = (W.xyxy + vec4(0.2113249, 0.2113249, -0.5773503, -0.5773503));
  R.zw = o.zw;
  R.xy = (o.xy - Z);
  highp vec2 O;
  O = (X - (floor(
    (X * 0.003460208)
  ) * 289.0));
  highp vec3 I;
  I.xz = vec2(0.0, 1.0);
  I.y = Z.y;
  highp vec3 i;
  i = (O.y + I);
  highp vec3 l;
  l = (((i * 34.0) + 1.0) * i);
  highp vec3 S;
  S.xz = vec2(0.0, 1.0);
  S.y = Z.x;
  highp vec3 G;
  G = (((l - 
    (floor((l * 0.003460208)) * 289.0)
  ) + O.x) + S);
  highp vec3 B;
  B = (((G * 34.0) + 1.0) * G);
  highp vec3 c;
  c.x = dot (W, W);
  c.y = dot (R.xy, R.xy);
  c.z = dot (o.zw, o.zw);
  highp vec3 d;
  d = max ((0.5 - c), 0.0);
  Y = (d * d);
  Y = (Y * Y);
  highp vec3 e;
  e = ((2.0 * fract(
    ((B - (floor(
      (B * 0.003460208)
    ) * 289.0)) * vec3(0.02439024, 0.02439024, 0.02439024))
  )) - 1.0);
  highp vec3 f;
  f = (abs(e) - 0.5);
  highp vec3 h;
  h = (e - floor((e + 0.5)));
  Y = (Y * (1.792843 - (0.8537347 * 
    ((h * h) + (f * f))
  )));
  A.x = ((h.x * W.x) + (f.x * W.y));
  A.yz = ((h.yz * R.xz) + (f.yz * R.yw));
  a = (130.0 * dot (Y, A));
  lowp vec4 j;
  j = texture2D (outline, texCoord);
  z = j;
  lowp vec4 k;
  k = texture2D (tex, texCoord);
  b = k;
  lowp vec2 m;
  m = texture2D (indexTex, texCoord).xy;
  x = m;
  highp float n;
  n = (((x.x * 256.0) + x.y) / 13.0);
  highp float p;
  p = cos((n + (a * 0.03)));
  highp vec2 q;
  q.x = p;
  q.y = sin((n + (a * 0.03)));
  highp vec2 s;
  s.x = q.y;
  s.y = -(p);
  g.w = min (min ((1.0 - b.x), (1.0 - b.y)), (1.0 - b.z));
  g.x = (((1.0 - b.x) - g.w) / (1.0 - g.w));
  g.y = (((1.0 - b.y) - g.w) / (1.0 - g.w));
  g.z = (((1.0 - b.z) - g.w) / (1.0 - g.w));
  mediump float t;
  t = max (max (g.x, g.y), max (g.z, g.w));
  y = t;
  highp float u;
  u = clamp (((
    (y - clamp ((abs(
      (fract(((
        (texCoord.x * q)
       + 
        (texCoord.y * s)
      ) * 200.0).y) - 0.5)
    ) * 2.0), 0.0, 1.0))
   * 3.0) + 0.5), 0.0, 1.0);
  r = u;
  highp float v;
  v = clamp (y, 0.05, 0.99);
  y = v;
  g = ((g / (v + 0.0001)) * r);
  mediump vec3 C;
  C.x = (1.0 - min (1.0, (
    (g.x * (1.0 - g.w))
   + g.w)));
  C.y = (1.0 - min (1.0, (
    (g.y * (1.0 - g.w))
   + g.w)));
  C.z = (1.0 - min (1.0, (
    (g.z * (1.0 - g.w))
   + g.w)));
  mediump vec3 D;
  D = clamp (mix (outlineColor, C, z.y), 0.0, 0.99999);
  highp vec2 E;
  highp vec2 F;
  mediump vec2 H;
  mediump vec2 J;
  mediump float K;
  K = (D.z * 63.0);
  J.y = floor((floor(K) / 8.0));
  J.x = (floor(K) - (J.y * 8.0));
  H.y = floor((ceil(K) / 8.0));
  H.x = (ceil(K) - (H.y * 8.0));
  F = (((J * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * D.xy));
  E = (((H * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * D.xy));
  lowp vec4 L;
  L = texture2D (lut, F);
  mediump vec4 M;
  M = L;
  lowp vec4 N;
  N = texture2D (lut, E);
  mediump vec4 P;
  P = N;
  mediump float Q;
  Q = clamp (fract(K), 0.0, 1.0);
  mediump vec4 T;
  T.w = 1.0;
  T.xyz = ((M * (1.0 - Q)) + (P * Q)).xyz;
  gl_FragColor = T;
}

