uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
uniform sampler2D blurmap2;
uniform sampler2D lut;
uniform sampler2D animNoise;
uniform highp vec2 timeStrength;
uniform highp vec3 outlineColor;
varying highp vec2 texCoord;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (animNoise, texCoord);
  x = b;
  highp float z;
  z = clamp (((
    (5.0 + (x.y * 2.0))
   - 
    (timeStrength.x * 15.0)
  ) - (
    (1.0 - timeStrength.y)
   * 10.0)), 0.0, 10.0);
  mediump vec4 a;
  mediump int _;
  highp int A;
  A = int(floor(z));
  _ = A;
  if ((_ == 0)) {
    lowp vec4 Y;
    Y = texture2D (tex, texCoord);
    lowp vec4 R;
    R = texture2D (blurmap0, texCoord);
    highp vec4 X;
    X = mix (Y, R, fract(z));
    a = X;
  } else {
    if ((_ == 1)) {
      lowp vec4 W;
      W = texture2D (blurmap0, texCoord);
      lowp vec4 Z;
      Z = texture2D (blurmap1, texCoord);
      highp vec4 o;
      o = mix (W, Z, fract(z));
      a = o;
    } else {
      if ((_ == 2)) {
        lowp vec4 O;
        O = texture2D (blurmap1, texCoord);
        lowp vec4 I;
        I = texture2D (blurmap2, texCoord);
        highp vec4 i;
        i = mix (O, I, fract(z));
        a = i;
      } else {
        if ((_ == 3)) {
          lowp vec4 l;
          l = texture2D (blurmap2, texCoord);
          highp vec4 S;
          S = mix (l, vec4(1.0, 1.0, 1.0, 1.0), fract(z));
          a = S;
        } else {
          a = vec4(1.0, 1.0, 1.0, 1.0);
        };
      };
    };
  };
  lowp vec4 G;
  G = texture2D (outline, texCoord);
  g = G;
  lowp vec4 B;
  B = texture2D (blurmap1, texCoord);
  y = B;
  mediump vec3 c;
  c = mix (vec3(1.0, 1.0, 1.0), a.xyz, g.w);
  highp vec3 d;
  mediump vec3 e;
  e = (c * (0.7 + (
    pow (y.w, 4.0)
   * 0.35)));
  d = mix (c, e, clamp ((
    pow ((timeStrength.x * 1.4), 4.0)
   + 
    (1.0 - timeStrength.y)
  ), 0.0, 1.0));
  r = d;
  highp vec3 f;
  f = mix (outlineColor, r, g.y);
  r = f;
  mediump vec4 h;
  h.w = 1.0;
  h.xyz = clamp (r, 0.0, 0.99999);
  highp vec2 j;
  highp vec2 k;
  mediump vec2 m;
  mediump vec2 n;
  mediump float p;
  p = (h.z * 63.0);
  n.y = floor((floor(p) / 8.0));
  n.x = (floor(p) - (n.y * 8.0));
  m.y = floor((ceil(p) / 8.0));
  m.x = (ceil(p) - (m.y * 8.0));
  k = (((n * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * h.xy));
  j = (((m * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * h.xy));
  lowp vec4 q;
  q = texture2D (lut, k);
  mediump vec4 s;
  s = q;
  lowp vec4 t;
  t = texture2D (lut, j);
  mediump vec4 u;
  u = t;
  mediump float v;
  v = clamp (fract(p), 0.0, 1.0);
  mediump vec4 C;
  C.w = 1.0;
  C.xyz = ((s * (1.0 - v)) + (u * v)).xyz;
  gl_FragColor = C;
}

