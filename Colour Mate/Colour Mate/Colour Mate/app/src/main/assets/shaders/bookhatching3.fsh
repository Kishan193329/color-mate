uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D patterns;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
uniform sampler2D blurmap2;
uniform sampler2D lut;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  mediump vec3 y;
  mediump vec3 g;
  mediump vec3 x;
  mediump vec3 b;
  highp float z;
  highp float a;
  highp float _;
  mediump vec4 A;
  mediump vec4 Y;
  lowp vec4 R;
  R = texture2D (tex, texCoord);
  Y = R;
  lowp vec4 X;
  X = texture2D (outline, texCoord);
  A = X;
  mediump vec4 W;
  W.w = 1.0;
  W.xyz = mix (outlineColor, Y.xyz, A.y);
  highp vec2 Z;
  highp vec2 o;
  mediump vec2 O;
  mediump vec2 I;
  mediump float i;
  i = (W.z * 63.0);
  I.y = floor((floor(i) / 8.0));
  I.x = (floor(i) - (I.y * 8.0));
  O.y = floor((ceil(i) / 8.0));
  O.x = (ceil(i) - (O.y * 8.0));
  o = (((I * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  Z = (((O * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  lowp vec4 l;
  l = texture2D (lut, o);
  mediump vec4 S;
  S = l;
  lowp vec4 G;
  G = texture2D (lut, Z);
  mediump vec4 B;
  B = G;
  mediump float c;
  c = clamp (fract(i), 0.0, 1.0);
  Y.xyz = ((S * (1.0 - c)) + (B * c)).xyz;
  lowp float d;
  lowp vec4 e;
  e = texture2D (blurmap0, texCoord);
  d = ((e.w * e.w) * e.w);
  _ = d;
  lowp float f;
  lowp vec4 h;
  h = texture2D (blurmap1, texCoord);
  f = ((h.w * h.w) * h.w);
  a = f;
  lowp float j;
  lowp vec4 k;
  k = texture2D (blurmap2, texCoord);
  j = ((k.w * k.w) * k.w);
  z = j;
  highp float m;
  mediump vec4 n;
  n = R;
  lowp vec4 p;
  p = texture2D (indexTex, texCoord);
  highp vec2 q;
  q = (p.xy * randomOffset.x);
  highp float s;
  s = (((
    (q.x * 256.0)
   + q.y) / 13.0) + 1.333);
  highp float t;
  t = cos((s + 0.025));
  highp vec2 u;
  u.x = t;
  u.y = sin((s - 0.025));
  highp vec2 v;
  v.x = u.y;
  v.y = -(t);
  highp vec2 C;
  highp float D;
  D = (randomOffset.x * 1.333);
  C = (((texCoord.x * u) + D) + ((texCoord.y * v) + D));
  mediump float E;
  E = (1.0 - ((
    max (max (n.x, n.y), n.z)
   + 
    min (min (n.x, n.y), n.z)
  ) / 2.0));
  m = E;
  mediump vec3 F;
  highp float H;
  highp float J;
  J = (randomOffset.x * 2.0);
  H = (((m * m) * 5.99) + J);
  highp int K;
  K = int(clamp (floor(H), 0.0, 7.0));
  highp int L;
  L = int(clamp (ceil(H), 0.0, 7.0));
  highp vec2 M;
  M.x = ((float(mod (float(K), 4.0))) / 4.0);
  M.y = (floor((
    float(K)
   / 4.0)) / 4.0);
  highp vec2 N;
  N.x = ((float(mod (float(L), 4.0))) / 4.0);
  N.y = (floor((
    float(L)
   / 4.0)) / 4.0);
  lowp vec4 P;
  highp vec2 Q;
  Q = ((M + vec2(0.004882812, 0.004882812)) + (fract(
    (C * 4.0)
  ) * 0.2402344));
  P = texture2D (patterns, Q);
  lowp vec4 T;
  highp vec2 U;
  U = ((N + vec2(0.004882812, 0.004882812)) + (fract(
    (C * 4.0)
  ) * 0.2402344));
  T = texture2D (patterns, U);
  highp vec3 V;
  V = mix (P.xyz, T.xyz, fract(H));
  F = V;
  highp float wr;
  wr = (randomOffset.x * 0.666);
  highp float rr;
  mediump vec4 yr;
  yr = R;
  highp vec2 gr;
  gr = (p.xy * wr);
  highp float xr;
  xr = (((
    (gr.x * 256.0)
   + gr.y) / 13.0) + 1.333);
  highp float br;
  br = cos((xr + 0.025));
  highp vec2 zr;
  zr.x = br;
  zr.y = sin((xr - 0.025));
  highp vec2 ar;
  ar.x = zr.y;
  ar.y = -(br);
  highp vec2 _r;
  _r = (((texCoord.x * zr) + (wr * 1.333)) + ((texCoord.y * ar) + (wr * 1.333)));
  mediump float Ar;
  Ar = (1.0 - ((
    max (max (yr.x, yr.y), yr.z)
   + 
    min (min (yr.x, yr.y), yr.z)
  ) / 2.0));
  rr = Ar;
  mediump vec3 Yr;
  highp float Rr;
  Rr = (((rr * rr) * 5.99) + J);
  highp int Xr;
  Xr = int(clamp (floor(Rr), 0.0, 7.0));
  highp int Wr;
  Wr = int(clamp (ceil(Rr), 0.0, 7.0));
  highp vec2 Zr;
  Zr.x = ((float(mod (float(Xr), 4.0))) / 4.0);
  Zr.y = (floor((
    float(Xr)
   / 4.0)) / 4.0);
  highp vec2 or;
  or.x = ((float(mod (float(Wr), 4.0))) / 4.0);
  or.y = (floor((
    float(Wr)
   / 4.0)) / 4.0);
  lowp vec4 Or;
  highp vec2 Ir;
  Ir = ((Zr + vec2(0.004882812, 0.004882812)) + (fract(
    (_r * 4.0)
  ) * 0.2402344));
  Or = texture2D (patterns, Ir);
  lowp vec4 ir;
  highp vec2 lr;
  lr = ((or + vec2(0.004882812, 0.004882812)) + (fract(
    (_r * 4.0)
  ) * 0.2402344));
  ir = texture2D (patterns, lr);
  highp vec3 Sr;
  Sr = mix (Or.xyz, ir.xyz, fract(Rr));
  Yr = Sr;
  highp vec3 Gr;
  Gr = (1.0 - ((1.0 - Yr.yyy) * (1.0 - _)));
  x = Gr;
  highp float Br;
  Br = (randomOffset.y * 0.666);
  highp float cr;
  mediump vec4 dr;
  dr = R;
  highp vec2 er;
  er = (p.xy * Br);
  highp float fr;
  fr = (((
    (er.x * 256.0)
   + er.y) / 13.0) + 1.333);
  highp float hr;
  hr = cos((fr + 0.025));
  highp vec2 jr;
  jr.x = hr;
  jr.y = sin((fr - 0.025));
  highp vec2 kr;
  kr.x = jr.y;
  kr.y = -(hr);
  highp vec2 mr;
  mr = (((texCoord.x * jr) + (Br * 1.333)) + ((texCoord.y * kr) + (Br * 1.333)));
  mediump float nr;
  nr = (1.0 - ((
    max (max (dr.x, dr.y), dr.z)
   + 
    min (min (dr.x, dr.y), dr.z)
  ) / 2.0));
  cr = nr;
  mediump vec3 pr;
  highp float qr;
  qr = (((cr * cr) * 5.99) + J);
  highp int sr;
  sr = int(clamp (floor(qr), 0.0, 7.0));
  highp int tr;
  tr = int(clamp (ceil(qr), 0.0, 7.0));
  highp vec2 ur;
  ur.x = ((float(mod (float(sr), 4.0))) / 4.0);
  ur.y = (floor((
    float(sr)
   / 4.0)) / 4.0);
  highp vec2 vr;
  vr.x = ((float(mod (float(tr), 4.0))) / 4.0);
  vr.y = (floor((
    float(tr)
   / 4.0)) / 4.0);
  lowp vec4 Cr;
  highp vec2 Dr;
  Dr = ((ur + vec2(0.004882812, 0.004882812)) + (fract(
    (mr * 4.0)
  ) * 0.2402344));
  Cr = texture2D (patterns, Dr);
  lowp vec4 Er;
  highp vec2 Fr;
  Fr = ((vr + vec2(0.004882812, 0.004882812)) + (fract(
    (mr * 4.0)
  ) * 0.2402344));
  Er = texture2D (patterns, Fr);
  highp vec3 Hr;
  Hr = mix (Cr.xyz, Er.xyz, fract(qr));
  pr = Hr;
  highp vec3 Jr;
  Jr = (1.0 - ((1.0 - pr.yyy) * (1.0 - a)));
  g = Jr;
  highp float Kr;
  Kr = (randomOffset.y * 0.333);
  highp float Lr;
  mediump vec4 Mr;
  Mr = R;
  highp vec2 Nr;
  Nr = (p.xy * Kr);
  highp float Pr;
  Pr = (((
    (Nr.x * 256.0)
   + Nr.y) / 13.0) + 1.333);
  highp float Qr;
  Qr = cos((Pr + 0.025));
  highp vec2 Tr;
  Tr.x = Qr;
  Tr.y = sin((Pr - 0.025));
  highp vec2 Ur;
  Ur.x = Tr.y;
  Ur.y = -(Qr);
  highp vec2 Vr;
  Vr = (((texCoord.x * Tr) + (Kr * 1.333)) + ((texCoord.y * Ur) + (Kr * 1.333)));
  mediump float wy;
  wy = (1.0 - ((
    max (max (Mr.x, Mr.y), Mr.z)
   + 
    min (min (Mr.x, Mr.y), Mr.z)
  ) / 2.0));
  Lr = wy;
  mediump vec3 ry;
  highp float yy;
  yy = (((Lr * Lr) * 5.99) + J);
  highp int gy;
  gy = int(clamp (floor(yy), 0.0, 7.0));
  highp int xy;
  xy = int(clamp (ceil(yy), 0.0, 7.0));
  highp vec2 by;
  by.x = ((float(mod (float(gy), 4.0))) / 4.0);
  by.y = (floor((
    float(gy)
   / 4.0)) / 4.0);
  highp vec2 zy;
  zy.x = ((float(mod (float(xy), 4.0))) / 4.0);
  zy.y = (floor((
    float(xy)
   / 4.0)) / 4.0);
  lowp vec4 ay;
  highp vec2 _y;
  _y = ((by + vec2(0.004882812, 0.004882812)) + (fract(
    (Vr * 4.0)
  ) * 0.2402344));
  ay = texture2D (patterns, _y);
  lowp vec4 Ay;
  highp vec2 Yy;
  Yy = ((zy + vec2(0.004882812, 0.004882812)) + (fract(
    (Vr * 4.0)
  ) * 0.2402344));
  Ay = texture2D (patterns, Yy);
  highp vec3 Ry;
  Ry = mix (ay.xyz, Ay.xyz, fract(yy));
  ry = Ry;
  highp vec3 Xy;
  Xy = (1.0 - ((1.0 - ry.yyy) * (1.0 - a)));
  y = Xy;
  highp float Wy;
  Wy = (randomOffset.x * 0.333);
  highp float Zy;
  mediump vec4 oy;
  oy = R;
  highp vec2 Oy;
  Oy = (p.xy * Wy);
  highp float Iy;
  Iy = (((
    (Oy.x * 256.0)
   + Oy.y) / 13.0) + 1.333);
  highp float iy;
  iy = cos((Iy + 0.025));
  highp vec2 ly;
  ly.x = iy;
  ly.y = sin((Iy - 0.025));
  highp vec2 Sy;
  Sy.x = ly.y;
  Sy.y = -(iy);
  highp vec2 Gy;
  Gy = (((texCoord.x * ly) + (Wy * 1.333)) + ((texCoord.y * Sy) + (Wy * 1.333)));
  mediump float By;
  By = (1.0 - ((
    max (max (oy.x, oy.y), oy.z)
   + 
    min (min (oy.x, oy.y), oy.z)
  ) / 2.0));
  Zy = By;
  mediump vec3 cy;
  highp float dy;
  dy = (((Zy * Zy) * 5.99) + J);
  highp int ey;
  ey = int(clamp (floor(dy), 0.0, 7.0));
  highp int fy;
  fy = int(clamp (ceil(dy), 0.0, 7.0));
  highp vec2 hy;
  hy.x = ((float(mod (float(ey), 4.0))) / 4.0);
  hy.y = (floor((
    float(ey)
   / 4.0)) / 4.0);
  highp vec2 jy;
  jy.x = ((float(mod (float(fy), 4.0))) / 4.0);
  jy.y = (floor((
    float(fy)
   / 4.0)) / 4.0);
  lowp vec4 ky;
  highp vec2 my;
  my = ((hy + vec2(0.004882812, 0.004882812)) + (fract(
    (Gy * 4.0)
  ) * 0.2402344));
  ky = texture2D (patterns, my);
  lowp vec4 ny;
  highp vec2 py;
  py = ((jy + vec2(0.004882812, 0.004882812)) + (fract(
    (Gy * 4.0)
  ) * 0.2402344));
  ny = texture2D (patterns, py);
  highp vec3 qy;
  qy = mix (ky.xyz, ny.xyz, fract(dy));
  cy = qy;
  highp vec3 sy;
  sy = (1.0 - ((1.0 - cy.yyy) * (1.0 - z)));
  r = sy;
  b = ((F.yyy * x) * g);
  b = ((b * 0.5) + 0.5);
  r = ((r * 0.6) + 0.4);
  y = ((y * 0.7) + 0.3);
  mediump vec3 ty;
  ty = min (min (b, r), y);
  y = ty;
  b = ((b - 0.5) * 2.0);
  b = ((b * 0.4) + 0.5);
  mediump float uy;
  if ((Y.x < 0.5)) {
    uy = ((2.0 * Y.x) * ty.x);
  } else {
    uy = (1.0 - ((2.0 * 
      (1.0 - Y.x)
    ) * (1.0 - ty.x)));
  };
  mediump float vy;
  if ((Y.y < 0.5)) {
    vy = ((2.0 * Y.y) * ty.y);
  } else {
    vy = (1.0 - ((2.0 * 
      (1.0 - Y.y)
    ) * (1.0 - ty.y)));
  };
  mediump float Cy;
  if ((Y.z < 0.5)) {
    Cy = ((2.0 * Y.z) * ty.z);
  } else {
    Cy = (1.0 - ((2.0 * 
      (1.0 - Y.z)
    ) * (1.0 - ty.z)));
  };
  mediump float Dy;
  if ((b.x < 0.5)) {
    Dy = ((2.0 * b.x) * uy);
  } else {
    Dy = (1.0 - ((2.0 * 
      (1.0 - b.x)
    ) * (1.0 - uy)));
  };
  mediump float Ey;
  if ((b.y < 0.5)) {
    Ey = ((2.0 * b.y) * vy);
  } else {
    Ey = (1.0 - ((2.0 * 
      (1.0 - b.y)
    ) * (1.0 - vy)));
  };
  mediump float Fy;
  if ((b.z < 0.5)) {
    Fy = ((2.0 * b.z) * Cy);
  } else {
    Fy = (1.0 - ((2.0 * 
      (1.0 - b.z)
    ) * (1.0 - Cy)));
  };
  mediump vec3 Hy;
  Hy.x = Dy;
  Hy.y = Ey;
  Hy.z = Fy;
  highp float Jy;
  highp float Ky;
  mediump vec4 Ly;
  lowp vec4 My;
  My = texture2D (indexTex, texCoord);
  highp vec2 Ny;
  Ny = (My.xy * randomOffset.y);
  highp float Py;
  Py = (((
    (Ny.x * 256.0)
   + Ny.y) / 13.0) + 1.333);
  highp float Qy;
  Qy = cos((Py + 0.025));
  highp vec2 Ty;
  Ty.x = Qy;
  Ty.y = sin((Py - 0.025));
  highp vec2 Uy;
  Uy.x = Ty.y;
  Uy.y = -(Qy);
  highp vec2 Vy;
  highp float wg;
  wg = (randomOffset.y * 1.333);
  Vy = (((texCoord.x * Ty) + wg) + ((texCoord.y * Uy) + wg));
  Ly.w = min (min ((1.0 - Y.x), (1.0 - Y.y)), (1.0 - Y.z));
  Ly.x = (((1.0 - Y.x) - Ly.w) / (1.0 - Ly.w));
  Ly.y = (((1.0 - Y.y) - Ly.w) / (1.0 - Ly.w));
  Ly.z = (((1.0 - Y.z) - Ly.w) / (1.0 - Ly.w));
  mediump float rg;
  rg = max (max (Ly.x, Ly.y), max (Ly.z, Ly.w));
  Ky = rg;
  highp float yg;
  yg = clamp (Ky, 0.06, 1.0);
  Ky = yg;
  mediump vec3 gg;
  highp float xg;
  xg = ((yg * yg) * 7.99);
  highp int bg;
  bg = int(clamp (floor(xg), 0.0, 7.0));
  highp int zg;
  zg = int(clamp (ceil(xg), 0.0, 7.0));
  highp vec2 ag;
  ag.x = ((float(mod (float(bg), 4.0))) / 4.0);
  ag.y = (floor((
    float(bg)
   / 4.0)) / 4.0);
  highp vec2 _g;
  _g.x = ((float(mod (float(zg), 4.0))) / 4.0);
  _g.y = (floor((
    float(zg)
   / 4.0)) / 4.0);
  lowp vec4 Ag;
  highp vec2 Yg;
  Yg = ((ag + vec2(0.004882812, 0.004882812)) + (fract(
    (Vy * 4.0)
  ) * 0.2402344));
  Ag = texture2D (patterns, Yg);
  lowp vec4 Rg;
  highp vec2 Xg;
  Xg = ((_g + vec2(0.004882812, 0.004882812)) + (fract(
    (Vy * 4.0)
  ) * 0.2402344));
  Rg = texture2D (patterns, Xg);
  highp vec3 Wg;
  Wg = mix (Ag.xyz, Rg.xyz, fract(xg));
  gg = Wg;
  mediump float Zg;
  Zg = (1.0 - gg.y);
  Jy = Zg;
  Ly = ((Ly / (yg + 0.0001)) * Jy);
  mediump vec4 og;
  og = clamp (Ly, 0.0, 1.0);
  Ly = og;
  mediump vec3 Og;
  Og.x = (1.0 - min (1.0, (
    (og.x * (1.0 - og.w))
   + og.w)));
  Og.y = (1.0 - min (1.0, (
    (og.y * (1.0 - og.w))
   + og.w)));
  Og.z = (1.0 - min (1.0, (
    (og.z * (1.0 - og.w))
   + og.w)));
  highp vec3 Ig;
  Ig = mix (Hy, Og, randomOffset.x);
  b = Ig;
  Y.xyz = mix (Y.xyz, b, A.y);
  mediump vec4 ig;
  ig.w = 1.0;
  ig.xyz = Y.xyz;
  gl_FragColor = ig;
}

