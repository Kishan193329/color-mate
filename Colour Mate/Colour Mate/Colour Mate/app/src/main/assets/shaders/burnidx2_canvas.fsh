uniform sampler2D drawLayer;
uniform sampler2D tex;
uniform sampler2D tex2;
uniform sampler2D colors;
uniform sampler2D colors2;
uniform sampler2D gradients;
uniform sampler2D noise;
uniform highp vec2 noiseAttr;
varying highp vec2 texCoord;
uniform highp float loopingTime;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  mediump vec2 g;
  mediump vec4 x;
  mediump vec2 b;
  lowp vec2 z;
  z = texture2D (tex, texCoord).xy;
  b = z;
  lowp vec4 a;
  a = texture2D (tex2, texCoord);
  x = a;
  g = (x.xy + vec2(0.0078125, 0.0078125));
  highp vec4 _;
  _ = texture2D (colors, b);
  highp vec4 A;
  A = texture2D (colors2, b);
  mediump vec3 Y;
  mediump int R;
  highp int X;
  X = int(floor((_.w * 8.0)));
  R = X;
  if ((R >= 7)) {
    Y = _.xyz;
  } else {
    highp int W;
    W = int(floor((
      fract((_.w * 8.0))
     * 32.0)));
    highp vec2 Z;
    Z = ((A.xy - vec2(0.5, 0.5)) * 2.0);
    highp vec2 o;
    o = (texCoord - A.zw);
    if ((R == 1)) {
      mediump vec3 O;
      highp vec3 I;
      mediump float i;
      highp float l;
      l = (1.0 - (sqrt(
        dot (o, o)
      ) / (
        sqrt(dot (Z, Z))
       * 2.0)));
      i = l;
      highp vec2 S;
      S.y = 0.05859375;
      S.x = ((float(W) + 0.5) / 256.0);
      mediump vec3 G;
      highp float B;
      B = i;
      highp vec2 c;
      c.x = (S.x + 0.000390625);
      c.y = (0.9379883 + ((
        clamp (B, 0.0, 1.0)
       * 63.0) / 1024.0));
      lowp vec4 d;
      d = texture2D (gradients, c);
      G = d.xyz;
      I = G;
      highp vec3 e;
      e = mix (fract((_.xyz * 15.9999)), (floor(
        (_.xyz * 16.0)
      ) / 16.0), I.x);
      O = e;
      highp vec3 f;
      f = clamp ((O - I.zzz), 0.0, 1.0);
      O = (O - (f * 0.5));
      highp vec3 h;
      h = clamp ((vec3((1.0 - I.z)) - O), 0.0, 1.0);
      O = (O + (h * 0.5));
      highp vec3 j;
      j = vec3((((I.y - 0.5) * 0.5) + 0.5));
      mediump float k;
      if ((j.x < 0.5)) {
        highp float m;
        m = max (((O.x + 
          (2.0 * j.x)
        ) - 1.0), 0.0);
        k = m;
      } else {
        highp float n;
        n = (2.0 * (j.x - 0.5));
        k = min ((O.x + n), 1.0);
      };
      mediump float p;
      if ((j.y < 0.5)) {
        highp float q;
        q = max (((O.y + 
          (2.0 * j.y)
        ) - 1.0), 0.0);
        p = q;
      } else {
        highp float s;
        s = (2.0 * (j.y - 0.5));
        p = min ((O.y + s), 1.0);
      };
      mediump float t;
      if ((j.z < 0.5)) {
        highp float u;
        u = max (((O.z + 
          (2.0 * j.z)
        ) - 1.0), 0.0);
        t = u;
      } else {
        highp float v;
        v = (2.0 * (j.z - 0.5));
        t = min ((O.z + v), 1.0);
      };
      mediump vec3 C;
      C.x = k;
      C.y = p;
      C.z = t;
      Y = C;
    } else {
      if ((R < 3)) {
        Y = _.xyz;
      } else {
        if ((R == 6)) {
          mediump float D;
          highp float E;
          E = (1.0 - (sqrt(
            dot (o, o)
          ) / (
            sqrt(dot (Z, Z))
           * 2.0)));
          D = E;
          highp vec2 F;
          F.y = 0.0;
          F.x = (_.z / 16.0);
          mediump vec3 H;
          highp vec2 J;
          J = (_.xy + F);
          highp vec2 K;
          K.x = (J.x + 0.000390625);
          K.y = (((
            floor((J.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (fract((D + (loopingTime * 
              float(W)
            ))), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 L;
          L = texture2D (gradients, K);
          H = L.xyz;
          Y = H;
        } else {
          mediump float M;
          highp float N;
          mediump float P;
          P = clamp ((1.0 - (
            abs((3.0 - float(R)))
           * 20.0)), 0.0, 1.0);
          N = P;
          highp float Q;
          Q = (1.0 - (sqrt(
            dot (o, o)
          ) / (
            sqrt(dot (Z, Z))
           * 2.0)));
          M = Q;
          highp vec2 T;
          T.y = 0.0;
          T.x = (_.z / 16.0);
          mediump vec3 U;
          highp vec2 V;
          V = (_.xy + T);
          highp vec2 wr;
          wr.x = (V.x + 0.000390625);
          wr.y = (((
            floor((V.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (mix (M, (1.0 + (
              ((o.x * Z.x) + (o.y * Z.y))
             * 4.0)), N), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 rr;
          rr = texture2D (gradients, wr);
          U = rr.xyz;
          Y = U;
        };
      };
    };
  };
  mediump vec4 yr;
  yr.w = 1.0;
  yr.xyz = clamp (Y, 0.0, 1.0);
  highp vec4 gr;
  gr = texture2D (colors, g);
  highp vec4 xr;
  xr = texture2D (colors2, g);
  mediump vec3 br;
  mediump int zr;
  highp int ar;
  ar = int(floor((gr.w * 8.0)));
  zr = ar;
  if ((zr >= 7)) {
    br = gr.xyz;
  } else {
    highp int _r;
    _r = int(floor((
      fract((gr.w * 8.0))
     * 32.0)));
    highp vec2 Ar;
    Ar = ((xr.xy - vec2(0.5, 0.5)) * 2.0);
    highp vec2 Yr;
    Yr = (texCoord - xr.zw);
    if ((zr == 1)) {
      mediump vec3 Rr;
      highp vec3 Xr;
      mediump float Wr;
      highp float Zr;
      Zr = (1.0 - (sqrt(
        dot (Yr, Yr)
      ) / (
        sqrt(dot (Ar, Ar))
       * 2.0)));
      Wr = Zr;
      highp vec2 or;
      or.y = 0.05859375;
      or.x = ((float(_r) + 0.5) / 256.0);
      mediump vec3 Or;
      highp float Ir;
      Ir = Wr;
      highp vec2 ir;
      ir.x = (or.x + 0.000390625);
      ir.y = (0.9379883 + ((
        clamp (Ir, 0.0, 1.0)
       * 63.0) / 1024.0));
      lowp vec4 lr;
      lr = texture2D (gradients, ir);
      Or = lr.xyz;
      Xr = Or;
      highp vec3 Sr;
      Sr = mix (fract((gr.xyz * 15.9999)), (floor(
        (gr.xyz * 16.0)
      ) / 16.0), Xr.x);
      Rr = Sr;
      highp vec3 Gr;
      Gr = clamp ((Rr - Xr.zzz), 0.0, 1.0);
      Rr = (Rr - (Gr * 0.5));
      highp vec3 Br;
      Br = clamp ((vec3((1.0 - Xr.z)) - Rr), 0.0, 1.0);
      Rr = (Rr + (Br * 0.5));
      highp vec3 cr;
      cr = vec3((((Xr.y - 0.5) * 0.5) + 0.5));
      mediump float dr;
      if ((cr.x < 0.5)) {
        highp float er;
        er = max (((Rr.x + 
          (2.0 * cr.x)
        ) - 1.0), 0.0);
        dr = er;
      } else {
        highp float fr;
        fr = (2.0 * (cr.x - 0.5));
        dr = min ((Rr.x + fr), 1.0);
      };
      mediump float hr;
      if ((cr.y < 0.5)) {
        highp float jr;
        jr = max (((Rr.y + 
          (2.0 * cr.y)
        ) - 1.0), 0.0);
        hr = jr;
      } else {
        highp float kr;
        kr = (2.0 * (cr.y - 0.5));
        hr = min ((Rr.y + kr), 1.0);
      };
      mediump float mr;
      if ((cr.z < 0.5)) {
        highp float nr;
        nr = max (((Rr.z + 
          (2.0 * cr.z)
        ) - 1.0), 0.0);
        mr = nr;
      } else {
        highp float pr;
        pr = (2.0 * (cr.z - 0.5));
        mr = min ((Rr.z + pr), 1.0);
      };
      mediump vec3 qr;
      qr.x = dr;
      qr.y = hr;
      qr.z = mr;
      br = qr;
    } else {
      if ((zr < 3)) {
        br = gr.xyz;
      } else {
        if ((zr == 6)) {
          mediump float sr;
          highp float tr;
          tr = (1.0 - (sqrt(
            dot (Yr, Yr)
          ) / (
            sqrt(dot (Ar, Ar))
           * 2.0)));
          sr = tr;
          highp vec2 ur;
          ur.y = 0.0;
          ur.x = (gr.z / 16.0);
          mediump vec3 vr;
          highp vec2 Cr;
          Cr = (gr.xy + ur);
          highp vec2 Dr;
          Dr.x = (Cr.x + 0.000390625);
          Dr.y = (((
            floor((Cr.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (fract((sr + (loopingTime * 
              float(_r)
            ))), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 Er;
          Er = texture2D (gradients, Dr);
          vr = Er.xyz;
          br = vr;
        } else {
          mediump float Fr;
          highp float Hr;
          mediump float Jr;
          Jr = clamp ((1.0 - (
            abs((3.0 - float(zr)))
           * 20.0)), 0.0, 1.0);
          Hr = Jr;
          highp float Kr;
          Kr = (1.0 - (sqrt(
            dot (Yr, Yr)
          ) / (
            sqrt(dot (Ar, Ar))
           * 2.0)));
          Fr = Kr;
          highp vec2 Lr;
          Lr.y = 0.0;
          Lr.x = (gr.z / 16.0);
          mediump vec3 Mr;
          highp vec2 Nr;
          Nr = (gr.xy + Lr);
          highp vec2 Pr;
          Pr.x = (Nr.x + 0.000390625);
          Pr.y = (((
            floor((Nr.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (mix (Fr, (1.0 + (
              ((Yr.x * Ar.x) + (Yr.y * Ar.y))
             * 4.0)), Hr), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 Qr;
          Qr = texture2D (gradients, Pr);
          Mr = Qr.xyz;
          br = Mr;
        };
      };
    };
  };
  mediump vec4 Tr;
  Tr.w = 1.0;
  Tr.xyz = clamp (br, 0.0, 1.0);
  lowp vec4 Ur;
  Ur = texture2D (drawLayer, texCoord);
  y = Ur;
  lowp vec4 Vr;
  highp vec2 wy;
  wy = (texCoord * noiseAttr.x);
  Vr = texture2D (noise, wy);
  highp vec3 ry;
  ry = ((Vr.xyz - vec3(0.5, 0.5, 0.5)) * noiseAttr.y);
  r = ry;
  mediump vec4 yy;
  yy.w = 1.0;
  yy.xyz = mix ((sqrt(
    mix ((yr.xyz * yr.xyz), (Tr.xyz * Tr.xyz), x.w)
  ) + r), y.xyz, y.w);
  gl_FragColor = yy;
}

