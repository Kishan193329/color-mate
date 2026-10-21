uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
varying highp vec2 texCoord;
uniform highp vec2 lightDir;
void main ()
{
  highp float r;
  highp float y;
  highp float g;
  highp float x;
  highp float b;
  highp float z;
  highp float a;
  highp float _;
  lowp float A;
  A = (1.0 - texture2D (outline, texCoord).x);
  _ = A;
  highp float Y;
  Y = ((90.0 * lightDir.x) - 45.0);
  highp float R;
  R = (-45.0 + Y);
  R = (((R - 180.0) * 3.14159) / 180.0);
  highp float X;
  X = cos(R);
  mediump float W;
  W = (0.002929688 * X);
  highp float Z;
  Z = sin(R);
  mediump float o;
  o = (0.002929688 * Z);
  mediump vec2 O;
  O.x = W;
  O.y = o;
  highp vec2 I;
  I = (texCoord + O);
  highp float i;
  i = (-135.0 + Y);
  i = (((i - 180.0) * 3.14159) / 180.0);
  highp float l;
  l = cos(i);
  mediump float S;
  S = (0.002929688 * l);
  highp float G;
  G = sin(i);
  mediump float B;
  B = (0.002929688 * G);
  mediump vec2 c;
  c.x = S;
  c.y = B;
  highp vec2 d;
  d = (texCoord + c);
  lowp float e;
  e = ((1.0 - texture2D (blurmap0, I)).w * (1.0 - texture2D (blurmap0, d)).w);
  a = e;
  highp float f;
  f = (45.0 + Y);
  f = (((f - 180.0) * 3.14159) / 180.0);
  highp float h;
  h = cos(f);
  mediump float j;
  j = (0.00390625 * h);
  highp float k;
  k = sin(f);
  mediump float m;
  m = (0.00390625 * k);
  mediump vec2 n;
  n.x = j;
  n.y = m;
  highp vec2 p;
  p = (texCoord + n);
  highp float q;
  q = (135.0 + Y);
  q = (((q - 180.0) * 3.14159) / 180.0);
  highp float s;
  s = cos(q);
  mediump float t;
  t = (0.00390625 * s);
  highp float u;
  u = sin(q);
  mediump float v;
  v = (0.00390625 * u);
  mediump vec2 C;
  C.x = t;
  C.y = v;
  highp vec2 D;
  D = (texCoord + C);
  lowp float E;
  E = ((1.0 - texture2D (blurmap0, p)).w * (1.0 - texture2D (blurmap0, D)).w);
  z = E;
  z = (1.0 - clamp ((_ - z), 0.0, 1.0));
  highp float F;
  F = (45.0 + Y);
  F = (((F - 180.0) * 3.14159) / 180.0);
  highp float H;
  H = cos(F);
  mediump float J;
  J = (0.009765625 * H);
  highp float K;
  K = sin(F);
  mediump float L;
  L = (0.009765625 * K);
  mediump vec2 M;
  M.x = J;
  M.y = L;
  highp vec2 N;
  N = (texCoord + M);
  highp float P;
  P = (135.0 + Y);
  P = (((P - 180.0) * 3.14159) / 180.0);
  highp float Q;
  Q = cos(P);
  mediump float T;
  T = (0.009765625 * Q);
  highp float U;
  U = sin(P);
  mediump float V;
  V = (0.009765625 * U);
  mediump vec2 wr;
  wr.x = T;
  wr.y = V;
  highp vec2 rr;
  rr = (texCoord + wr);
  lowp float yr;
  yr = (1.0 - (texture2D (blurmap1, N).w * texture2D (blurmap1, rr).w));
  b = yr;
  highp float gr;
  gr = (-45.0 + Y);
  gr = (((gr - 180.0) * 3.14159) / 180.0);
  highp float xr;
  xr = cos(gr);
  mediump float br;
  br = (0.009765625 * xr);
  highp float zr;
  zr = sin(gr);
  mediump float ar;
  ar = (0.009765625 * zr);
  mediump vec2 _r;
  _r.x = br;
  _r.y = ar;
  highp vec2 Ar;
  Ar = (texCoord + _r);
  highp float Yr;
  Yr = (-135.0 + Y);
  Yr = (((Yr - 180.0) * 3.14159) / 180.0);
  highp float Rr;
  Rr = cos(Yr);
  mediump float Xr;
  Xr = (0.009765625 * Rr);
  highp float Wr;
  Wr = sin(Yr);
  mediump float Zr;
  Zr = (0.009765625 * Wr);
  mediump vec2 or;
  or.x = Xr;
  or.y = Zr;
  highp vec2 Or;
  Or = (texCoord + or);
  lowp float Ir;
  Ir = (1.0 - (texture2D (blurmap1, Ar).w * texture2D (blurmap1, Or).w));
  x = Ir;
  highp float ir;
  ir = clamp (((1.0 - _) * b), 0.0, 1.0);
  b = ir;
  x = (1.0 - clamp ((
    (1.0 - _)
   * x), 0.0, 1.0));
  a = (((
    (0.5 * ((z * 0.3) + 0.7))
   * 
    ((x * 0.25) + 0.75)
  ) + (
    clamp ((_ - a), 0.0, 1.0)
   * 0.2)) + (ir * 0.1));
  highp float lr;
  highp float Sr;
  Sr = (16.0 * lightDir.y);
  lr = ((4.0 + Sr) / 2048.0);
  highp float Gr;
  Gr = ((4.0 + Sr) / 2048.0);
  lowp float Br;
  Br = (1.0 - texture2D (blurmap0, texCoord).w);
  g = Br;
  highp float cr;
  cr = (Y + 22.5);
  cr = (((cr - 180.0) * 3.14159) / 180.0);
  highp float dr;
  dr = cos(cr);
  mediump float er;
  er = (lr * dr);
  highp float fr;
  fr = sin(cr);
  mediump float hr;
  hr = (lr * fr);
  mediump vec2 jr;
  jr.x = er;
  jr.y = hr;
  highp vec2 kr;
  kr = (texCoord + jr);
  highp float mr;
  mr = (-22.5 + Y);
  mr = (((mr - 180.0) * 3.14159) / 180.0);
  highp float nr;
  nr = cos(mr);
  mediump float pr;
  pr = (lr * nr);
  highp float qr;
  qr = sin(mr);
  mediump float sr;
  sr = (lr * qr);
  mediump vec2 tr;
  tr.x = pr;
  tr.y = sr;
  highp vec2 ur;
  ur = (texCoord + tr);
  highp float vr;
  vr = (-67.5 + Y);
  vr = (((vr - 180.0) * 3.14159) / 180.0);
  highp float Cr;
  Cr = cos(vr);
  mediump float Dr;
  Dr = (lr * Cr);
  highp float Er;
  Er = sin(vr);
  mediump float Fr;
  Fr = (lr * Er);
  mediump vec2 Hr;
  Hr.x = Dr;
  Hr.y = Fr;
  highp vec2 Jr;
  Jr = (texCoord + Hr);
  highp float Kr;
  Kr = (-112.5 + Y);
  Kr = (((Kr - 180.0) * 3.14159) / 180.0);
  highp float Lr;
  Lr = cos(Kr);
  mediump float Mr;
  Mr = (lr * Lr);
  highp float Nr;
  Nr = sin(Kr);
  mediump float Pr;
  Pr = (lr * Nr);
  mediump vec2 Qr;
  Qr.x = Mr;
  Qr.y = Pr;
  highp vec2 Tr;
  Tr = (texCoord + Qr);
  lowp float Ur;
  Ur = (1.0 - ((texture2D (blurmap0, kr).w * texture2D (blurmap0, ur).w) * (texture2D (blurmap0, Jr).w * texture2D (blurmap0, Tr).w)));
  y = Ur;
  highp float Vr;
  Vr = ((45.0 + Y) - 22.5);
  Vr = (((Vr - 180.0) * 3.14159) / 180.0);
  highp float wy;
  wy = cos(Vr);
  mediump float ry;
  ry = (Gr * wy);
  highp float yy;
  yy = sin(Vr);
  mediump float gy;
  gy = (Gr * yy);
  mediump vec2 xy;
  xy.x = ry;
  xy.y = gy;
  highp vec2 by;
  by = (texCoord + xy);
  highp float zy;
  zy = ((90.0 + Y) - 22.5);
  zy = (((zy - 180.0) * 3.14159) / 180.0);
  highp float ay;
  ay = cos(zy);
  mediump float _y;
  _y = (Gr * ay);
  highp float Ay;
  Ay = sin(zy);
  mediump float Yy;
  Yy = (Gr * Ay);
  mediump vec2 Ry;
  Ry.x = _y;
  Ry.y = Yy;
  highp vec2 Xy;
  Xy = (texCoord + Ry);
  highp float Wy;
  Wy = ((135.0 + Y) - 22.5);
  Wy = (((Wy - 180.0) * 3.14159) / 180.0);
  highp float Zy;
  Zy = cos(Wy);
  mediump float oy;
  oy = (Gr * Zy);
  highp float Oy;
  Oy = sin(Wy);
  mediump float Iy;
  Iy = (Gr * Oy);
  mediump vec2 iy;
  iy.x = oy;
  iy.y = Iy;
  highp vec2 ly;
  ly = (texCoord + iy);
  highp float Sy;
  Sy = ((180.0 + Y) - 22.5);
  Sy = (((Sy - 180.0) * 3.14159) / 180.0);
  highp float Gy;
  Gy = cos(Sy);
  mediump float By;
  By = (Gr * Gy);
  highp float cy;
  cy = sin(Sy);
  mediump float dy;
  dy = (Gr * cy);
  mediump vec2 ey;
  ey.x = By;
  ey.y = dy;
  highp vec2 fy;
  fy = (texCoord + ey);
  lowp float hy;
  hy = (1.0 - ((texture2D (blurmap0, by).w * texture2D (blurmap0, Xy).w) * (texture2D (blurmap0, ly).w * texture2D (blurmap0, fy).w)));
  r = hy;
  highp float jy;
  if ((g > 0.005)) {
    jy = clamp ((g * 7.5), 0.0, 1.0);
  } else {
    jy = g;
  };
  g = jy;
  highp float ky;
  if ((y > 0.005)) {
    ky = clamp ((y * 7.5), 0.0, 1.0);
  } else {
    ky = y;
  };
  y = ky;
  highp float my;
  if ((r > 0.005)) {
    my = clamp ((r * 7.5), 0.0, 1.0);
  } else {
    my = r;
  };
  r = my;
  highp float ny;
  ny = (((
    (0.5 * (((1.0 - 
      clamp ((ky - jy), 0.0, 1.0)
    ) * 0.075) + 0.925))
   * 
    (((1.0 - clamp (
      (jy - ky)
    , 0.0, 1.0)) * 0.075) + 0.925)
  ) + (
    clamp ((jy - my), 0.0, 1.0)
   * 0.35)) + (clamp (
    (my - jy)
  , 0.0, 1.0) * 0.35));
  highp float py;
  if ((a < 0.5)) {
    py = max (((ny + 
      (2.0 * a)
    ) - 1.0), 0.0);
  } else {
    py = min ((ny + (2.0 * 
      (a - 0.5)
    )), 1.0);
  };
  highp float qy;
  if ((a < 0.5)) {
    qy = max (((ny + 
      (2.0 * a)
    ) - 1.0), 0.0);
  } else {
    qy = min ((ny + (2.0 * 
      (a - 0.5)
    )), 1.0);
  };
  highp float sy;
  if ((a < 0.5)) {
    sy = max (((ny + 
      (2.0 * a)
    ) - 1.0), 0.0);
  } else {
    sy = min ((ny + (2.0 * 
      (a - 0.5)
    )), 1.0);
  };
  highp vec3 ty;
  ty.x = py;
  ty.y = qy;
  ty.z = sy;
  mediump vec4 uy;
  uy.w = 1.0;
  uy.xyz = ty;
  gl_FragColor = uy;
}

