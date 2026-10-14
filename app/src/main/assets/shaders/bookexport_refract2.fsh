precision highp float;
uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D specularMap;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform highp vec2 timeStrength;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  mediump vec2 g;
  highp float x;
  x = (6.28318 * timeStrength.x);
  highp vec2 b;
  b.x = cos(x);
  b.y = sin(x);
  highp vec2 z;
  z = (b * 1.25);
  highp vec4 a;
  highp vec2 _;
  _ = (texCoord * 15.0);
  a.xy = (_ + randomOffset);
  a.zw = z;
  lowp float A;
  highp vec2 Y;
  highp vec3 R;
  lowp vec4 X;
  highp vec4 W;
  highp vec4 Z;
  Z = floor((a + dot (a, vec4(0.309017, 0.309017, 0.309017, 0.309017))));
  highp vec4 o;
  o = ((a - Z) + dot (Z, vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966)));
  highp vec3 O;
  O.x = float((o.x >= o.y));
  O.y = float((o.x >= o.z));
  O.z = float((o.x >= o.w));
  highp vec3 I;
  I.x = float((o.y >= o.z));
  I.y = float((o.y >= o.w));
  I.z = float((o.z >= o.w));
  W.x = ((O.x + O.y) + O.z);
  W.yzw = (1.0 - O);
  W.y = (W.y + (I.x + I.y));
  W.zw = (W.zw + (1.0 - I.xy));
  W.z = (W.z + I.z);
  W.w = (W.w + (1.0 - I.z));
  highp vec4 i;
  i = clamp (W, 0.0, 1.0);
  highp vec4 l;
  l = clamp ((W - 1.0), 0.0, 1.0);
  highp vec4 S;
  S = clamp ((W - 2.0), 0.0, 1.0);
  highp vec4 G;
  G = ((o - S) + vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966));
  highp vec4 B;
  B = ((o - l) + vec4(0.2763932, 0.2763932, 0.2763932, 0.2763932));
  highp vec4 c;
  c = ((o - i) + vec4(0.4145898, 0.4145898, 0.4145898, 0.4145898));
  highp vec4 d;
  d = (o + vec4(-0.4472136, -0.4472136, -0.4472136, -0.4472136));
  highp vec4 e;
  e = (Z - (floor(
    (Z * 0.003460208)
  ) * 289.0));
  highp float f;
  f = (((e.w * 34.0) + 1.0) * e.w);
  highp float h;
  h = ((f - (
    floor((f * 0.003460208))
   * 289.0)) + e.z);
  highp float j;
  j = (((h * 34.0) + 1.0) * h);
  highp float k;
  k = ((j - (
    floor((j * 0.003460208))
   * 289.0)) + e.y);
  highp float m;
  m = (((k * 34.0) + 1.0) * k);
  highp float n;
  n = ((m - (
    floor((m * 0.003460208))
   * 289.0)) + e.x);
  highp float p;
  p = (((n * 34.0) + 1.0) * n);
  highp vec4 q;
  q.w = 1.0;
  q.x = S.w;
  q.y = l.w;
  q.z = i.w;
  highp vec4 s;
  s = (e.w + q);
  highp vec4 t;
  t = (((s * 34.0) + 1.0) * s);
  highp vec4 u;
  u.w = 1.0;
  u.x = S.z;
  u.y = l.z;
  u.z = i.z;
  highp vec4 v;
  v = (((t - 
    (floor((t * 0.003460208)) * 289.0)
  ) + e.z) + u);
  highp vec4 C;
  C = (((v * 34.0) + 1.0) * v);
  highp vec4 D;
  D.w = 1.0;
  D.x = S.y;
  D.y = l.y;
  D.z = i.y;
  highp vec4 E;
  E = (((C - 
    (floor((C * 0.003460208)) * 289.0)
  ) + e.y) + D);
  highp vec4 F;
  F = (((E * 34.0) + 1.0) * E);
  highp vec4 H;
  H.w = 1.0;
  H.x = S.x;
  H.y = l.x;
  H.z = i.x;
  highp vec4 J;
  J = (((F - 
    (floor((F * 0.003460208)) * 289.0)
  ) + e.x) + H);
  highp vec4 K;
  highp vec4 L;
  L = (((J * 34.0) + 1.0) * J);
  K = (L - (floor(
    (L * 0.003460208)
  ) * 289.0));
  lowp vec4 M;
  highp vec3 N;
  N = floor((fract(
    (vec3((p - (floor(
      (p * 0.003460208)
    ) * 289.0))) * vec3(0.00340136, 0.02040816, 0.1428571))
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
    (K.xxx * vec3(0.00340136, 0.02040816, 0.1428571))
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
    (K.yyy * vec3(0.00340136, 0.02040816, 0.1428571))
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
    (K.zzz * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  yr.xyz = ((gr * 0.1428571) - 1.0);
  yr.w = (1.5 - dot (abs(yr.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 xr;
  xr = vec4(lessThan (yr, vec4(0.0, 0.0, 0.0, 0.0)));
  yr.xyz = (yr.xyz + ((
    (xr.xyz * 2.0)
   - 1.0) * xr.www));
  lowp vec4 br;
  highp vec3 zr;
  zr = floor((fract(
    (K.www * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  br.xyz = ((zr * 0.1428571) - 1.0);
  br.w = (1.5 - dot (abs(br.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 ar;
  ar = vec4(lessThan (br, vec4(0.0, 0.0, 0.0, 0.0)));
  br.xyz = (br.xyz + ((
    (ar.xyz * 2.0)
   - 1.0) * ar.www));
  lowp vec4 _r;
  _r.x = dot (M, M);
  _r.y = dot (Q, Q);
  _r.z = dot (V, V);
  _r.w = dot (yr, yr);
  lowp vec4 Ar;
  Ar = (1.792843 - (0.8537347 * _r));
  X = (M * Ar.x);
  highp vec3 Yr;
  Yr.x = dot (o, o);
  Yr.y = dot (G, G);
  Yr.z = dot (B, B);
  highp vec3 Rr;
  Rr = max ((0.6 - Yr), 0.0);
  highp vec2 Xr;
  Xr.x = dot (c, c);
  Xr.y = dot (d, d);
  highp vec2 Wr;
  Wr = max ((0.6 - Xr), 0.0);
  R = (Rr * Rr);
  Y = (Wr * Wr);
  lowp vec3 Zr;
  Zr.x = dot (X, o);
  Zr.y = dot ((Q * Ar.y), G);
  Zr.z = dot ((V * Ar.z), B);
  highp vec3 or;
  or = (R * R);
  lowp vec2 Or;
  Or.x = dot ((yr * Ar.w), c);
  Or.y = dot ((br * (1.792843 - 
    (0.8537347 * dot (br, br))
  )), d);
  highp vec2 Ir;
  Ir = (Y * Y);
  A = (49.0 * (dot (or, Zr) + dot (Ir, Or)));
  highp vec4 ir;
  ir.xy = (_ + randomOffset);
  ir.zw = (z + vec2(100.0, 100.0));
  lowp float lr;
  highp vec2 Sr;
  highp vec3 Gr;
  lowp vec4 Br;
  highp vec4 cr;
  highp vec4 dr;
  dr = floor((ir + dot (ir, vec4(0.309017, 0.309017, 0.309017, 0.309017))));
  highp vec4 er;
  er = ((ir - dr) + dot (dr, vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966)));
  highp vec3 fr;
  fr.x = float((er.x >= er.y));
  fr.y = float((er.x >= er.z));
  fr.z = float((er.x >= er.w));
  highp vec3 hr;
  hr.x = float((er.y >= er.z));
  hr.y = float((er.y >= er.w));
  hr.z = float((er.z >= er.w));
  cr.x = ((fr.x + fr.y) + fr.z);
  cr.yzw = (1.0 - fr);
  cr.y = (cr.y + (hr.x + hr.y));
  cr.zw = (cr.zw + (1.0 - hr.xy));
  cr.z = (cr.z + hr.z);
  cr.w = (cr.w + (1.0 - hr.z));
  highp vec4 jr;
  jr = clamp (cr, 0.0, 1.0);
  highp vec4 kr;
  kr = clamp ((cr - 1.0), 0.0, 1.0);
  highp vec4 mr;
  mr = clamp ((cr - 2.0), 0.0, 1.0);
  highp vec4 nr;
  nr = ((er - mr) + vec4(0.1381966, 0.1381966, 0.1381966, 0.1381966));
  highp vec4 pr;
  pr = ((er - kr) + vec4(0.2763932, 0.2763932, 0.2763932, 0.2763932));
  highp vec4 qr;
  qr = ((er - jr) + vec4(0.4145898, 0.4145898, 0.4145898, 0.4145898));
  highp vec4 sr;
  sr = (er + vec4(-0.4472136, -0.4472136, -0.4472136, -0.4472136));
  highp vec4 tr;
  tr = (dr - (floor(
    (dr * 0.003460208)
  ) * 289.0));
  highp float ur;
  ur = (((tr.w * 34.0) + 1.0) * tr.w);
  highp float vr;
  vr = ((ur - (
    floor((ur * 0.003460208))
   * 289.0)) + tr.z);
  highp float Cr;
  Cr = (((vr * 34.0) + 1.0) * vr);
  highp float Dr;
  Dr = ((Cr - (
    floor((Cr * 0.003460208))
   * 289.0)) + tr.y);
  highp float Er;
  Er = (((Dr * 34.0) + 1.0) * Dr);
  highp float Fr;
  Fr = ((Er - (
    floor((Er * 0.003460208))
   * 289.0)) + tr.x);
  highp float Hr;
  Hr = (((Fr * 34.0) + 1.0) * Fr);
  highp vec4 Jr;
  Jr.w = 1.0;
  Jr.x = mr.w;
  Jr.y = kr.w;
  Jr.z = jr.w;
  highp vec4 Kr;
  Kr = (tr.w + Jr);
  highp vec4 Lr;
  Lr = (((Kr * 34.0) + 1.0) * Kr);
  highp vec4 Mr;
  Mr.w = 1.0;
  Mr.x = mr.z;
  Mr.y = kr.z;
  Mr.z = jr.z;
  highp vec4 Nr;
  Nr = (((Lr - 
    (floor((Lr * 0.003460208)) * 289.0)
  ) + tr.z) + Mr);
  highp vec4 Pr;
  Pr = (((Nr * 34.0) + 1.0) * Nr);
  highp vec4 Qr;
  Qr.w = 1.0;
  Qr.x = mr.y;
  Qr.y = kr.y;
  Qr.z = jr.y;
  highp vec4 Tr;
  Tr = (((Pr - 
    (floor((Pr * 0.003460208)) * 289.0)
  ) + tr.y) + Qr);
  highp vec4 Ur;
  Ur = (((Tr * 34.0) + 1.0) * Tr);
  highp vec4 Vr;
  Vr.w = 1.0;
  Vr.x = mr.x;
  Vr.y = kr.x;
  Vr.z = jr.x;
  highp vec4 wy;
  wy = (((Ur - 
    (floor((Ur * 0.003460208)) * 289.0)
  ) + tr.x) + Vr);
  highp vec4 ry;
  highp vec4 yy;
  yy = (((wy * 34.0) + 1.0) * wy);
  ry = (yy - (floor(
    (yy * 0.003460208)
  ) * 289.0));
  lowp vec4 gy;
  highp vec3 xy;
  xy = floor((fract(
    (vec3((Hr - (floor(
      (Hr * 0.003460208)
    ) * 289.0))) * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  gy.xyz = ((xy * 0.1428571) - 1.0);
  gy.w = (1.5 - dot (abs(gy.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 by;
  by = vec4(lessThan (gy, vec4(0.0, 0.0, 0.0, 0.0)));
  gy.xyz = (gy.xyz + ((
    (by.xyz * 2.0)
   - 1.0) * by.www));
  lowp vec4 zy;
  highp vec3 ay;
  ay = floor((fract(
    (ry.xxx * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  zy.xyz = ((ay * 0.1428571) - 1.0);
  zy.w = (1.5 - dot (abs(zy.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 _y;
  _y = vec4(lessThan (zy, vec4(0.0, 0.0, 0.0, 0.0)));
  zy.xyz = (zy.xyz + ((
    (_y.xyz * 2.0)
   - 1.0) * _y.www));
  lowp vec4 Ay;
  highp vec3 Yy;
  Yy = floor((fract(
    (ry.yyy * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  Ay.xyz = ((Yy * 0.1428571) - 1.0);
  Ay.w = (1.5 - dot (abs(Ay.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 Ry;
  Ry = vec4(lessThan (Ay, vec4(0.0, 0.0, 0.0, 0.0)));
  Ay.xyz = (Ay.xyz + ((
    (Ry.xyz * 2.0)
   - 1.0) * Ry.www));
  lowp vec4 Xy;
  highp vec3 Wy;
  Wy = floor((fract(
    (ry.zzz * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  Xy.xyz = ((Wy * 0.1428571) - 1.0);
  Xy.w = (1.5 - dot (abs(Xy.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 Zy;
  Zy = vec4(lessThan (Xy, vec4(0.0, 0.0, 0.0, 0.0)));
  Xy.xyz = (Xy.xyz + ((
    (Zy.xyz * 2.0)
   - 1.0) * Zy.www));
  lowp vec4 oy;
  highp vec3 Oy;
  Oy = floor((fract(
    (ry.www * vec3(0.00340136, 0.02040816, 0.1428571))
  ) * 7.0));
  oy.xyz = ((Oy * 0.1428571) - 1.0);
  oy.w = (1.5 - dot (abs(oy.xyz), vec3(1.0, 1.0, 1.0)));
  lowp vec4 Iy;
  Iy = vec4(lessThan (oy, vec4(0.0, 0.0, 0.0, 0.0)));
  oy.xyz = (oy.xyz + ((
    (Iy.xyz * 2.0)
   - 1.0) * Iy.www));
  lowp vec4 iy;
  iy.x = dot (gy, gy);
  iy.y = dot (zy, zy);
  iy.z = dot (Ay, Ay);
  iy.w = dot (Xy, Xy);
  lowp vec4 ly;
  ly = (1.792843 - (0.8537347 * iy));
  Br = (gy * ly.x);
  highp vec3 Sy;
  Sy.x = dot (er, er);
  Sy.y = dot (nr, nr);
  Sy.z = dot (pr, pr);
  highp vec3 Gy;
  Gy = max ((0.6 - Sy), 0.0);
  highp vec2 By;
  By.x = dot (qr, qr);
  By.y = dot (sr, sr);
  highp vec2 cy;
  cy = max ((0.6 - By), 0.0);
  Gr = (Gy * Gy);
  Sr = (cy * cy);
  lowp vec3 dy;
  dy.x = dot (Br, er);
  dy.y = dot ((zy * ly.y), nr);
  dy.z = dot ((Ay * ly.z), pr);
  highp vec3 ey;
  ey = (Gr * Gr);
  lowp vec2 fy;
  fy.x = dot ((Xy * ly.w), qr);
  fy.y = dot ((oy * (1.792843 - 
    (0.8537347 * dot (oy, oy))
  )), sr);
  highp vec2 hy;
  hy = (Sr * Sr);
  lr = (49.0 * (dot (ey, dy) + dot (hy, fy)));
  highp vec3 jy;
  jy.z = 18.0;
  jy.x = A;
  jy.y = lr;
  highp vec3 ky;
  ky = normalize(jy);
  highp vec3 my;
  my.z = 0.5;
  my.xy = (texCoord - vec2(0.5, 0.5));
  highp vec3 ny;
  ny = normalize(mix (ky, vec3(0.0, 0.0, 1.0), 0.9));
  highp vec2 py;
  py = (vec2(0.5, 0.5) + (my - (2.0 * 
    (dot (ny, my) * ny)
  )).xy);
  g = py;
  lowp vec4 qy;
  qy = texture2D (outline, g);
  y = qy;
  lowp vec4 sy;
  sy = texture2D (tex, g);
  highp vec2 ty;
  ty = (((my - 
    (2.0 * (dot (ky, my) * ky))
  ).xy * 0.8) + vec2(0.5, 0.5));
  lowp float uy;
  uy = pow (texture2D (specularMap, ty).y, 4.0);
  r = (mix (outlineColor, sy.xyz, y.y) + (vec3(uy) * 0.5));
  mediump vec3 vy;
  vy = clamp (r, 0.0, 1.0);
  r = vy;
  highp vec2 Cy;
  highp vec2 Dy;
  mediump vec2 Ey;
  mediump vec2 Fy;
  mediump float Hy;
  Hy = (vy.z * 63.0);
  Fy.y = floor((floor(Hy) / 8.0));
  Fy.x = (floor(Hy) - (Fy.y * 8.0));
  Ey.y = floor((ceil(Hy) / 8.0));
  Ey.x = (ceil(Hy) - (Ey.y * 8.0));
  Dy = (((Fy * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * vy.xy));
  Cy = (((Ey * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * vy.xy));
  lowp vec4 Jy;
  Jy = texture2D (lut, Dy);
  mediump vec4 Ky;
  Ky = Jy;
  lowp vec4 Ly;
  Ly = texture2D (lut, Cy);
  mediump vec4 My;
  My = Ly;
  mediump float Ny;
  Ny = clamp (fract(Hy), 0.0, 1.0);
  mediump vec4 Py;
  Py.w = 1.0;
  Py.xyz = ((Ky * (1.0 - Ny)) + (My * Ny)).xyz;
  gl_FragColor = Py;
}

