uniform sampler2D drawLayer;
uniform sampler2D tex;
uniform sampler2D colors;
uniform sampler2D colors2;
uniform sampler2D patterns;
uniform sampler2D gradients;
uniform sampler2D noise;
uniform highp vec2 noiseAttr;
varying highp vec2 texCoord;
uniform highp float loopingTime;
uniform highp vec3 lightDir;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  mediump vec2 g;
  lowp vec2 x;
  x = texture2D (tex, texCoord).xy;
  g = x;
  lowp vec4 b;
  b = texture2D (drawLayer, texCoord);
  y = b;
  lowp vec4 z;
  highp vec2 a;
  a = (texCoord * noiseAttr.x);
  z = texture2D (noise, a);
  highp vec3 _;
  _ = ((z.xyz - vec3(0.5, 0.5, 0.5)) * noiseAttr.y);
  r = _;
  highp vec4 A;
  A = texture2D (colors, g);
  highp vec4 Y;
  Y = texture2D (colors2, g);
  mediump vec3 R;
  mediump int X;
  highp int W;
  W = int(floor((A.w * 8.0)));
  X = W;
  if ((X >= 7)) {
    R = A.xyz;
  } else {
    highp int Z;
    Z = int(floor((
      fract((A.w * 8.0))
     * 32.0)));
    highp vec2 o;
    o = ((Y.xy - vec2(0.5, 0.5)) * 2.0);
    highp vec2 O;
    O = (texCoord - Y.zw);
    if ((X == 1)) {
      mediump vec3 I;
      highp vec3 i;
      mediump float l;
      highp float S;
      S = (1.0 - (sqrt(
        dot (O, O)
      ) / (
        sqrt(dot (o, o))
       * 2.0)));
      l = S;
      highp vec2 G;
      G.y = 0.05859375;
      G.x = ((float(Z) + 0.5) / 256.0);
      mediump vec3 B;
      highp float c;
      c = l;
      highp vec2 d;
      d.x = (G.x + 0.000390625);
      d.y = (0.9379883 + ((
        clamp (c, 0.0, 1.0)
       * 63.0) / 1024.0));
      lowp vec4 e;
      e = texture2D (gradients, d);
      B = e.xyz;
      i = B;
      highp vec3 f;
      f = mix (fract((A.xyz * 15.9999)), (floor(
        (A.xyz * 16.0)
      ) / 16.0), i.x);
      I = f;
      highp vec3 h;
      h = clamp ((I - i.zzz), 0.0, 1.0);
      I = (I - (h * 0.5));
      highp vec3 j;
      j = clamp ((vec3((1.0 - i.z)) - I), 0.0, 1.0);
      I = (I + (j * 0.5));
      highp vec3 k;
      k = vec3((((i.y - 0.5) * 0.5) + 0.5));
      mediump float m;
      if ((k.x < 0.5)) {
        highp float n;
        n = max (((I.x + 
          (2.0 * k.x)
        ) - 1.0), 0.0);
        m = n;
      } else {
        highp float p;
        p = (2.0 * (k.x - 0.5));
        m = min ((I.x + p), 1.0);
      };
      mediump float q;
      if ((k.y < 0.5)) {
        highp float s;
        s = max (((I.y + 
          (2.0 * k.y)
        ) - 1.0), 0.0);
        q = s;
      } else {
        highp float t;
        t = (2.0 * (k.y - 0.5));
        q = min ((I.y + t), 1.0);
      };
      mediump float u;
      if ((k.z < 0.5)) {
        highp float v;
        v = max (((I.z + 
          (2.0 * k.z)
        ) - 1.0), 0.0);
        u = v;
      } else {
        highp float C;
        C = (2.0 * (k.z - 0.5));
        u = min ((I.z + C), 1.0);
      };
      mediump vec3 D;
      D.x = m;
      D.y = q;
      D.z = u;
      R = D;
    } else {
      if ((X < 3)) {
        highp vec2 E;
        E.x = o.y;
        E.y = -(o.x);
        mediump vec3 F;
        mediump int H;
        H = Z;
        mediump float J;
        J = float(H);
        highp vec2 K;
        K.x = ((float(mod (J, 8.0))) / 8.0);
        K.y = (floor((
          float(H)
         / 8.0)) / 8.0);
        lowp vec4 L;
        highp vec2 M;
        M = ((K + vec2(0.004882812, 0.004882812)) + (fract(
          (((O.x * o) + (O.y * E)) * 8.0)
        ) * 0.1152344));
        L = texture2D (patterns, M);
        F = L.xyz;
        R = ((A.xyz * mix (vec3(1.0, 1.0, 1.0), 
          (F * 2.0)
        , 
          clamp ((1.0 - (abs(
            (1.0 - float(X))
          ) * 20.0)), 0.0, 1.0)
        )) + (clamp (
          (1.0 - (abs((2.0 - 
            float(X)
          )) * 20.0))
        , 0.0, 1.0) * F));
      } else {
        if ((X == 5)) {
          mediump float N;
          mediump vec3 P;
          mediump int Q;
          Q = Z;
          mediump float T;
          T = float(Q);
          highp vec2 U;
          U.x = ((float(mod (T, 8.0))) / 8.0);
          U.y = (floor((
            float(Q)
           / 8.0)) / 8.0);
          lowp vec4 V;
          highp vec2 wr;
          wr = ((U + vec2(0.004882812, 0.004882812)) + (fract(
            (12.0 * texCoord)
          ) * 0.1152344));
          V = texture2D (patterns, wr);
          P = V.xyz;
          mediump vec3 rr;
          rr = normalize((P - vec3(0.5, 0.5, 0.5)));
          highp float yr;
          yr = pow (clamp (dot (rr, lightDir), 0.0, 1.0), 16.0);
          N = yr;
          R = (A.xyz + vec3(N));
        } else {
          if ((X == 6)) {
            mediump float gr;
            highp float xr;
            xr = (1.0 - (sqrt(
              dot (O, O)
            ) / (
              sqrt(dot (o, o))
             * 2.0)));
            gr = xr;
            highp vec2 br;
            br.y = 0.0;
            br.x = (A.z / 16.0);
            mediump vec3 zr;
            highp vec2 ar;
            ar = (A.xy + br);
            highp vec2 _r;
            _r.x = (ar.x + 0.000390625);
            _r.y = (((
              floor((ar.y * 256.0))
             / 16.0) + 0.0004882812) + ((
              clamp (fract((gr + (loopingTime * 
                float(Z)
              ))), 0.0, 1.0)
             * 63.0) / 1024.0));
            lowp vec4 Ar;
            Ar = texture2D (gradients, _r);
            zr = Ar.xyz;
            R = zr;
          } else {
            mediump float Yr;
            highp float Rr;
            mediump float Xr;
            Xr = clamp ((1.0 - (
              abs((3.0 - float(X)))
             * 20.0)), 0.0, 1.0);
            Rr = Xr;
            highp float Wr;
            Wr = (1.0 - (sqrt(
              dot (O, O)
            ) / (
              sqrt(dot (o, o))
             * 2.0)));
            Yr = Wr;
            highp vec2 Zr;
            Zr.y = 0.0;
            Zr.x = (A.z / 16.0);
            mediump vec3 or;
            highp vec2 Or;
            Or = (A.xy + Zr);
            highp vec2 Ir;
            Ir.x = (Or.x + 0.000390625);
            Ir.y = (((
              floor((Or.y * 256.0))
             / 16.0) + 0.0004882812) + ((
              clamp (mix (Yr, (1.0 + (
                ((O.x * o.x) + (O.y * o.y))
               * 4.0)), Rr), 0.0, 1.0)
             * 63.0) / 1024.0));
            lowp vec4 ir;
            ir = texture2D (gradients, Ir);
            or = ir.xyz;
            R = or;
          };
        };
      };
    };
  };
  mediump vec4 lr;
  lr.w = 1.0;
  lr.xyz = mix (clamp ((R + r), 0.0, 1.0), y.xyz, y.w);
  gl_FragColor = lr;
}

