uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D om;
uniform sampler2D background;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
uniform highp vec2 randomOffset;
void main ()
{
  highp int r;
  mediump float y;
  mediump vec4 g;
  lowp vec4 x;
  mediump vec2 b;
  mediump vec3 z;
  highp vec2 a;
  a = (texCoordTransformed + ((randomOffset * 0.05) - 0.025));
  lowp vec3 _;
  _ = texture2D (om, a).xyz;
  highp vec2 A;
  A = (texCoord - randomOffset);
  lowp vec3 Y;
  Y = mix (vec3(0.5, 0.5, 0.5), texture2D (background, A).xyz, 0.5);
  z = Y;
  lowp vec2 R;
  R = _.xy;
  b = R;
  x = texture2D (outline, texCoord);
  mediump vec2 X;
  X.x = b.x;
  X.y = (1.0 - b.y);
  b = X;
  g = vec4(0.0, 0.0, 0.0, 0.0);
  y = 0.75;
  r = 0;
  for (; r < 15; r++, y += 0.0175) {
    mediump vec4 W;
    mediump vec2 Z;
    Z = (vec2(-0.0024, 0.0024) + mix (b, texCoord, y));
    mediump vec4 o;
    mediump vec4 O;
    lowp vec4 I;
    I = texture2D (tex, Z);
    mediump vec4 i;
    i = I;
    O = i;
    if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
      o = i;
    } else {
      lowp vec4 l;
      mediump vec2 S;
      S = (Z + vec2(0.001464844, 0.0));
      l = texture2D (tex, S);
      O = l;
      if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
        o = O;
      } else {
        lowp vec4 G;
        mediump vec2 B;
        B = (Z + vec2(-0.001464844, 0.0));
        G = texture2D (tex, B);
        O = G;
        if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
          o = O;
        } else {
          lowp vec4 c;
          mediump vec2 d;
          d = (Z + vec2(0.001464844, 0.001464844));
          c = texture2D (tex, d);
          O = c;
          if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
            o = O;
          } else {
            lowp vec4 e;
            mediump vec2 f;
            f = (Z + vec2(0.0, 0.001464844));
            e = texture2D (tex, f);
            O = e;
            if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
              o = O;
            } else {
              lowp vec4 h;
              mediump vec2 j;
              j = (Z + vec2(0.001464844, 0.001464844));
              h = texture2D (tex, j);
              O = h;
              if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
                o = O;
              } else {
                lowp vec4 k;
                mediump vec2 m;
                m = (Z + vec2(0.001464844, -0.001464844));
                k = texture2D (tex, m);
                O = k;
                if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
                  o = O;
                } else {
                  lowp vec4 n;
                  mediump vec2 p;
                  p = (Z + vec2(0.0, -0.001464844));
                  n = texture2D (tex, p);
                  O = n;
                  if ((O != vec4(0.0, 0.0, 0.0, 1.0))) {
                    o = O;
                  } else {
                    lowp vec4 q;
                    mediump vec2 s;
                    s = (Z + vec2(0.001464844, -0.001464844));
                    q = texture2D (tex, s);
                    O = q;
                    o = O;
                  };
                };
              };
            };
          };
        };
      };
    };
    W.xyz = o.xyz;
    W.w = 1.0;
    if ((((o.x + o.y) + o.z) < 0.2)) {
      W = vec4(0.0, 0.0, 0.0, 0.0);
    };
    g = (g + W);
  };
  g = (g * (1.0/(g.w)));
  x.w = (x.w * mix (1.0, 0.86, _.z));
  mediump vec3 t;
  t = mix (outlineColor, g.xyz, x.y);
  mediump vec3 u;
  mediump float v;
  v = ((max (
    max (t.x, t.y)
  , t.z) + min (
    min (t.x, t.y)
  , t.z)) / 2.0);
  if ((v > 0.825)) {
    lowp float C;
    mediump float D;
    D = ((v - 0.825) * 0.25);
    C = D;
    u = (t - vec3(C));
  } else {
    if ((v < 0.0)) {
      u = t;
    } else {
      u = t;
    };
  };
  mediump vec3 E;
  highp vec2 F;
  highp vec2 H;
  mediump vec2 J;
  mediump vec2 K;
  mediump float L;
  L = (u.z * 63.0);
  K.y = floor((floor(L) / 8.0));
  K.x = (floor(L) - (K.y * 8.0));
  J.y = floor((ceil(L) / 8.0));
  J.x = (ceil(L) - (J.y * 8.0));
  H = (((K * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * u.xy));
  F = (((J * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * u.xy));
  lowp vec4 M;
  M = texture2D (lut, H);
  mediump vec4 N;
  N = M;
  lowp vec4 P;
  P = texture2D (lut, F);
  mediump vec4 Q;
  Q = P;
  mediump float T;
  T = clamp (fract(L), 0.0, 1.0);
  E = ((N * (1.0 - T)) + (Q * T)).xyz;
  mediump float U;
  if ((E.x < 0.5)) {
    U = ((2.0 * E.x) * z.x);
  } else {
    U = (1.0 - ((2.0 * 
      (1.0 - E.x)
    ) * (1.0 - z.x)));
  };
  mediump float V;
  if ((E.y < 0.5)) {
    V = ((2.0 * E.y) * z.y);
  } else {
    V = (1.0 - ((2.0 * 
      (1.0 - E.y)
    ) * (1.0 - z.y)));
  };
  mediump float wr;
  if ((E.z < 0.5)) {
    wr = ((2.0 * E.z) * z.z);
  } else {
    wr = (1.0 - ((2.0 * 
      (1.0 - E.z)
    ) * (1.0 - z.z)));
  };
  mediump vec3 rr;
  rr.x = U;
  rr.y = V;
  rr.z = wr;
  lowp vec4 yr;
  yr.xyz = rr;
  yr.w = (x.w * 2.0);
  gl_FragColor = yr;
}

