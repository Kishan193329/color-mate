uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
uniform sampler2D paper;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform highp float posterizeLevel;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  highp float y;
  mediump vec3 g;
  mediump vec3 x;
  lowp vec3 b;
  b = mix (vec3(0.5, 0.5, 0.5), texture2D (paper, texCoordTransformed).xyz, 0.35);
  x = b;
  lowp float z;
  z = texture2D (blurmap1, texCoord).w;
  y = z;
  if ((randomOffset.x < 0.5)) {
    lowp vec4 a;
    a = texture2D (blurmap0, texCoord);
    g = a.xyz;
  } else {
    lowp vec4 _;
    _ = texture2D (blurmap1, texCoord);
    g = _.xyz;
  };
  highp vec3 A;
  A = mix (vec3(1.0, 1.0, 1.0), g, clamp (y, 0.0, 1.0));
  g = A;
  if ((randomOffset.y < 0.5)) {
    lowp vec3 Y;
    Y = clamp (texture2D (tex, texCoord).xyz, 0.0, 0.98);
    highp vec3 R;
    R = g;
    highp float X;
    X = floor((2.0 + (posterizeLevel * 4.0)));
    highp vec3 W;
    W = clamp (((
      floor((R * X))
     + vec3(0.5, 0.5, 0.5)) / X), 0.0, 1.0);
    lowp vec3 Z;
    lowp float o;
    o = min (min (Y.x, Y.y), Y.z);
    lowp float O;
    O = max (max (Y.x, Y.y), Y.z);
    lowp float I;
    I = (O - o);
    Z.z = ((O + o) / 2.0);
    if ((I == 0.0)) {
      Z.x = 0.0;
      Z.y = 0.0;
    } else {
      if ((Z.z < 0.5)) {
        Z.y = (I / (O + o));
      } else {
        Z.y = (I / ((2.0 - O) - o));
      };
      lowp float i;
      i = (((
        (O - Y.x)
       / 6.0) + (I / 2.0)) / I);
      lowp float l;
      l = (((
        (O - Y.y)
       / 6.0) + (I / 2.0)) / I);
      lowp float S;
      S = (((
        (O - Y.z)
       / 6.0) + (I / 2.0)) / I);
      if ((Y.x == O)) {
        Z.x = (S - l);
      } else {
        if ((Y.y == O)) {
          Z.x = ((0.3333333 + i) - S);
        } else {
          if ((Y.z == O)) {
            Z.x = ((0.6666667 + l) - i);
          };
        };
      };
      if ((Z.x < 0.0)) {
        Z.x = (Z.x + 1.0);
      } else {
        if ((Z.x > 1.0)) {
          Z.x = (Z.x - 1.0);
        };
      };
    };
    highp vec3 G;
    highp float B;
    B = min (min (W.x, W.y), W.z);
    highp float c;
    c = max (max (W.x, W.y), W.z);
    highp float d;
    d = (c - B);
    G.z = ((c + B) / 2.0);
    if ((d == 0.0)) {
      G.x = 0.0;
      G.y = 0.0;
    } else {
      if ((G.z < 0.5)) {
        G.y = (d / (c + B));
      } else {
        G.y = (d / ((2.0 - c) - B));
      };
      highp float e;
      e = (((
        (c - W.x)
       / 6.0) + (d / 2.0)) / d);
      highp float f;
      f = (((
        (c - W.y)
       / 6.0) + (d / 2.0)) / d);
      highp float h;
      h = (((
        (c - W.z)
       / 6.0) + (d / 2.0)) / d);
      if ((W.x == c)) {
        G.x = (h - f);
      } else {
        if ((W.y == c)) {
          G.x = ((0.3333333 + e) - h);
        } else {
          if ((W.z == c)) {
            G.x = ((0.6666667 + f) - e);
          };
        };
      };
      if ((G.x < 0.0)) {
        G.x = (G.x + 1.0);
      } else {
        if ((G.x > 1.0)) {
          G.x = (G.x - 1.0);
        };
      };
    };
    highp vec3 j;
    j.xy = Z.xy;
    j.z = G.z;
    highp vec3 k;
    if ((j.y == 0.0)) {
      k = j.zzz;
    } else {
      highp float m;
      if ((G.z < 0.5)) {
        m = (G.z * (1.0 + j.y));
      } else {
        m = ((G.z + j.y) - (j.y * G.z));
      };
      highp float n;
      n = ((2.0 * G.z) - m);
      highp float p;
      p = (j.x + 0.3333333);
      highp float q;
      if ((p < 0.0)) {
        p += 1.0;
      } else {
        if ((p > 1.0)) {
          p = (p - 1.0);
        };
      };
      if (((6.0 * p) < 1.0)) {
        q = (n + ((
          (m - n)
         * 6.0) * p));
      } else {
        if (((2.0 * p) < 1.0)) {
          q = m;
        } else {
          if (((3.0 * p) < 2.0)) {
            q = (n + ((
              (m - n)
             * 
              (0.6666667 - p)
            ) * 6.0));
          } else {
            q = n;
          };
        };
      };
      k.x = q;
      highp float s;
      s = j.x;
      highp float t;
      if ((j.x < 0.0)) {
        s = (j.x + 1.0);
      } else {
        if ((s > 1.0)) {
          s = (s - 1.0);
        };
      };
      if (((6.0 * s) < 1.0)) {
        t = (n + ((
          (m - n)
         * 6.0) * s));
      } else {
        if (((2.0 * s) < 1.0)) {
          t = m;
        } else {
          if (((3.0 * s) < 2.0)) {
            t = (n + ((
              (m - n)
             * 
              (0.6666667 - s)
            ) * 6.0));
          } else {
            t = n;
          };
        };
      };
      k.y = t;
      highp float u;
      u = (j.x - 0.3333333);
      highp float v;
      if ((u < 0.0)) {
        u += 1.0;
      } else {
        if ((u > 1.0)) {
          u = (u - 1.0);
        };
      };
      if (((6.0 * u) < 1.0)) {
        v = (n + ((
          (m - n)
         * 6.0) * u));
      } else {
        if (((2.0 * u) < 1.0)) {
          v = m;
        } else {
          if (((3.0 * u) < 2.0)) {
            v = (n + ((
              (m - n)
             * 
              (0.6666667 - u)
            ) * 6.0));
          } else {
            v = n;
          };
        };
      };
      k.z = v;
    };
    highp float C;
    C = min (k.x, k.y);
    mediump float D;
    D = min (C, k.z);
    highp float E;
    E = max (k.x, k.y);
    mediump float F;
    F = max (E, k.z);
    mediump float H;
    H = ((0.5 - (
      (F + D)
     / 2.0)) * 2.0);
    g = (k + ((H - 
      clamp (H, -0.5, 0.5)
    ) * 0.125));
  } else {
    highp vec3 J;
    J = g;
    highp float K;
    K = floor((2.0 + (posterizeLevel * 4.0)));
    highp vec3 L;
    L = clamp (((
      floor((J * K))
     + vec3(0.5, 0.5, 0.5)) / K), 0.0, 1.0);
    highp float M;
    M = min (L.x, L.y);
    mediump float N;
    N = min (M, L.z);
    highp float P;
    P = max (L.x, L.y);
    mediump float Q;
    Q = max (P, L.z);
    mediump float T;
    T = ((0.5 - (
      (Q + N)
     / 2.0)) * 2.0);
    g = (L + ((T - 
      clamp (T, -0.5, 0.5)
    ) * 0.125));
  };
  lowp vec4 U;
  U = texture2D (outline, texCoord);
  r = U;
  mediump vec4 V;
  V.w = 1.0;
  V.xyz = mix (outlineColor, g, r.y);
  highp vec2 wr;
  highp vec2 rr;
  mediump vec2 yr;
  mediump vec2 gr;
  mediump float xr;
  xr = (V.z * 63.0);
  gr.y = floor((floor(xr) / 8.0));
  gr.x = (floor(xr) - (gr.y * 8.0));
  yr.y = floor((ceil(xr) / 8.0));
  yr.x = (ceil(xr) - (yr.y * 8.0));
  rr = (((gr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * V.xy));
  wr = (((yr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * V.xy));
  lowp vec4 br;
  br = texture2D (lut, rr);
  mediump vec4 zr;
  zr = br;
  lowp vec4 ar;
  ar = texture2D (lut, wr);
  mediump vec4 _r;
  _r = ar;
  mediump float Ar;
  Ar = clamp (fract(xr), 0.0, 1.0);
  mediump vec3 Yr;
  Yr = clamp (((zr * 
    (1.0 - Ar)
  ) + (_r * Ar)).xyz, 0.0, 1.0);
  mediump float Rr;
  if ((x.x < 0.5)) {
    Rr = max (((Yr.x + 
      (2.0 * x.x)
    ) - 1.0), 0.0);
  } else {
    Rr = min ((Yr.x + (2.0 * 
      (x.x - 0.5)
    )), 1.0);
  };
  mediump float Xr;
  if ((x.y < 0.5)) {
    Xr = max (((Yr.y + 
      (2.0 * x.y)
    ) - 1.0), 0.0);
  } else {
    Xr = min ((Yr.y + (2.0 * 
      (x.y - 0.5)
    )), 1.0);
  };
  mediump float Wr;
  if ((x.z < 0.5)) {
    Wr = max (((Yr.z + 
      (2.0 * x.z)
    ) - 1.0), 0.0);
  } else {
    Wr = min ((Yr.z + (2.0 * 
      (x.z - 0.5)
    )), 1.0);
  };
  mediump vec3 Zr;
  Zr.x = Rr;
  Zr.y = Xr;
  Zr.z = Wr;
  mediump vec4 or;
  or.w = 1.0;
  or.xyz = Zr;
  gl_FragColor = or;
}

