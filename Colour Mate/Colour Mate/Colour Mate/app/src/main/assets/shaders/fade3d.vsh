attribute mediump vec3 vertex;
attribute mediump vec3 normal;
attribute mediump vec2 uv;
attribute mediump vec4 ao;
varying mediump vec2 texCoord;
varying mediump vec3 pixelNormal;
varying highp vec4 worldpos;
varying highp vec3 colorMul;
varying mediump float fade;
varying mediump mat3 normalMatrix;
varying mediump vec3 toView;
varying mediump vec3 toViewScreenSpace;
uniform mat4 modelMatrix;
uniform mat4 cameraMatrix;
uniform mat4 projectionMatrix;
uniform highp vec2 timeStrength;
void main ()
{
  highp mat4 r;
  r = modelMatrix;
  mediump vec4 y;
  y.w = 1.0;
  y.xyz = vertex;
  worldpos = (modelMatrix * y);
  mediump vec4 g;
  g.w = 1.0;
  g.xyz = vertex;
  highp vec4 x;
  x = ((projectionMatrix * r) * g);
  gl_Position = x;
  highp vec3 b;
  b.z = -1.0;
  b.xy = (x / x.w).xy;
  toViewScreenSpace = b;
  toViewScreenSpace.x = (toViewScreenSpace.x / projectionMatrix[0].x);
  toViewScreenSpace.y = (toViewScreenSpace.y / projectionMatrix[1].y);
  mat3 z;
  z[0] = cameraMatrix[0].xyz;
  z[1] = cameraMatrix[1].xyz;
  z[2] = cameraMatrix[2].xyz;
  toView = (z * normalize(toViewScreenSpace));
  pixelNormal = normal;
  mediump vec4 a;
  a.w = 0.0;
  a.xyz = normal;
  highp vec3 _;
  _ = (r * a).xyz;
  normalMatrix[2] = _;
  highp vec3 A;
  A.y = 0.0;
  A.x = -(_.z);
  A.z = _.x;
  highp vec3 Y;
  Y = normalize(A);
  normalMatrix[0] = Y;
  mediump vec3 R;
  R = normalMatrix[0];
  mediump vec3 X;
  X = normalMatrix[2];
  normalMatrix[1] = normalize(((R.yzx * X.zxy) - (R.zxy * X.yzx)));
  highp vec4 W;
  W.xyz = (vertex * 5.0);
  W.w = (timeStrength.x * 10.0);
  highp float Z;
  highp vec2 o;
  highp vec3 O;
  lowp vec4 I;
  highp vec4 i;
  highp vec4 l;
  l = floor((W + dot (W, vec4(0.309017, 0.309017, 0.309017, 0.309017))));
  highp vec4 S;
  S = ((W - l) + dot (l, vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966)));
  highp vec3 G;
  G.x = float((S.x >= S.y));
  G.y = float((S.x >= S.z));
  G.z = float((S.x >= S.w));
  highp vec3 B;
  B.x = float((S.y >= S.z));
  B.y = float((S.y >= S.w));
  B.z = float((S.z >= S.w));
  i.x = ((G.x + G.y) + G.z);
  i.yzw = (1.0 - G);
  i.y = (i.y + (B.x + B.y));
  i.zw = (i.zw + (1.0 - B.xy));
  i.z = (i.z + B.z);
  i.w = (i.w + (1.0 - B.z));
  highp vec4 c;
  c = clamp (i, 0.0, 1.0);
  highp vec4 d;
  d = clamp ((i - 1.0), 0.0, 1.0);
  highp vec4 e;
  e = clamp ((i - 2.0), 0.0, 1.0);
  highp vec4 f;
  f = ((S - e) + vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966));
  highp vec4 h;
  h = ((S - d) + vec4(0.2763932, 0.2763932, 0.2763932, 0.2763932));
  highp vec4 j;
  j = ((S - c) + vec4(0.4145898, 0.4145898, 0.4145898, 0.4145898));
  highp vec4 k;
  k = (S + vec4(-0.4472136, -0.4472136, -0.4472136, -0.4472136));
  highp vec4 m;
  m = (l - (floor(
    (l * 0.003460208)
  ) * 289.0));
  highp float n;
  n = (((m.w * 34.0) + 1.0) * m.w);
  highp float p;
  p = ((n - (
    floor((n * 0.003460208))
   * 289.0)) + m.z);
  highp float q;
  q = (((p * 34.0) + 1.0) * p);
  highp float s;
  s = ((q - (
    floor((q * 0.003460208))
   * 289.0)) + m.y);
  highp float t;
  t = (((s * 34.0) + 1.0) * s);
  highp float u;
  u = ((t - (
    floor((t * 0.003460208))
   * 289.0)) + m.x);
  highp float v;
  v = (((u * 34.0) + 1.0) * u);
  highp vec4 C;
  C.w = 1.0;
  C.x = e.w;
  C.y = d.w;
  C.z = c.w;
  highp vec4 D;
  D = (m.w + C);
  highp vec4 E;
  E = (((D * 34.0) + 1.0) * D);
  highp vec4 F;
  F.w = 1.0;
  F.x = e.z;
  F.y = d.z;
  F.z = c.z;
  highp vec4 H;
  H = (((E - 
    (floor((E * 0.003460208)) * 289.0)
  ) + m.z) + F);
  highp vec4 J;
  J = (((H * 34.0) + 1.0) * H);
  highp vec4 K;
  K.w = 1.0;
  K.x = e.y;
  K.y = d.y;
  K.z = c.y;
  highp vec4 L;
  L = (((J - 
    (floor((J * 0.003460208)) * 289.0)
  ) + m.y) + K);
  highp vec4 M;
  M = (((L * 34.0) + 1.0) * L);
  highp vec4 N;
  N.w = 1.0;
  N.x = e.x;
  N.y = d.x;
  N.z = c.x;
  highp vec4 P;
  P = (((M - 
    (floor((M * 0.003460208)) * 289.0)
  ) + m.x) + N);
  highp vec4 Q;
  highp vec4 T;
  T = (((P * 34.0) + 1.0) * P);
  Q = (T - (floor(
    (T * 0.003460208)
  ) * 289.0));
  lowp vec4 U;
  highp vec3 V;
  V = floor((fract(
    (vec3((v - (floor(
      (v * 0.003460208)
    ) * 289.0))) * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  U.xyz = ((V * 0.1428571) - 1.0);
  U.w = (1.5 - dot (abs(U.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 wr;
  wr = vec4(lessThan (U, vec4(0.0, 0.0, 0.0, 0.0)));
  U.xyz = (U.xyz + ((
    (wr.xyz * 2.0)
   - 1.0) * wr.www));
  lowp vec4 rr;
  highp vec3 yr;
  yr = floor((fract(
    (Q.xxx * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  rr.xyz = ((yr * 0.1428571) - 1.0);
  rr.w = (1.5 - dot (abs(rr.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 gr;
  gr = vec4(lessThan (rr, vec4(0.0, 0.0, 0.0, 0.0)));
  rr.xyz = (rr.xyz + ((
    (gr.xyz * 2.0)
   - 1.0) * gr.www));
  lowp vec4 xr;
  highp vec3 br;
  br = floor((fract(
    (Q.yyy * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  xr.xyz = ((br * 0.1428571) - 1.0);
  xr.w = (1.5 - dot (abs(xr.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 zr;
  zr = vec4(lessThan (xr, vec4(0.0, 0.0, 0.0, 0.0)));
  xr.xyz = (xr.xyz + ((
    (zr.xyz * 2.0)
   - 1.0) * zr.www));
  lowp vec4 ar;
  highp vec3 _r;
  _r = floor((fract(
    (Q.zzz * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  ar.xyz = ((_r * 0.1428571) - 1.0);
  ar.w = (1.5 - dot (abs(ar.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 Ar;
  Ar = vec4(lessThan (ar, vec4(0.0, 0.0, 0.0, 0.0)));
  ar.xyz = (ar.xyz + ((
    (Ar.xyz * 2.0)
   - 1.0) * Ar.www));
  lowp vec4 Yr;
  highp vec3 Rr;
  Rr = floor((fract(
    (Q.www * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  Yr.xyz = ((Rr * 0.1428571) - 1.0);
  Yr.w = (1.5 - dot (abs(Yr.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 Xr;
  Xr = vec4(lessThan (Yr, vec4(0.0, 0.0, 0.0, 0.0)));
  Yr.xyz = (Yr.xyz + ((
    (Xr.xyz * 2.0)
   - 1.0) * Xr.www));
  lowp vec4 Wr;
  Wr.x = dot (U, U);
  Wr.y = dot (rr, rr);
  Wr.z = dot (xr, xr);
  Wr.w = dot (ar, ar);
  lowp vec4 Zr;
  Zr = (1.792843 - (0.8537347 * Wr));
  I = (U * Zr.x);
  highp vec3 or;
  or.x = dot (S, S);
  or.y = dot (f, f);
  or.z = dot (h, h);
  highp vec3 Or;
  Or = max ((0.6 - or), 0.0);
  highp vec2 Ir;
  Ir.x = dot (j, j);
  Ir.y = dot (k, k);
  highp vec2 ir;
  ir = max ((0.6 - Ir), 0.0);
  O = (Or * Or);
  o = (ir * ir);
  lowp vec3 lr;
  lr.x = dot (I, S);
  lr.y = dot ((rr * Zr.y), f);
  lr.z = dot ((xr * Zr.z), h);
  lowp float Sr;
  highp vec3 Gr;
  Gr = (O * O);
  Sr = dot (Gr, lr);
  lowp vec2 Br;
  Br.x = dot ((ar * Zr.w), j);
  Br.y = dot ((Yr * (1.792843 - 
    (0.8537347 * dot (Yr, Yr))
  )), k);
  lowp float cr;
  highp vec2 dr;
  dr = (o * o);
  cr = dot (dr, Br);
  Z = (49.0 * (Sr + cr));
  highp float er;
  er = clamp (((
    (Z + 1.75)
   * 3.0) - (timeStrength.x * 16.0)), 0.0, 20.0);
  fade = er;
  texCoord = uv;
  mediump vec3 fr;
  fr = ao.xyz;
  colorMul = fr;
}

