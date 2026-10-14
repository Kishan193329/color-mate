uniform sampler2D tex;
uniform sampler2D colors;
uniform sampler2D colors2;
uniform sampler2D gradients;
uniform sampler2D noise;
uniform highp vec2 noiseAttr;
varying highp vec2 texCoord;
uniform highp float loopingTime;
void main ()
{
  mediump vec2 r;
  mediump vec3 y;
  lowp vec4 g;
  highp vec2 x;
  x = (texCoord * noiseAttr.x);
  g = texture2D (noise, x);
  highp vec3 b;
  b = ((g.xyz - vec3(0.5, 0.5, 0.5)) * noiseAttr.y);
  y = b;
  lowp vec2 z;
  z = texture2D (tex, texCoord).xy;
  r = z;
  highp vec4 a;
  a = texture2D (colors, r);
  highp vec4 _;
  _ = texture2D (colors2, r);
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
  rr.xyz = clamp ((A + y), 0.0, 1.0);
  gl_FragColor = rr;
}

