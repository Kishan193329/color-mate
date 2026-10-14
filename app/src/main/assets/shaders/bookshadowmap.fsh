uniform sampler2D blurmap0;
varying highp vec2 texCoord;
void main ()
{
  highp float r;
  highp float y;
  highp float g;
  mediump float x;
  mediump float b;
  mediump float z;
  mediump float a;
  mediump float _;
  mediump float A;
  mediump float Y;
  mediump float R;
  highp vec4 X;
  lowp vec4 W;
  highp vec2 Z;
  Z = (texCoord + vec2(-0.001464844, -0.001464844));
  W = texture2D (blurmap0, Z);
  X = W;
  highp float o;
  o = dot (X.xyz, vec3(0.33333, 0.33333, 0.33333));
  R = o;
  highp vec4 O;
  lowp vec4 I;
  highp vec2 i;
  i = (texCoord + vec2(0.0, -0.001464844));
  I = texture2D (blurmap0, i);
  O = I;
  highp float l;
  l = dot (O.xyz, vec3(0.33333, 0.33333, 0.33333));
  Y = l;
  highp vec4 S;
  lowp vec4 G;
  highp vec2 B;
  B = (texCoord + vec2(0.001464844, -0.001464844));
  G = texture2D (blurmap0, B);
  S = G;
  highp float c;
  c = dot (S.xyz, vec3(0.33333, 0.33333, 0.33333));
  A = c;
  highp vec4 d;
  lowp vec4 e;
  highp vec2 f;
  f = (texCoord + vec2(-0.001464844, 0.0));
  e = texture2D (blurmap0, f);
  d = e;
  highp float h;
  h = dot (d.xyz, vec3(0.33333, 0.33333, 0.33333));
  _ = h;
  highp vec4 j;
  lowp vec4 k;
  highp vec2 m;
  m = (texCoord + vec2(0.001464844, 0.0));
  k = texture2D (blurmap0, m);
  j = k;
  highp float n;
  n = dot (j.xyz, vec3(0.33333, 0.33333, 0.33333));
  a = n;
  highp vec4 p;
  lowp vec4 q;
  highp vec2 s;
  s = (texCoord + vec2(-0.001464844, 0.001464844));
  q = texture2D (blurmap0, s);
  p = q;
  highp float t;
  t = dot (p.xyz, vec3(0.33333, 0.33333, 0.33333));
  z = t;
  highp vec4 u;
  lowp vec4 v;
  highp vec2 C;
  C = (texCoord + vec2(0.0, 0.001464844));
  v = texture2D (blurmap0, C);
  u = v;
  highp float D;
  D = dot (u.xyz, vec3(0.33333, 0.33333, 0.33333));
  b = D;
  highp vec4 E;
  lowp vec4 F;
  highp vec2 H;
  H = (texCoord + vec2(0.001464844, 0.001464844));
  F = texture2D (blurmap0, H);
  E = F;
  highp float J;
  J = dot (E.xyz, vec3(0.33333, 0.33333, 0.33333));
  x = J;
  mediump float K;
  K = (((
    ((-(R) - z) - (2.0 * _))
   + 
    (2.0 * a)
  ) + x) + A);
  g = K;
  mediump float L;
  L = (((
    ((-(R) + (-2.0 * Y)) - A)
   + z) + (2.0 * b)) + x);
  y = L;
  highp vec3 M;
  M.z = 0.5;
  M.x = g;
  M.y = y;
  highp vec3 N;
  N = normalize(M);
  highp float P;
  P = (0.75 + (dot (N, vec3(-0.7071068, 0.7071068, 0.0)) * 0.35));
  r = P;
  highp vec2 Q;
  Q = texCoord;
  highp float U;
  highp float V;
  highp vec4 wr;
  lowp vec4 rr;
  rr = texture2D (blurmap0, texCoord);
  wr = rr;
  V = dot (wr.xyz, vec3(0.33333, 0.33333, 0.33333));
  U = 0.0;
  for (mediump int T = 0; T < 10; T++) {
    highp vec4 yr;
    lowp vec4 gr;
    highp vec2 xr;
    xr = (Q + (vec2(0.0015, -0.0015) * float(
      (T + 1)
    )));
    gr = texture2D (blurmap0, xr);
    yr = gr;
    U = (U + (clamp (
      ((dot (yr.xyz, vec3(0.33333, 0.33333, 0.33333)) - V) * 2.0)
    , 0.0, 1.0) / (
      (float(T) * 0.25)
     + 1.0)));
  };
  r = (P * (1.0 - (
    clamp (U, 0.0, 1.0)
   * 0.4)));
  highp vec4 br;
  br.xyz = ((N * 0.5) + vec3(0.5, 0.5, 0.5));
  br.w = clamp ((r * 0.5), 0.0, 1.0);
  gl_FragColor = br;
}

