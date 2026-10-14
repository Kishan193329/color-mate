uniform sampler2D blurmap1;
uniform sampler2D om;
uniform sampler2D noisemap;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
void main ()
{
  highp int r;
  mediump float y;
  mediump vec4 g;
  mediump vec2 x;
  mediump vec3 b;
  mediump vec3 z;
  lowp vec3 a;
  a = texture2D (om, texCoord).xyz;
  z = a;
  lowp vec3 _;
  _ = texture2D (noisemap, texCoordTransformed).xyz;
  b = _;
  highp vec3 A;
  A = mix (b, vec3(0.5, 0.5, 0.5), (0.9 - (randomOffset.y * 0.35)));
  b = A;
  mediump float Y;
  if ((b.x < 0.5)) {
    Y = ((2.0 * b.x) * z.x);
  } else {
    Y = (1.0 - ((2.0 * 
      (1.0 - b.x)
    ) * (1.0 - z.x)));
  };
  mediump float R;
  if ((b.y < 0.5)) {
    R = ((2.0 * b.y) * z.y);
  } else {
    R = (1.0 - ((2.0 * 
      (1.0 - b.y)
    ) * (1.0 - z.y)));
  };
  mediump float X;
  if ((b.z < 0.5)) {
    X = ((2.0 * b.z) * z.z);
  } else {
    X = (1.0 - ((2.0 * 
      (1.0 - b.z)
    ) * (1.0 - z.z)));
  };
  mediump vec3 W;
  W.x = Y;
  W.y = R;
  W.z = X;
  z = W;
  mediump vec2 Z;
  Z.x = W.x;
  Z.y = (1.0 - R);
  x = Z;
  g = vec4(0.0, 0.0, 0.0, 0.0);
  y = 0.75;
  r = 0;
  for (; r < 15; r++, y += 0.0175) {
    mediump vec4 o;
    mediump vec2 O;
    O = (vec2(-0.0024, 0.0024) + mix (x, texCoord, y));
    mediump vec4 I;
    mediump vec4 i;
    lowp vec4 l;
    l = texture2D (blurmap1, O);
    mediump vec4 S;
    S = l;
    i = S;
    if ((S != vec4(0.0, 0.0, 0.0, 1.0))) {
      I = S;
    } else {
      lowp vec4 G;
      mediump vec2 B;
      B = (O + vec2(0.001464844, 0.0));
      G = texture2D (blurmap1, B);
      i = G;
      if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
        I = i;
      } else {
        lowp vec4 c;
        mediump vec2 d;
        d = (O + vec2(-0.001464844, 0.0));
        c = texture2D (blurmap1, d);
        i = c;
        if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
          I = i;
        } else {
          lowp vec4 e;
          mediump vec2 f;
          f = (O + vec2(0.001464844, 0.001464844));
          e = texture2D (blurmap1, f);
          i = e;
          if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
            I = i;
          } else {
            lowp vec4 h;
            mediump vec2 j;
            j = (O + vec2(0.0, 0.001464844));
            h = texture2D (blurmap1, j);
            i = h;
            if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
              I = i;
            } else {
              lowp vec4 k;
              mediump vec2 m;
              m = (O + vec2(0.001464844, 0.001464844));
              k = texture2D (blurmap1, m);
              i = k;
              if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
                I = i;
              } else {
                lowp vec4 n;
                mediump vec2 p;
                p = (O + vec2(0.001464844, -0.001464844));
                n = texture2D (blurmap1, p);
                i = n;
                if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
                  I = i;
                } else {
                  lowp vec4 q;
                  mediump vec2 s;
                  s = (O + vec2(0.0, -0.001464844));
                  q = texture2D (blurmap1, s);
                  i = q;
                  if ((i != vec4(0.0, 0.0, 0.0, 1.0))) {
                    I = i;
                  } else {
                    lowp vec4 t;
                    mediump vec2 u;
                    u = (O + vec2(0.001464844, -0.001464844));
                    t = texture2D (blurmap1, u);
                    i = t;
                    I = i;
                  };
                };
              };
            };
          };
        };
      };
    };
    o.xyz = I.xyz;
    o.w = 1.0;
    if ((((I.x + I.y) + I.z) < 0.2)) {
      o = vec4(0.0, 0.0, 0.0, 0.0);
    };
    g = (g + o);
  };
  g = (g * (1.0/(g.w)));
  mediump vec4 v;
  v.w = 1.0;
  v.xyz = g.xyz;
  gl_FragColor = v;
}

