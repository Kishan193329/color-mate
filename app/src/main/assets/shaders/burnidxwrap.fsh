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
  mediump vec2 y;
  lowp vec2 g;
  g = texture2D (tex, texCoord).xy;
  y = g;
  lowp vec4 x;
  highp vec2 b;
  b = (texCoord * noiseAttr.x);
  x = texture2D (noise, b);
  highp vec3 z;
  z = ((x.xyz - vec3(0.5, 0.5, 0.5)) * noiseAttr.y);
  r = z;
  highp vec4 a;
  a = texture2D (colors, y);
  highp vec4 _;
  _ = texture2D (colors2, y);
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
        highp vec2 C;
        C.x = W.y;
        C.y = -(W.x);
        mediump vec3 D;
        mediump int E;
        E = X;
        mediump float F;
        F = float(E);
        highp vec2 H;
        H.x = ((float(mod (F, 8.0))) / 8.0);
        H.y = (floor((
          float(E)
         / 8.0)) / 8.0);
        lowp vec4 J;
        highp vec2 K;
        K = ((H + vec2(0.004882812, 0.004882812)) + (fract(
          (((Z.x * W) + (Z.y * C)) * 8.0)
        ) * 0.1152344));
        J = texture2D (patterns, K);
        D = J.xyz;
        A = ((a.xyz * mix (vec3(1.0, 1.0, 1.0), 
          (D * 2.0)
        , 
          clamp ((1.0 - (abs(
            (1.0 - float(Y))
          ) * 20.0)), 0.0, 1.0)
        )) + (clamp (
          (1.0 - (abs((2.0 - 
            float(Y)
          )) * 20.0))
        , 0.0, 1.0) * D));
      } else {
        if ((Y == 5)) {
          mediump float L;
          mediump vec3 M;
          mediump int N;
          N = X;
          mediump float P;
          P = float(N);
          highp vec2 Q;
          Q.x = ((float(mod (P, 8.0))) / 8.0);
          Q.y = (floor((
            float(N)
           / 8.0)) / 8.0);
          lowp vec4 T;
          highp vec2 U;
          U = ((Q + vec2(0.004882812, 0.004882812)) + (fract(
            (12.0 * texCoord)
          ) * 0.1152344));
          T = texture2D (patterns, U);
          M = T.xyz;
          mediump vec3 V;
          V = normalize((M - vec3(0.5, 0.5, 0.5)));
          highp float wr;
          wr = pow (clamp (dot (V, lightDir), 0.0, 1.0), 16.0);
          L = wr;
          A = (a.xyz + vec3(L));
        } else {
          if ((Y == 6)) {
            mediump float rr;
            highp float yr;
            yr = (1.0 - (sqrt(
              dot (Z, Z)
            ) / (
              sqrt(dot (W, W))
             * 2.0)));
            rr = yr;
            highp vec2 gr;
            gr.y = 0.0;
            gr.x = (a.z / 16.0);
            mediump vec3 xr;
            highp vec2 br;
            br = (a.xy + gr);
            highp vec2 zr;
            zr.x = (br.x + 0.000390625);
            zr.y = (((
              floor((br.y * 256.0))
             / 16.0) + 0.0004882812) + ((
              clamp (fract((rr + (loopingTime * 
                float(X)
              ))), 0.0, 1.0)
             * 63.0) / 1024.0));
            lowp vec4 ar;
            ar = texture2D (gradients, zr);
            xr = ar.xyz;
            A = xr;
          } else {
            mediump float _r;
            highp float Ar;
            mediump float Yr;
            Yr = clamp ((1.0 - (
              abs((3.0 - float(Y)))
             * 20.0)), 0.0, 1.0);
            Ar = Yr;
            highp float Rr;
            Rr = (1.0 - (sqrt(
              dot (Z, Z)
            ) / (
              sqrt(dot (W, W))
             * 2.0)));
            _r = Rr;
            highp vec2 Xr;
            Xr.y = 0.0;
            Xr.x = (a.z / 16.0);
            mediump vec3 Wr;
            highp vec2 Zr;
            Zr = (a.xy + Xr);
            highp vec2 or;
            or.x = (Zr.x + 0.000390625);
            or.y = (((
              floor((Zr.y * 256.0))
             / 16.0) + 0.0004882812) + ((
              clamp (mix (_r, (1.0 + (
                ((Z.x * W.x) + (Z.y * W.y))
               * 4.0)), Ar), 0.0, 1.0)
             * 63.0) / 1024.0));
            lowp vec4 Or;
            Or = texture2D (gradients, or);
            Wr = Or.xyz;
            A = Wr;
          };
        };
      };
    };
  };
  mediump vec4 Ir;
  Ir.w = 1.0;
  Ir.xyz = clamp ((A + r), 0.0, 1.0);
  gl_FragColor = Ir;
}

