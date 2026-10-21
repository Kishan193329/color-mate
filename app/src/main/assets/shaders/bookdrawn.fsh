uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D hatch;
uniform sampler2D disp;
uniform sampler2D noise;
uniform sampler2D lut;
uniform highp vec2 timeStrength;
uniform highp vec4 animationVec;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec2 b;
  mediump vec4 z;
  lowp vec4 a;
  highp vec2 _;
  highp vec2 A;
  A = (texCoord + animationVec.zw);
  _ = (A * 5.0);
  a = texture2D (noise, _);
  z = a;
  highp vec2 Y;
  Y = (texCoord + ((vec2(0.5, 0.5) - z.yz) * 0.006));
  b = Y;
  lowp vec4 R;
  R = texture2D (outline, b);
  x = R;
  lowp vec4 X;
  X = texture2D (tex, b);
  g = X;
  lowp vec4 W;
  W = texture2D (disp, A);
  z = W;
  lowp vec2 Z;
  Z = texture2D (indexTex, texCoord).xy;
  y = Z;
  highp float o;
  o = (((y.x * 256.0) + y.y) / 13.0);
  highp float O;
  O = sin(o);
  highp vec2 I;
  I.x = cos(o);
  I.y = O;
  highp vec2 i;
  i.x = -(O);
  i.y = I.x;
  highp vec2 l;
  l = normalize((I + (
    (i * (z.x - 0.5))
   * 0.005)));
  highp vec2 S;
  S.x = l.y;
  S.y = -(l.x);
  lowp vec4 G;
  highp vec2 B;
  B = (((
    (texCoord.x + 10.0)
   * l) + (
    (texCoord.y + 10.0)
   * S)) - animationVec.wz);
  G = texture2D (hatch, B);
  r = G;
  highp vec2 c;
  c.x = l.y;
  c.y = -(l.x);
  highp vec3 d;
  d = g.zyx;
  highp vec4 e;
  e.xy = d.zy;
  e.zw = vec2(-1.0, 0.6666667);
  highp vec4 f;
  f.xy = d.yz;
  f.zw = vec2(0.0, -0.3333333);
  highp vec4 h;
  h = mix (e, f, float((d.y >= d.z)));
  highp vec4 j;
  j.xyz = h.xyw;
  j.w = d.x;
  highp vec4 k;
  k.x = d.x;
  k.yzw = h.yzx;
  highp vec4 m;
  m = mix (j, k, float((d.x >= h.x)));
  lowp vec4 n;
  highp vec2 p;
  p = (texCoord * 2.0);
  n = texture2D (noise, p);
  highp vec4 q;
  q = mix (vec4(1.0, 1.0, 1.0, 1.0), g, mix (1.0, clamp (
    ((((
      (timeStrength.x * 18.0)
     - 
      ((dot ((texCoord - vec2(0.5, 0.5)), c) / dot (l, l)) + (abs((m.z + 
        ((m.w - m.y) / ((6.0 * (m.x - 
          min (m.w, m.y)
        )) + 1e-10))
      )) * 6.0))
    ) - (r.x * 3.0)) - (n.x * 0.4)) * 10.0)
  , 0.0, 1.0), timeStrength.y));
  g = q;
  mediump vec3 s;
  s = mix (((outlineColor * 0.5) + vec3(0.25, 0.25, 0.25)), mix (g.xyz, vec3(1.0, 1.0, 1.0), clamp (
    (1.0 - (r.y * 1.33))
  , 0.0, 1.0)), x.y);
  highp vec2 t;
  highp vec2 u;
  mediump vec2 v;
  mediump vec2 C;
  mediump float D;
  D = (s.z * 63.0);
  C.y = floor((floor(D) / 8.0));
  C.x = (floor(D) - (C.y * 8.0));
  v.y = floor((ceil(D) / 8.0));
  v.x = (ceil(D) - (v.y * 8.0));
  u = (((C * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * s.xy));
  t = (((v * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * s.xy));
  lowp vec4 E;
  E = texture2D (lut, u);
  mediump vec4 F;
  F = E;
  lowp vec4 H;
  H = texture2D (lut, t);
  mediump vec4 J;
  J = H;
  mediump float K;
  K = clamp (fract(D), 0.0, 1.0);
  mediump vec4 L;
  L.w = 1.0;
  L.xyz = ((F * (1.0 - K)) + (J * K)).xyz;
  gl_FragColor = L;
}

