uniform sampler2D tex;
uniform sampler2D tex2;
uniform sampler2D colors;
uniform sampler2D colors2;
uniform sampler2D gradients;
uniform sampler2D noise;
varying highp vec2 texCoord;
uniform highp vec2 noiseAttr;
uniform highp float loopingTime;
void main ()
{
  mediump vec3 r;
  mediump vec2 y;
  mediump vec4 g;
  mediump vec2 x;
  lowp vec2 b;
  b = texture2D (tex, texCoord).xy;
  x = b;
  lowp vec4 z;
  z = texture2D (tex2, texCoord);
  g = z;
  y = (g.xy + vec2(0.0078125, 0.0078125));
  highp vec4 a;
  a = texture2D (colors, x);
  highp vec4 _;
  _ = texture2D (colors2, x);
  mediump vec3 A;
  mediump int Y;
  highp int R;
  R = int(floor((a.w * 8.0)));
  Y = R;
  if ((Y >= 7)) {
    A = a.xyz;
  } else {
    highp int X;
    X = int(floor((
      fract((a.w * 8.0))
     * 32.0)));
    highp vec2 W;
    W = ((_.xy - vec2(0.5, 0.5)) * 2.0);
    highp vec2 Z;
    Z = (texCoord - _.zw);
    if ((Y == 1)) {
      mediump vec3 o;
      highp vec3 O;
      mediump float I;
      highp float i;
      i = (1.0 - (sqrt(
        dot (Z, Z)
      ) / (
        sqrt(dot (W, W))
       * 2.0)));
      I = i;
      highp vec2 l;
      l.y = 0.05859375;
      l.x = ((float(X) + 0.5) / 256.0);
      mediump vec3 S;
      highp float G;
      G = I;
      highp vec2 B;
      B.x = (l.x + 0.000390625);
      B.y = (0.9379883 + ((
        clamp (G, 0.0, 1.0)
       * 63.0) / 1024.0));
      lowp vec4 c;
      c = texture2D (gradients, B);
      S = c.xyz;
      O = S;
      highp vec3 d;
      d = mix (fract((a.xyz * 15.9999)), (floor(
        (a.xyz * 16.0)
      ) / 16.0), O.x);
      o = d;
      highp vec3 e;
      e = clamp ((o - O.zzz), 0.0, 1.0);
      o = (o - (e * 0.5));
      highp vec3 f;
      f = clamp ((vec3((1.0 - O.z)) - o), 0.0, 1.0);
      o = (o + (f * 0.5));
      highp vec3 h;
      h = vec3((((O.y - 0.5) * 0.5) + 0.5));
      mediump float j;
      if ((h.x < 0.5)) {
        highp float k;
        k = max (((o.x + 
          (2.0 * h.x)
        ) - 1.0), 0.0);
        j = k;
      } else {
        highp float m;
        m = (2.0 * (h.x - 0.5));
        j = min ((o.x + m), 1.0);
      };
      mediump float n;
      if ((h.y < 0.5)) {
        highp float p;
        p = max (((o.y + 
          (2.0 * h.y)
        ) - 1.0), 0.0);
        n = p;
      } else {
        highp float q;
        q = (2.0 * (h.y - 0.5));
        n = min ((o.y + q), 1.0);
      };
      mediump float s;
      if ((h.z < 0.5)) {
        highp float t;
        t = max (((o.z + 
          (2.0 * h.z)
        ) - 1.0), 0.0);
        s = t;
      } else {
        highp float u;
        u = (2.0 * (h.z - 0.5));
        s = min ((o.z + u), 1.0);
      };
      mediump vec3 v;
      v.x = j;
      v.y = n;
      v.z = s;
      A = v;
    } else {
      if ((Y < 3)) {
        A = a.xyz;
      } else {
        if ((Y == 6)) {
          mediump float C;
          highp float D;
          D = (1.0 - (sqrt(
            dot (Z, Z)
          ) / (
            sqrt(dot (W, W))
           * 2.0)));
          C = D;
          highp vec2 E;
          E.y = 0.0;
          E.x = (a.z / 16.0);
          mediump vec3 F;
          highp vec2 H;
          H = (a.xy + E);
          highp vec2 J;
          J.x = (H.x + 0.000390625);
          J.y = (((
            floor((H.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (fract((C + (loopingTime * 
              float(X)
            ))), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 K;
          K = texture2D (gradients, J);
          F = K.xyz;
          A = F;
        } else {
          mediump float L;
          highp float M;
          mediump float N;
          N = clamp ((1.0 - (
            abs((3.0 - float(Y)))
           * 20.0)), 0.0, 1.0);
          M = N;
          highp float P;
          P = (1.0 - (sqrt(
            dot (Z, Z)
          ) / (
            sqrt(dot (W, W))
           * 2.0)));
          L = P;
          highp vec2 Q;
          Q.y = 0.0;
          Q.x = (a.z / 16.0);
          mediump vec3 T;
          highp vec2 U;
          U = (a.xy + Q);
          highp vec2 V;
          V.x = (U.x + 0.000390625);
          V.y = (((
            floor((U.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (mix (L, (1.0 + (
              ((Z.x * W.x) + (Z.y * W.y))
             * 4.0)), M), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 wr;
          wr = texture2D (gradients, V);
          T = wr.xyz;
          A = T;
        };
      };
    };
  };
  mediump vec4 rr;
  rr.w = 1.0;
  rr.xyz = clamp (A, 0.0, 1.0);
  highp vec4 yr;
  yr = texture2D (colors, y);
  highp vec4 gr;
  gr = texture2D (colors2, y);
  mediump vec3 xr;
  mediump int br;
  highp int zr;
  zr = int(floor((yr.w * 8.0)));
  br = zr;
  if ((br >= 7)) {
    xr = yr.xyz;
  } else {
    highp int ar;
    ar = int(floor((
      fract((yr.w * 8.0))
     * 32.0)));
    highp vec2 _r;
    _r = ((gr.xy - vec2(0.5, 0.5)) * 2.0);
    highp vec2 Ar;
    Ar = (texCoord - gr.zw);
    if ((br == 1)) {
      mediump vec3 Yr;
      highp vec3 Rr;
      mediump float Xr;
      highp float Wr;
      Wr = (1.0 - (sqrt(
        dot (Ar, Ar)
      ) / (
        sqrt(dot (_r, _r))
       * 2.0)));
      Xr = Wr;
      highp vec2 Zr;
      Zr.y = 0.05859375;
      Zr.x = ((float(ar) + 0.5) / 256.0);
      mediump vec3 or;
      highp float Or;
      Or = Xr;
      highp vec2 Ir;
      Ir.x = (Zr.x + 0.000390625);
      Ir.y = (0.9379883 + ((
        clamp (Or, 0.0, 1.0)
       * 63.0) / 1024.0));
      lowp vec4 ir;
      ir = texture2D (gradients, Ir);
      or = ir.xyz;
      Rr = or;
      highp vec3 lr;
      lr = mix (fract((yr.xyz * 15.9999)), (floor(
        (yr.xyz * 16.0)
      ) / 16.0), Rr.x);
      Yr = lr;
      highp vec3 Sr;
      Sr = clamp ((Yr - Rr.zzz), 0.0, 1.0);
      Yr = (Yr - (Sr * 0.5));
      highp vec3 Gr;
      Gr = clamp ((vec3((1.0 - Rr.z)) - Yr), 0.0, 1.0);
      Yr = (Yr + (Gr * 0.5));
      highp vec3 Br;
      Br = vec3((((Rr.y - 0.5) * 0.5) + 0.5));
      mediump float cr;
      if ((Br.x < 0.5)) {
        highp float dr;
        dr = max (((Yr.x + 
          (2.0 * Br.x)
        ) - 1.0), 0.0);
        cr = dr;
      } else {
        highp float er;
        er = (2.0 * (Br.x - 0.5));
        cr = min ((Yr.x + er), 1.0);
      };
      mediump float fr;
      if ((Br.y < 0.5)) {
        highp float hr;
        hr = max (((Yr.y + 
          (2.0 * Br.y)
        ) - 1.0), 0.0);
        fr = hr;
      } else {
        highp float jr;
        jr = (2.0 * (Br.y - 0.5));
        fr = min ((Yr.y + jr), 1.0);
      };
      mediump float kr;
      if ((Br.z < 0.5)) {
        highp float mr;
        mr = max (((Yr.z + 
          (2.0 * Br.z)
        ) - 1.0), 0.0);
        kr = mr;
      } else {
        highp float nr;
        nr = (2.0 * (Br.z - 0.5));
        kr = min ((Yr.z + nr), 1.0);
      };
      mediump vec3 pr;
      pr.x = cr;
      pr.y = fr;
      pr.z = kr;
      xr = pr;
    } else {
      if ((br < 3)) {
        xr = yr.xyz;
      } else {
        if ((br == 6)) {
          mediump float qr;
          highp float sr;
          sr = (1.0 - (sqrt(
            dot (Ar, Ar)
          ) / (
            sqrt(dot (_r, _r))
           * 2.0)));
          qr = sr;
          highp vec2 tr;
          tr.y = 0.0;
          tr.x = (yr.z / 16.0);
          mediump vec3 ur;
          highp vec2 vr;
          vr = (yr.xy + tr);
          highp vec2 Cr;
          Cr.x = (vr.x + 0.000390625);
          Cr.y = (((
            floor((vr.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (fract((qr + (loopingTime * 
              float(ar)
            ))), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 Dr;
          Dr = texture2D (gradients, Cr);
          ur = Dr.xyz;
          xr = ur;
        } else {
          mediump float Er;
          highp float Fr;
          mediump float Hr;
          Hr = clamp ((1.0 - (
            abs((3.0 - float(br)))
           * 20.0)), 0.0, 1.0);
          Fr = Hr;
          highp float Jr;
          Jr = (1.0 - (sqrt(
            dot (Ar, Ar)
          ) / (
            sqrt(dot (_r, _r))
           * 2.0)));
          Er = Jr;
          highp vec2 Kr;
          Kr.y = 0.0;
          Kr.x = (yr.z / 16.0);
          mediump vec3 Lr;
          highp vec2 Mr;
          Mr = (yr.xy + Kr);
          highp vec2 Nr;
          Nr.x = (Mr.x + 0.000390625);
          Nr.y = (((
            floor((Mr.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (mix (Er, (1.0 + (
              ((Ar.x * _r.x) + (Ar.y * _r.y))
             * 4.0)), Fr), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 Pr;
          Pr = texture2D (gradients, Nr);
          Lr = Pr.xyz;
          xr = Lr;
        };
      };
    };
  };
  mediump vec4 Qr;
  Qr.w = 1.0;
  Qr.xyz = clamp (xr, 0.0, 1.0);
  lowp vec4 Tr;
  highp vec2 Ur;
  Ur = (texCoord * noiseAttr.x);
  Tr = texture2D (noise, Ur);
  highp vec3 Vr;
  Vr = ((Tr.xyz - vec3(0.5, 0.5, 0.5)) * noiseAttr.y);
  r = Vr;
  mediump vec4 wy;
  wy.w = 1.0;
  wy.xyz = (sqrt(mix (
    (rr.xyz * rr.xyz)
  , 
    (Qr.xyz * Qr.xyz)
  , g.w)) + r);
  gl_FragColor = wy;
}

