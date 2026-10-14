uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
uniform sampler2D lut;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump float r;
  mediump vec4 y;
  highp float g;
  mediump vec4 x;
  mediump vec4 b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  b = z;
  x.w = min (min ((1.0 - b.x), (1.0 - b.y)), (1.0 - b.z));
  x.x = (((1.0 - b.x) - x.w) / (1.0 - x.w));
  x.y = (((1.0 - b.y) - x.w) / (1.0 - x.w));
  x.z = (((1.0 - b.z) - x.w) / (1.0 - x.w));
  mediump float a;
  a = max (max (x.x, x.y), max (x.z, x.w));
  g = a;
  lowp vec4 _;
  _ = texture2D (outline, texCoord);
  y = _;
  highp vec2 A;
  A = (texCoord + vec2(0.0, -0.001953125));
  lowp float Y;
  Y = clamp (((texture2D (blurmap0, A).w - 0.95) * 80.0), 0.0, 1.0);
  highp vec2 R;
  highp float X;
  highp vec2 W;
  W = ((A + (randomOffset * 4.0)) * 2.0);
  highp vec3 Z;
  highp vec3 o;
  highp vec4 O;
  highp vec2 I;
  I = floor((W + dot (W, vec2(0.3660254, 0.3660254))));
  highp vec2 i;
  i = ((W - I) + dot (I, vec2(0.2113249, 0.2113249)));
  highp vec2 l;
  if ((i.x > i.y)) {
    l = vec2(1.0, 0.0);
  } else {
    l = vec2(0.0, 1.0);
  };
  highp vec4 S;
  S = (i.xyxy + vec4(0.2113249, 0.2113249, -0.5773503, -0.5773503));
  O.zw = S.zw;
  O.xy = (S.xy - l);
  highp vec2 G;
  G = (I - (floor(
    (I * 0.003460208)
  ) * 289.0));
  highp vec3 B;
  B.xz = vec2(0.0, 1.0);
  B.y = l.y;
  highp vec3 c;
  c = (G.y + B);
  highp vec3 d;
  d = (((c * 34.0) + 1.0) * c);
  highp vec3 e;
  e.xz = vec2(0.0, 1.0);
  e.y = l.x;
  highp vec3 f;
  f = (((d - 
    (floor((d * 0.003460208)) * 289.0)
  ) + G.x) + e);
  highp vec3 h;
  h = (((f * 34.0) + 1.0) * f);
  highp vec3 j;
  j.x = dot (i, i);
  j.y = dot (O.xy, O.xy);
  j.z = dot (S.zw, S.zw);
  highp vec3 k;
  k = max ((0.5 - j), 0.0);
  o = (k * k);
  o = (o * o);
  highp vec3 m;
  m = ((2.0 * fract(
    ((h - (floor(
      (h * 0.003460208)
    ) * 289.0)) * vec3(0.02439024, 0.02439024, 0.02439024))
  )) - 1.0);
  highp vec3 n;
  n = (abs(m) - 0.5);
  highp vec3 p;
  p = (m - floor((m + 0.5)));
  o = (o * (1.792843 - (0.8537347 * 
    ((p * p) + (n * n))
  )));
  Z.x = ((p.x * i.x) + (n.x * i.y));
  Z.yz = ((p.yz * O.xz) + (n.yz * O.yw));
  X = (130.0 * dot (o, Z));
  lowp vec2 q;
  q = texture2D (indexTex, A).xy;
  R = q;
  highp float s;
  s = (((R.x * 256.0) + R.y) / 13.0);
  highp float t;
  t = cos((s + (X * 0.1)));
  highp vec2 u;
  u.x = t;
  u.y = sin((s + (X * 0.1)));
  highp vec2 v;
  v.x = u.y;
  v.y = -(t);
  highp vec2 C;
  C = (texCoord + vec2(0.0, -0.001953125));
  lowp float D;
  D = clamp (((texture2D (blurmap1, C).w - 0.95) * 80.0), 0.0, 1.0);
  highp float E;
  E = mix (Y, (1.0 - clamp (
    (((g - clamp (
      (abs((fract(
        (((A.x * u) + (A.y * v)) * 50.0)
      .y) - 0.5)) * 2.0)
    , 0.0, 1.0)) * 8.0) + 0.5)
  , 0.0, 1.0)), D);
  highp vec2 F;
  F = (texCoord + vec2(0.0, 0.001953125));
  lowp float H;
  H = clamp (((texture2D (blurmap0, F).w - 0.95) * 80.0), 0.0, 1.0);
  highp vec2 J;
  highp float K;
  highp vec2 L;
  L = ((F + (randomOffset * 4.0)) * 2.0);
  highp vec3 M;
  highp vec3 N;
  highp vec4 P;
  highp vec2 Q;
  Q = floor((L + dot (L, vec2(0.3660254, 0.3660254))));
  highp vec2 T;
  T = ((L - Q) + dot (Q, vec2(0.2113249, 0.2113249)));
  highp vec2 U;
  if ((T.x > T.y)) {
    U = vec2(1.0, 0.0);
  } else {
    U = vec2(0.0, 1.0);
  };
  highp vec4 V;
  V = (T.xyxy + vec4(0.2113249, 0.2113249, -0.5773503, -0.5773503));
  P.zw = V.zw;
  P.xy = (V.xy - U);
  highp vec2 wr;
  wr = (Q - (floor(
    (Q * 0.003460208)
  ) * 289.0));
  highp vec3 rr;
  rr.xz = vec2(0.0, 1.0);
  rr.y = U.y;
  highp vec3 yr;
  yr = (wr.y + rr);
  highp vec3 gr;
  gr = (((yr * 34.0) + 1.0) * yr);
  highp vec3 xr;
  xr.xz = vec2(0.0, 1.0);
  xr.y = U.x;
  highp vec3 br;
  br = (((gr - 
    (floor((gr * 0.003460208)) * 289.0)
  ) + wr.x) + xr);
  highp vec3 zr;
  zr = (((br * 34.0) + 1.0) * br);
  highp vec3 ar;
  ar.x = dot (T, T);
  ar.y = dot (P.xy, P.xy);
  ar.z = dot (V.zw, V.zw);
  highp vec3 _r;
  _r = max ((0.5 - ar), 0.0);
  N = (_r * _r);
  N = (N * N);
  highp vec3 Ar;
  Ar = ((2.0 * fract(
    ((zr - (floor(
      (zr * 0.003460208)
    ) * 289.0)) * vec3(0.02439024, 0.02439024, 0.02439024))
  )) - 1.0);
  highp vec3 Yr;
  Yr = (abs(Ar) - 0.5);
  highp vec3 Rr;
  Rr = (Ar - floor((Ar + 0.5)));
  N = (N * (1.792843 - (0.8537347 * 
    ((Rr * Rr) + (Yr * Yr))
  )));
  M.x = ((Rr.x * T.x) + (Yr.x * T.y));
  M.yz = ((Rr.yz * P.xz) + (Yr.yz * P.yw));
  K = (130.0 * dot (N, M));
  lowp vec2 Xr;
  Xr = texture2D (indexTex, F).xy;
  J = Xr;
  highp float Wr;
  Wr = (((J.x * 256.0) + J.y) / 13.0);
  highp float Zr;
  Zr = cos((Wr + (K * 0.1)));
  highp vec2 or;
  or.x = Zr;
  or.y = sin((Wr + (K * 0.1)));
  highp vec2 Or;
  Or.x = or.y;
  Or.y = -(Zr);
  highp vec2 Ir;
  Ir = (texCoord + vec2(0.0, 0.001953125));
  lowp float ir;
  ir = clamp (((texture2D (blurmap1, Ir).w - 0.95) * 80.0), 0.0, 1.0);
  highp vec3 lr;
  lr.yz = vec2(0.0, 0.5);
  lr.x = (E - mix (H, (1.0 - 
    clamp ((((g - 
      clamp ((abs((
        fract((((F.x * or) + (F.y * Or)) * 50.0).y)
       - 0.5)) * 2.0), 0.0, 1.0)
    ) * 8.0) + 0.5), 0.0, 1.0)
  ), ir));
  highp float Sr;
  Sr = (1.0 + (normalize(lr).x * 0.5));
  r = Sr;
  mediump vec4 Gr;
  Gr.w = 1.0;
  Gr.xyz = mix (outlineColor, b.xyz, y.y);
  highp vec2 Br;
  highp vec2 cr;
  mediump vec2 dr;
  mediump vec2 er;
  mediump float fr;
  fr = (Gr.z * 63.0);
  er.y = floor((floor(fr) / 8.0));
  er.x = (floor(fr) - (er.y * 8.0));
  dr.y = floor((ceil(fr) / 8.0));
  dr.x = (ceil(fr) - (dr.y * 8.0));
  cr = (((er * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Gr.xy));
  Br = (((dr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Gr.xy));
  lowp vec4 hr;
  hr = texture2D (lut, cr);
  mediump vec4 jr;
  jr = hr;
  lowp vec4 kr;
  kr = texture2D (lut, Br);
  mediump vec4 mr;
  mr = kr;
  mediump float nr;
  nr = clamp (fract(fr), 0.0, 1.0);
  mediump vec4 pr;
  pr.w = 1.0;
  pr.xyz = clamp (((
    (jr * (1.0 - nr))
   + 
    (mr * nr)
  ).xyz * r), 0.0, 1.0);
  gl_FragColor = pr;
}

