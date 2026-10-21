uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D preprocessed;
uniform sampler2D specularMap;
varying highp vec2 texCoord;
void main ()
{
  mediump float r;
  mediump vec3 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  g = x;
  lowp vec3 b;
  b = texture2D (tex, texCoord).xyz;
  y = b;
  highp float z;
  highp float a;
  mediump float _;
  mediump float A;
  mediump float Y;
  mediump float R;
  mediump float X;
  mediump float W;
  mediump float Z;
  mediump float o;
  highp float O;
  highp vec2 I;
  I = (texCoord + vec2(-0.002441406, -0.002441406));
  mediump vec4 i;
  lowp vec4 l;
  l = texture2D (preprocessed, I);
  i = l;
  O = (1.0 - ((i.y * i.y) * i.w));
  o = O;
  highp float S;
  highp vec2 G;
  G = (texCoord + vec2(0.0, -0.002441406));
  mediump vec4 B;
  lowp vec4 c;
  c = texture2D (preprocessed, G);
  B = c;
  S = (1.0 - ((B.y * B.y) * B.w));
  Z = S;
  highp float d;
  highp vec2 e;
  e = (texCoord + vec2(0.002441406, -0.002441406));
  mediump vec4 f;
  lowp vec4 h;
  h = texture2D (preprocessed, e);
  f = h;
  d = (1.0 - ((f.y * f.y) * f.w));
  W = d;
  highp float j;
  highp vec2 k;
  k = (texCoord + vec2(-0.002441406, 0.0));
  mediump vec4 m;
  lowp vec4 n;
  n = texture2D (preprocessed, k);
  m = n;
  j = (1.0 - ((m.y * m.y) * m.w));
  X = j;
  highp float p;
  highp vec2 q;
  q = (texCoord + vec2(0.002441406, 0.0));
  mediump vec4 s;
  lowp vec4 t;
  t = texture2D (preprocessed, q);
  s = t;
  p = (1.0 - ((s.y * s.y) * s.w));
  R = p;
  highp float u;
  highp vec2 v;
  v = (texCoord + vec2(-0.002441406, 0.002441406));
  mediump vec4 C;
  lowp vec4 D;
  D = texture2D (preprocessed, v);
  C = D;
  u = (1.0 - ((C.y * C.y) * C.w));
  Y = u;
  highp float E;
  highp vec2 F;
  F = (texCoord + vec2(0.0, 0.002441406));
  mediump vec4 H;
  lowp vec4 J;
  J = texture2D (preprocessed, F);
  H = J;
  E = (1.0 - ((H.y * H.y) * H.w));
  A = E;
  highp float K;
  highp vec2 L;
  L = (texCoord + vec2(0.002441406, 0.002441406));
  mediump vec4 M;
  lowp vec4 N;
  N = texture2D (preprocessed, L);
  M = N;
  K = (1.0 - ((M.y * M.y) * M.w));
  _ = K;
  mediump float P;
  P = (((
    ((-(o) + W) + (-2.0 * X))
   + 
    (2.0 * R)
  ) - Y) + _);
  a = P;
  mediump float Q;
  Q = (((
    ((-(o) + (-2.0 * Z)) - W)
   + Y) + (2.0 * A)) + _);
  z = Q;
  highp vec3 T;
  T.z = 0.5;
  T.x = a;
  T.y = z;
  highp vec2 U;
  U = ((normalize(
    (normalize(T) + ((y - vec3(0.5, 0.5, 0.5)) * 0.1))
  ).xy * 0.5) + vec2(0.5, 0.5));
  lowp float V;
  V = (texture2D (specularMap, U).y * 0.5);
  r = V;
  y = ((y * g.y) * 0.95);
  mediump vec4 wr;
  wr.w = 1.0;
  wr.xyz = mix (vec3(1.0, 1.0, 1.0), (y + vec3(r)), g.w);
  gl_FragColor = wr;
}

