uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D displace;
uniform sampler2D noisetex;
uniform sampler2D grungetex;
uniform sampler2D papertex;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
uniform highp vec2 randomOffset;
uniform highp vec2 outlineType;
uniform highp vec3 effectType;
void main ()
{
  mediump vec4 r;
  mediump float y;
  mediump vec3 g;
  mediump float x;
  mediump float b;
  mediump float z;
  mediump vec3 a;
  lowp vec3 _;
  _ = texture2D (tex, texCoord).xyz;
  a = _;
  highp vec2 A;
  A = (texCoordTransformed + randomOffset);
  lowp float Y;
  Y = texture2D (grungetex, A).x;
  z = Y;
  highp vec2 R;
  R = (texCoord - randomOffset);
  lowp float X;
  X = texture2D (papertex, R).x;
  b = X;
  highp vec2 W;
  W = (texCoordTransformed + effectType.xy);
  lowp float Z;
  Z = texture2D (noisetex, W).x;
  x = Z;
  lowp vec3 o;
  o = texture2D (displace, texCoord).xyz;
  g = o;
  lowp float O;
  O = texture2D (outline, texCoord).x;
  y = O;
  lowp vec4 I;
  I = texture2D (blurmap0, texCoord);
  r = I;
  mediump float i;
  i = mix (0.5, z, (x * x));
  z = i;
  highp vec3 l;
  l = (1.0 - ((1.0 - g) * r.w));
  highp float S;
  S = floor((3.0 + (effectType.z * 6.0)));
  highp vec3 G;
  G = mix (clamp ((
    (floor((l * S)) + vec3(0.5, 0.5, 0.5))
   / S), 0.0, 1.0), g, x);
  highp float B;
  if ((a.x < 0.5)) {
    B = ((2.0 * a.x) * G.x);
  } else {
    B = (1.0 - ((2.0 * 
      (1.0 - a.x)
    ) * (1.0 - G.x)));
  };
  highp float c;
  if ((a.y < 0.5)) {
    c = ((2.0 * a.y) * G.y);
  } else {
    c = (1.0 - ((2.0 * 
      (1.0 - a.y)
    ) * (1.0 - G.y)));
  };
  highp float d;
  if ((a.z < 0.5)) {
    d = ((2.0 * a.z) * G.z);
  } else {
    d = (1.0 - ((2.0 * 
      (1.0 - a.z)
    ) * (1.0 - G.z)));
  };
  mediump vec3 e;
  e.x = B;
  e.y = c;
  e.z = d;
  if ((effectType.x < 0.5)) {
    mediump float f;
    f = min (g.x, a.x);
    mediump float h;
    h = min (g.y, a.y);
    mediump float j;
    j = min (g.z, a.z);
    mediump float k;
    if ((e.x < 0.5)) {
      k = ((2.0 * e.x) * g.x);
    } else {
      k = (1.0 - ((2.0 * 
        (1.0 - e.x)
      ) * (1.0 - g.x)));
    };
    mediump float m;
    if ((e.y < 0.5)) {
      m = ((2.0 * e.y) * g.y);
    } else {
      m = (1.0 - ((2.0 * 
        (1.0 - e.y)
      ) * (1.0 - g.y)));
    };
    mediump float n;
    if ((e.z < 0.5)) {
      n = ((2.0 * e.z) * g.z);
    } else {
      n = (1.0 - ((2.0 * 
        (1.0 - e.z)
      ) * (1.0 - g.z)));
    };
    mediump vec3 p;
    mediump float q;
    q = min (min (f, h), j);
    mediump float s;
    s = max (max (f, h), j);
    mediump float t;
    t = (s - q);
    p.z = ((s + q) / 2.0);
    if ((t == 0.0)) {
      p.x = 0.0;
      p.y = 0.0;
    } else {
      if ((p.z < 0.5)) {
        p.y = (t / (s + q));
      } else {
        p.y = (t / ((2.0 - s) - q));
      };
      mediump float u;
      u = (((
        (s - f)
       / 6.0) + (t / 2.0)) / t);
      mediump float v;
      v = (((
        (s - h)
       / 6.0) + (t / 2.0)) / t);
      mediump float C;
      C = (((
        (s - j)
       / 6.0) + (t / 2.0)) / t);
      if ((f == s)) {
        p.x = (C - v);
      } else {
        if ((h == s)) {
          p.x = ((0.3333333 + u) - C);
        } else {
          if ((j == s)) {
            p.x = ((0.6666667 + v) - u);
          };
        };
      };
      if ((p.x < 0.0)) {
        p.x = (p.x + 1.0);
      } else {
        if ((p.x > 1.0)) {
          p.x = (p.x - 1.0);
        };
      };
    };
    mediump vec3 D;
    mediump float E;
    E = min (min (k, m), n);
    mediump float F;
    F = max (max (k, m), n);
    mediump float H;
    H = (F - E);
    D.z = ((F + E) / 2.0);
    if ((H == 0.0)) {
      D.x = 0.0;
      D.y = 0.0;
    } else {
      if ((D.z < 0.5)) {
        D.y = (H / (F + E));
      } else {
        D.y = (H / ((2.0 - F) - E));
      };
      mediump float J;
      J = (((
        (F - k)
       / 6.0) + (H / 2.0)) / H);
      mediump float K;
      K = (((
        (F - m)
       / 6.0) + (H / 2.0)) / H);
      mediump float L;
      L = (((
        (F - n)
       / 6.0) + (H / 2.0)) / H);
      if ((k == F)) {
        D.x = (L - K);
      } else {
        if ((m == F)) {
          D.x = ((0.3333333 + J) - L);
        } else {
          if ((n == F)) {
            D.x = ((0.6666667 + K) - J);
          };
        };
      };
      if ((D.x < 0.0)) {
        D.x = (D.x + 1.0);
      } else {
        if ((D.x > 1.0)) {
          D.x = (D.x - 1.0);
        };
      };
    };
    mediump vec3 M;
    M.xy = p.xy;
    M.z = D.z;
    mediump vec3 N;
    if ((p.y == 0.0)) {
      N = M.zzz;
    } else {
      mediump float P;
      if ((D.z < 0.5)) {
        P = (D.z * (1.0 + p.y));
      } else {
        P = ((D.z + p.y) - (p.y * D.z));
      };
      mediump float Q;
      Q = ((2.0 * D.z) - P);
      mediump float T;
      T = (p.x + 0.3333333);
      mediump float U;
      if ((T < 0.0)) {
        T += 1.0;
      } else {
        if ((T > 1.0)) {
          T = (T - 1.0);
        };
      };
      if (((6.0 * T) < 1.0)) {
        U = (Q + ((
          (P - Q)
         * 6.0) * T));
      } else {
        if (((2.0 * T) < 1.0)) {
          U = P;
        } else {
          if (((3.0 * T) < 2.0)) {
            U = (Q + ((
              (P - Q)
             * 
              (0.6666667 - T)
            ) * 6.0));
          } else {
            U = Q;
          };
        };
      };
      N.x = U;
      mediump float V;
      V = M.x;
      mediump float wr;
      if ((p.x < 0.0)) {
        V = (p.x + 1.0);
      } else {
        if ((V > 1.0)) {
          V = (V - 1.0);
        };
      };
      if (((6.0 * V) < 1.0)) {
        wr = (Q + ((
          (P - Q)
         * 6.0) * V));
      } else {
        if (((2.0 * V) < 1.0)) {
          wr = P;
        } else {
          if (((3.0 * V) < 2.0)) {
            wr = (Q + ((
              (P - Q)
             * 
              (0.6666667 - V)
            ) * 6.0));
          } else {
            wr = Q;
          };
        };
      };
      N.y = wr;
      mediump float rr;
      rr = (p.x - 0.3333333);
      mediump float yr;
      if ((rr < 0.0)) {
        rr += 1.0;
      } else {
        if ((rr > 1.0)) {
          rr = (rr - 1.0);
        };
      };
      if (((6.0 * rr) < 1.0)) {
        yr = (Q + ((
          (P - Q)
         * 6.0) * rr));
      } else {
        if (((2.0 * rr) < 1.0)) {
          yr = P;
        } else {
          if (((3.0 * rr) < 2.0)) {
            yr = (Q + ((
              (P - Q)
             * 
              (0.6666667 - rr)
            ) * 6.0));
          } else {
            yr = Q;
          };
        };
      };
      N.z = yr;
    };
    a = N;
  } else {
    mediump float gr;
    gr = max (g.x, a.x);
    mediump float xr;
    xr = max (g.y, a.y);
    mediump float br;
    br = max (g.z, a.z);
    mediump float zr;
    if ((e.x < 0.5)) {
      zr = ((2.0 * e.x) * g.x);
    } else {
      zr = (1.0 - ((2.0 * 
        (1.0 - e.x)
      ) * (1.0 - g.x)));
    };
    mediump float ar;
    if ((e.y < 0.5)) {
      ar = ((2.0 * e.y) * g.y);
    } else {
      ar = (1.0 - ((2.0 * 
        (1.0 - e.y)
      ) * (1.0 - g.y)));
    };
    mediump float _r;
    if ((e.z < 0.5)) {
      _r = ((2.0 * e.z) * g.z);
    } else {
      _r = (1.0 - ((2.0 * 
        (1.0 - e.z)
      ) * (1.0 - g.z)));
    };
    mediump vec3 Ar;
    mediump float Yr;
    Yr = min (min (gr, xr), br);
    mediump float Rr;
    Rr = max (max (gr, xr), br);
    mediump float Xr;
    Xr = (Rr - Yr);
    Ar.z = ((Rr + Yr) / 2.0);
    if ((Xr == 0.0)) {
      Ar.x = 0.0;
      Ar.y = 0.0;
    } else {
      if ((Ar.z < 0.5)) {
        Ar.y = (Xr / (Rr + Yr));
      } else {
        Ar.y = (Xr / ((2.0 - Rr) - Yr));
      };
      mediump float Wr;
      Wr = (((
        (Rr - gr)
       / 6.0) + (Xr / 2.0)) / Xr);
      mediump float Zr;
      Zr = (((
        (Rr - xr)
       / 6.0) + (Xr / 2.0)) / Xr);
      mediump float or;
      or = (((
        (Rr - br)
       / 6.0) + (Xr / 2.0)) / Xr);
      if ((gr == Rr)) {
        Ar.x = (or - Zr);
      } else {
        if ((xr == Rr)) {
          Ar.x = ((0.3333333 + Wr) - or);
        } else {
          if ((br == Rr)) {
            Ar.x = ((0.6666667 + Zr) - Wr);
          };
        };
      };
      if ((Ar.x < 0.0)) {
        Ar.x = (Ar.x + 1.0);
      } else {
        if ((Ar.x > 1.0)) {
          Ar.x = (Ar.x - 1.0);
        };
      };
    };
    mediump vec3 Or;
    mediump float Ir;
    Ir = min (min (zr, ar), _r);
    mediump float ir;
    ir = max (max (zr, ar), _r);
    mediump float lr;
    lr = (ir - Ir);
    Or.z = ((ir + Ir) / 2.0);
    if ((lr == 0.0)) {
      Or.x = 0.0;
      Or.y = 0.0;
    } else {
      if ((Or.z < 0.5)) {
        Or.y = (lr / (ir + Ir));
      } else {
        Or.y = (lr / ((2.0 - ir) - Ir));
      };
      mediump float Sr;
      Sr = (((
        (ir - zr)
       / 6.0) + (lr / 2.0)) / lr);
      mediump float Gr;
      Gr = (((
        (ir - ar)
       / 6.0) + (lr / 2.0)) / lr);
      mediump float Br;
      Br = (((
        (ir - _r)
       / 6.0) + (lr / 2.0)) / lr);
      if ((zr == ir)) {
        Or.x = (Br - Gr);
      } else {
        if ((ar == ir)) {
          Or.x = ((0.3333333 + Sr) - Br);
        } else {
          if ((_r == ir)) {
            Or.x = ((0.6666667 + Gr) - Sr);
          };
        };
      };
      if ((Or.x < 0.0)) {
        Or.x = (Or.x + 1.0);
      } else {
        if ((Or.x > 1.0)) {
          Or.x = (Or.x - 1.0);
        };
      };
    };
    mediump vec3 cr;
    cr.xy = Ar.xy;
    cr.z = Or.z;
    mediump vec3 dr;
    if ((Ar.y == 0.0)) {
      dr = cr.zzz;
    } else {
      mediump float er;
      if ((Or.z < 0.5)) {
        er = (Or.z * (1.0 + Ar.y));
      } else {
        er = ((Or.z + Ar.y) - (Ar.y * Or.z));
      };
      mediump float fr;
      fr = ((2.0 * Or.z) - er);
      mediump float hr;
      hr = (Ar.x + 0.3333333);
      mediump float jr;
      if ((hr < 0.0)) {
        hr += 1.0;
      } else {
        if ((hr > 1.0)) {
          hr = (hr - 1.0);
        };
      };
      if (((6.0 * hr) < 1.0)) {
        jr = (fr + ((
          (er - fr)
         * 6.0) * hr));
      } else {
        if (((2.0 * hr) < 1.0)) {
          jr = er;
        } else {
          if (((3.0 * hr) < 2.0)) {
            jr = (fr + ((
              (er - fr)
             * 
              (0.6666667 - hr)
            ) * 6.0));
          } else {
            jr = fr;
          };
        };
      };
      dr.x = jr;
      mediump float kr;
      kr = cr.x;
      mediump float mr;
      if ((Ar.x < 0.0)) {
        kr = (Ar.x + 1.0);
      } else {
        if ((kr > 1.0)) {
          kr = (kr - 1.0);
        };
      };
      if (((6.0 * kr) < 1.0)) {
        mr = (fr + ((
          (er - fr)
         * 6.0) * kr));
      } else {
        if (((2.0 * kr) < 1.0)) {
          mr = er;
        } else {
          if (((3.0 * kr) < 2.0)) {
            mr = (fr + ((
              (er - fr)
             * 
              (0.6666667 - kr)
            ) * 6.0));
          } else {
            mr = fr;
          };
        };
      };
      dr.y = mr;
      mediump float nr;
      nr = (Ar.x - 0.3333333);
      mediump float pr;
      if ((nr < 0.0)) {
        nr += 1.0;
      } else {
        if ((nr > 1.0)) {
          nr = (nr - 1.0);
        };
      };
      if (((6.0 * nr) < 1.0)) {
        pr = (fr + ((
          (er - fr)
         * 6.0) * nr));
      } else {
        if (((2.0 * nr) < 1.0)) {
          pr = er;
        } else {
          if (((3.0 * nr) < 2.0)) {
            pr = (fr + ((
              (er - fr)
             * 
              (0.6666667 - nr)
            ) * 6.0));
          } else {
            pr = fr;
          };
        };
      };
      dr.z = pr;
    };
    a = dr;
  };
  if ((effectType.y < 0.2)) {
    a = mix (g, a, y);
  } else {
    if ((effectType.y < 0.6)) {
      mediump float qr;
      highp float sr;
      sr = (1.0 - ((
        (1.0 - r.w)
       * x) * mix (x, 1.0, outlineType.y)));
      qr = sr;
      qr = (qr + ((outlineType.x * 0.15) - 0.05));
      if ((qr < 0.88)) {
        a = vec3(1.0, 1.0, 1.0);
      } else {
        if ((qr < 0.885)) {
          a = (1.0 - ((1.0 - a) * vec3(0.2, 0.2, 0.2)));
        } else {
          if ((qr < 0.89)) {
            a = (1.0 - ((1.0 - a) * vec3(0.4, 0.4, 0.4)));
          } else {
            if ((qr < 0.895)) {
              a = (1.0 - ((1.0 - a) * vec3(0.6, 0.6, 0.6)));
            } else {
              if ((qr < 0.9)) {
                a = (1.0 - ((1.0 - a) * vec3(0.7, 0.7, 0.7)));
              } else {
                if ((qr < 0.905)) {
                  a = (1.0 - ((1.0 - a) * vec3(0.8, 0.8, 0.8)));
                } else {
                  if ((qr < 0.91)) {
                    a = (1.0 - ((1.0 - a) * vec3(0.9, 0.9, 0.9)));
                  } else {
                    if ((qr < 0.915)) {
                      a = (1.0 - ((1.0 - a) * vec3(0.95, 0.95, 0.95)));
                    };
                  };
                };
              };
            };
          };
        };
      };
      a = mix (g, a, y);
    } else {
      mediump float tr;
      highp float ur;
      ur = (1.0 - ((
        (1.0 - r.w)
       * x) * mix (x, 1.0, outlineType.y)));
      tr = ur;
      tr = (tr + ((outlineType.x * 0.15) - 0.05));
      if ((tr < 0.88)) {
        a = vec3(1.0, 1.0, 1.0);
      } else {
        if ((tr < 0.885)) {
          a = mix (a, vec3(1.0, 1.0, 1.0), 0.8);
        } else {
          if ((tr < 0.89)) {
            a = mix (a, vec3(1.0, 1.0, 1.0), 0.6);
          } else {
            if ((tr < 0.895)) {
              a = mix (a, vec3(1.0, 1.0, 1.0), 0.4);
            } else {
              if ((tr < 0.9)) {
                a = mix (a, vec3(1.0, 1.0, 1.0), 0.3);
              } else {
                if ((tr < 0.905)) {
                  a = mix (a, vec3(1.0, 1.0, 1.0), 0.2);
                } else {
                  if ((tr < 0.91)) {
                    a = mix (a, vec3(1.0, 1.0, 1.0), 0.1);
                  } else {
                    if ((tr < 0.915)) {
                      a = mix (a, vec3(1.0, 1.0, 1.0), 0.05);
                    };
                  };
                };
              };
            };
          };
        };
      };
      a = mix (outlineColor, a, y);
    };
  };
  mediump float vr;
  vr = ((0.5 - (
    (max (max (a.x, a.y), a.z) + min (min (a.x, a.y), a.z))
   / 2.0)) * 2.0);
  mediump vec3 Cr;
  mediump vec4 Dr;
  Dr.w = 1.0;
  Dr.xyz = (a + ((vr - 
    clamp (vr, -0.5, 0.5)
  ) * 0.125));
  highp vec2 Er;
  highp vec2 Fr;
  mediump vec2 Hr;
  mediump vec2 Jr;
  mediump float Kr;
  Kr = (Dr.z * 63.0);
  Jr.y = floor((floor(Kr) / 8.0));
  Jr.x = (floor(Kr) - (Jr.y * 8.0));
  Hr.y = floor((ceil(Kr) / 8.0));
  Hr.x = (ceil(Kr) - (Hr.y * 8.0));
  Fr = (((Jr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Dr.xy));
  Er = (((Hr * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * Dr.xy));
  lowp vec4 Lr;
  Lr = texture2D (lut, Fr);
  mediump vec4 Mr;
  Mr = Lr;
  lowp vec4 Nr;
  Nr = texture2D (lut, Er);
  mediump vec4 Pr;
  Pr = Nr;
  mediump float Qr;
  Qr = clamp (fract(Kr), 0.0, 1.0);
  Cr = ((Mr * (1.0 - Qr)) + (Pr * Qr)).xyz;
  mediump float Tr;
  if ((i < 0.5)) {
    Tr = (((2.0 * Cr.x) * i) + ((Cr.x * Cr.x) * (1.0 - 
      (2.0 * i)
    )));
  } else {
    Tr = ((sqrt(Cr.x) * (
      (2.0 * i)
     - 1.0)) + ((2.0 * Cr.x) * (1.0 - i)));
  };
  mediump float Ur;
  if ((i < 0.5)) {
    Ur = (((2.0 * Cr.y) * i) + ((Cr.y * Cr.y) * (1.0 - 
      (2.0 * i)
    )));
  } else {
    Ur = ((sqrt(Cr.y) * (
      (2.0 * i)
     - 1.0)) + ((2.0 * Cr.y) * (1.0 - i)));
  };
  mediump float Vr;
  if ((i < 0.5)) {
    Vr = (((2.0 * Cr.z) * i) + ((Cr.z * Cr.z) * (1.0 - 
      (2.0 * i)
    )));
  } else {
    Vr = ((sqrt(Cr.z) * (
      (2.0 * i)
     - 1.0)) + ((2.0 * Cr.z) * (1.0 - i)));
  };
  mediump vec3 wy;
  wy.x = Tr;
  wy.y = Ur;
  wy.z = Vr;
  a = wy;
  mediump float ry;
  if ((b < 0.5)) {
    ry = max (((Tr + 
      (2.0 * b)
    ) - 1.0), 0.0);
  } else {
    ry = min ((Tr + (2.0 * 
      (b - 0.5)
    )), 1.0);
  };
  mediump float yy;
  if ((b < 0.5)) {
    yy = max (((Ur + 
      (2.0 * b)
    ) - 1.0), 0.0);
  } else {
    yy = min ((Ur + (2.0 * 
      (b - 0.5)
    )), 1.0);
  };
  mediump float gy;
  if ((b < 0.5)) {
    gy = max (((Vr + 
      (2.0 * b)
    ) - 1.0), 0.0);
  } else {
    gy = min ((Vr + (2.0 * 
      (b - 0.5)
    )), 1.0);
  };
  mediump vec3 xy;
  xy.x = ry;
  xy.y = yy;
  xy.z = gy;
  mediump vec4 by;
  by.w = 1.0;
  by.xyz = xy;
  gl_FragColor = by;
}

