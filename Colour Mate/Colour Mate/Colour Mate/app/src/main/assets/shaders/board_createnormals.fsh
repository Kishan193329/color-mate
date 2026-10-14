uniform sampler2D blurmap0;
varying highp vec2 texCoord;
void main ()
{
  highp float r;
  highp float y;
  mediump float g;
  mediump float x;
  mediump float b;
  mediump float z;
  mediump float a;
  mediump float _;
  mediump float A;
  mediump float Y;
  highp float R;
  highp vec2 X;
  X = (texCoord + vec2(-0.0009765625, -0.0009765625));
  lowp float W;
  W = pow ((1.0 - texture2D (blurmap0, X).w), 8.0);
  R = W;
  Y = R;
  highp float Z;
  highp vec2 o;
  o = (texCoord + vec2(0.0, -0.0009765625));
  lowp float O;
  O = pow ((1.0 - texture2D (blurmap0, o).w), 8.0);
  Z = O;
  A = Z;
  highp float I;
  highp vec2 i;
  i = (texCoord + vec2(0.0009765625, -0.0009765625));
  lowp float l;
  l = pow ((1.0 - texture2D (blurmap0, i).w), 8.0);
  I = l;
  _ = I;
  highp float S;
  highp vec2 G;
  G = (texCoord + vec2(-0.0009765625, 0.0));
  lowp float B;
  B = pow ((1.0 - texture2D (blurmap0, G).w), 8.0);
  S = B;
  a = S;
  highp float c;
  highp vec2 d;
  d = (texCoord + vec2(0.0009765625, 0.0));
  lowp float e;
  e = pow ((1.0 - texture2D (blurmap0, d).w), 8.0);
  c = e;
  z = c;
  highp float f;
  highp vec2 h;
  h = (texCoord + vec2(-0.0009765625, 0.0009765625));
  lowp float j;
  j = pow ((1.0 - texture2D (blurmap0, h).w), 8.0);
  f = j;
  b = f;
  highp float k;
  highp vec2 m;
  m = (texCoord + vec2(0.0, 0.0009765625));
  lowp float n;
  n = pow ((1.0 - texture2D (blurmap0, m).w), 8.0);
  k = n;
  x = k;
  highp float p;
  highp vec2 q;
  q = (texCoord + vec2(0.0009765625, 0.0009765625));
  lowp float s;
  s = pow ((1.0 - texture2D (blurmap0, q).w), 8.0);
  p = s;
  g = p;
  mediump float t;
  t = (((
    ((-(Y) + _) + (-2.0 * a))
   + 
    (2.0 * z)
  ) - b) + g);
  y = t;
  mediump float u;
  u = (((
    ((-(Y) + (-2.0 * A)) - _)
   + b) + (2.0 * x)) + g);
  r = u;
  highp vec3 v;
  v.z = 0.5;
  v.x = y;
  v.y = r;
  highp vec4 C;
  C.w = 1.0;
  C.xyz = (vec3(0.5, 0.5, 0.5) + (normalize(v) * 0.5));
  gl_FragColor = C;
}

