uniform sampler2D drawLayer;
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
        R = A.xyz;
      } else {
        if ((X == 6)) {
          mediump float E;
          highp float F;
          F = (1.0 - (sqrt(
            dot (O, O)
          ) / (
            sqrt(dot (o, o))
           * 2.0)));
          E = F;
          highp vec2 H;
          H.y = 0.0;
          H.x = (A.z / 16.0);
          mediump vec3 J;
          highp vec2 K;
          K = (A.xy + H);
          highp vec2 L;
          L.x = (K.x + 0.000390625);
          L.y = (((
            floor((K.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (fract((E + (loopingTime * 
              float(Z)
            ))), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 M;
          M = texture2D (gradients, L);
          J = M.xyz;
          R = J;
        } else {
          mediump float N;
          highp float P;
          mediump float Q;
          Q = clamp ((1.0 - (
            abs((3.0 - float(X)))
           * 20.0)), 0.0, 1.0);
          P = Q;
          highp float T;
          T = (1.0 - (sqrt(
            dot (O, O)
          ) / (
            sqrt(dot (o, o))
           * 2.0)));
          N = T;
          highp vec2 U;
          U.y = 0.0;
          U.x = (A.z / 16.0);
          mediump vec3 V;
          highp vec2 wr;
          wr = (A.xy + U);
          highp vec2 rr;
          rr.x = (wr.x + 0.000390625);
          rr.y = (((
            floor((wr.y * 256.0))
           / 16.0) + 0.0004882812) + ((
            clamp (mix (N, (1.0 + (
              ((O.x * o.x) + (O.y * o.y))
             * 4.0)), P), 0.0, 1.0)
           * 63.0) / 1024.0));
          lowp vec4 yr;
          yr = texture2D (gradients, rr);
          V = yr.xyz;
          R = V;
        };
      };
    };
  };
  mediump vec4 gr;
  gr.w = 1.0;
  gr.xyz = mix (clamp ((R + r), 0.0, 1.0), y.xyz, y.w);
  gl_FragColor = gr;
}

