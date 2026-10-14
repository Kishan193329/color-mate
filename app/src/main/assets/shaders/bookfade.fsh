precision mediump float;
uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform highp vec2 timeStrength;
uniform mediump vec3 outlineColor;
void main ()
{
  lowp vec4 r;
  lowp vec4 y;
  y = texture2D (tex, texCoord);
  lowp vec4 g;
  g = texture2D (outline, texCoord);
  mediump vec4 x;
  x.xyz = mix (outlineColor, y.xyz, g.x);
  x.w = (g.w + y.w);
  highp vec4 b;
  b.xyz = ((y.xyz * 7.0) + (texCoord.xyx * 1.3));
  b.w = (timeStrength.x * 5.0);
  lowp float z;
  highp vec2 a;
  highp vec3 _;
  lowp vec4 A;
  highp vec4 Y;
  highp vec4 R;
  R = floor((b + dot (b, vec4(0.309017, 0.309017, 0.309017, 0.309017))));
  highp vec4 X;
  X = ((b - R) + dot (R, vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966)));
  highp vec3 W;
  W.x = float((X.x >= X.y));
  W.y = float((X.x >= X.z));
  W.z = float((X.x >= X.w));
  highp vec3 Z;
  Z.x = float((X.y >= X.z));
  Z.y = float((X.y >= X.w));
  Z.z = float((X.z >= X.w));
  Y.x = ((W.x + W.y) + W.z);
  Y.yzw = (1.0 - W);
  Y.y = (Y.y + (Z.x + Z.y));
  Y.zw = (Y.zw + (1.0 - Z.xy));
  Y.z = (Y.z + Z.z);
  Y.w = (Y.w + (1.0 - Z.z));
  highp vec4 o;
  o = clamp (Y, 0.0, 1.0);
  highp vec4 O;
  O = clamp ((Y - 1.0), 0.0, 1.0);
  highp vec4 I;
  I = clamp ((Y - 2.0), 0.0, 1.0);
  highp vec4 i;
  i = ((X - I) + vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966));
  highp vec4 l;
  l = ((X - O) + vec4(0.2763932, 0.2763932, 0.2763932, 0.2763932));
  highp vec4 S;
  S = ((X - o) + vec4(0.4145898, 0.4145898, 0.4145898, 0.4145898));
  highp vec4 G;
  G = (X + vec4(-0.4472136, -0.4472136, -0.4472136, -0.4472136));
  highp vec4 B;
  B = (R - (floor(
    (R * 0.003460208)
  ) * 289.0));
  highp float c;
  c = (((B.w * 34.0) + 1.0) * B.w);
  highp float d;
  d = ((c - (
    floor((c * 0.003460208))
   * 289.0)) + B.z);
  highp float e;
  e = (((d * 34.0) + 1.0) * d);
  highp float f;
  f = ((e - (
    floor((e * 0.003460208))
   * 289.0)) + B.y);
  highp float h;
  h = (((f * 34.0) + 1.0) * f);
  highp float j;
  j = ((h - (
    floor((h * 0.003460208))
   * 289.0)) + B.x);
  highp float k;
  k = (((j * 34.0) + 1.0) * j);
  highp vec4 m;
  m.w = 1.0;
  m.x = I.w;
  m.y = O.w;
  m.z = o.w;
  highp vec4 n;
  n = (B.w + m);
  highp vec4 p;
  p = (((n * 34.0) + 1.0) * n);
  highp vec4 q;
  q.w = 1.0;
  q.x = I.z;
  q.y = O.z;
  q.z = o.z;
  highp vec4 s;
  s = (((p - 
    (floor((p * 0.003460208)) * 289.0)
  ) + B.z) + q);
  highp vec4 t;
  t = (((s * 34.0) + 1.0) * s);
  highp vec4 u;
  u.w = 1.0;
  u.x = I.y;
  u.y = O.y;
  u.z = o.y;
  highp vec4 v;
  v = (((t - 
    (floor((t * 0.003460208)) * 289.0)
  ) + B.y) + u);
  highp vec4 C;
  C = (((v * 34.0) + 1.0) * v);
  highp vec4 D;
  D.w = 1.0;
  D.x = I.x;
  D.y = O.x;
  D.z = o.x;
  highp vec4 E;
  E = (((C - 
    (floor((C * 0.003460208)) * 289.0)
  ) + B.x) + D);
  highp vec4 F;
  highp vec4 H;
  H = (((E * 34.0) + 1.0) * E);
  F = (H - (floor(
    (H * 0.003460208)
  ) * 289.0));
  lowp vec4 J;
  highp vec3 K;
  K = floor((fract(
    (vec3((k - (floor(
      (k * 0.003460208)
    ) * 289.0))) * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  J.xyz = ((K * 0.1428571) - 1.0);
  J.w = (1.5 - dot (abs(J.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 L;
  L = vec4(lessThan (J, vec4(0.0, 0.0, 0.0, 0.0)));
  J.xyz = (J.xyz + ((
    (L.xyz * 2.0)
   - 1.0) * L.www));
  lowp vec4 M;
  highp vec3 N;
  N = floor((fract(
    (F.xxx * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  M.xyz = ((N * 0.1428571) - 1.0);
  M.w = (1.5 - dot (abs(M.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 P;
  P = vec4(lessThan (M, vec4(0.0, 0.0, 0.0, 0.0)));
  M.xyz = (M.xyz + ((
    (P.xyz * 2.0)
   - 1.0) * P.www));
  lowp vec4 Q;
  highp vec3 T;
  T = floor((fract(
    (F.yyy * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  Q.xyz = ((T * 0.1428571) - 1.0);
  Q.w = (1.5 - dot (abs(Q.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 U;
  U = vec4(lessThan (Q, vec4(0.0, 0.0, 0.0, 0.0)));
  Q.xyz = (Q.xyz + ((
    (U.xyz * 2.0)
   - 1.0) * U.www));
  lowp vec4 V;
  highp vec3 wr;
  wr = floor((fract(
    (F.zzz * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  V.xyz = ((wr * 0.1428571) - 1.0);
  V.w = (1.5 - dot (abs(V.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 rr;
  rr = vec4(lessThan (V, vec4(0.0, 0.0, 0.0, 0.0)));
  V.xyz = (V.xyz + ((
    (rr.xyz * 2.0)
   - 1.0) * rr.www));
  lowp vec4 yr;
  highp vec3 gr;
  gr = floor((fract(
    (F.www * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  yr.xyz = ((gr * 0.1428571) - 1.0);
  yr.w = (1.5 - dot (abs(yr.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 xr;
  xr = vec4(lessThan (yr, vec4(0.0, 0.0, 0.0, 0.0)));
  yr.xyz = (yr.xyz + ((
    (xr.xyz * 2.0)
   - 1.0) * xr.www));
  lowp vec4 br;
  br.x = dot (J, J);
  br.y = dot (M, M);
  br.z = dot (Q, Q);
  br.w = dot (V, V);
  lowp vec4 zr;
  zr = (1.792843 - (0.8537347 * br));
  A = (J * zr.x);
  highp vec3 ar;
  ar.x = dot (X, X);
  ar.y = dot (i, i);
  ar.z = dot (l, l);
  highp vec3 _r;
  _r = max ((0.6 - ar), 0.0);
  highp vec2 Ar;
  Ar.x = dot (S, S);
  Ar.y = dot (G, G);
  highp vec2 Yr;
  Yr = max ((0.6 - Ar), 0.0);
  _ = (_r * _r);
  a = (Yr * Yr);
  lowp vec3 Rr;
  Rr.x = dot (A, X);
  Rr.y = dot ((M * zr.y), i);
  Rr.z = dot ((Q * zr.z), l);
  highp vec3 Xr;
  Xr = (_ * _);
  lowp vec2 Wr;
  Wr.x = dot ((V * zr.w), S);
  Wr.y = dot ((yr * (1.792843 - 
    (0.8537347 * dot (yr, yr))
  )), G);
  highp vec2 Zr;
  Zr = (a * a);
  z = (49.0 * (dot (Xr, Rr) + dot (Zr, Wr)));
  mediump vec4 or;
  or.w = 1.0;
  or.xyz = outlineColor;
  mediump vec4 Or;
  lowp float Ir;
  Ir = (1.0 - g.y);
  Or = mix (vec4(1.0, 1.0, 1.0, 1.0), or, Ir);
  r = Or;
  highp vec4 ir;
  ir = mix (x, r, ((1.0 - 
    clamp ((z + ((timeStrength.x - 0.25) * 4.0)), 0.0, 1.0)
  ) * timeStrength.y));
  mediump vec4 lr;
  lr = ir;
  highp vec2 Sr;
  highp vec2 Gr;
  mediump vec2 Br;
  mediump vec2 cr;
  mediump float dr;
  dr = (lr.z * 63.0);
  cr.y = floor((floor(dr) / 8.0));
  cr.x = (floor(dr) - (cr.y * 8.0));
  Br.y = floor((ceil(dr) / 8.0));
  Br.x = (ceil(dr) - (Br.y * 8.0));
  Gr = (((cr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * lr.xy));
  Sr = (((Br * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * lr.xy));
  lowp vec4 er;
  er = texture2D (lut, Gr);
  mediump vec4 fr;
  fr = er;
  lowp vec4 hr;
  hr = texture2D (lut, Sr);
  mediump vec4 jr;
  jr = hr;
  mediump float kr;
  kr = clamp (fract(dr), 0.0, 1.0);
  gl_FragColor = ((fr * (1.0 - kr)) + (jr * kr));
}

