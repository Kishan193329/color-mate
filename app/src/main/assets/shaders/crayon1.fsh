uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D indexTex;
uniform sampler2D animNoise;
uniform sampler2D noise;
uniform sampler2D hatch;
uniform sampler2D background;
varying highp vec2 texCoord;
uniform highp vec2 randomOffset;
uniform highp vec2 timeStrength;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  mediump vec4 a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord * 3.0);
  _ = texture2D (background, A);
  a = _;
  lowp vec4 Y;
  highp vec2 R;
  R = (texCoord * 2.25);
  Y = texture2D (noise, R);
  z = Y;
  lowp vec4 X;
  X = texture2D (blurmap0, texCoord);
  b = X;
  lowp vec4 W;
  W = texture2D (outline, texCoord);
  x = W;
  lowp vec4 Z;
  Z = texture2D (tex, texCoord);
  lowp vec2 o;
  o = texture2D (indexTex, texCoord).xy;
  y = o;
  highp float O;
  O = (((y.x * 256.0) + y.y) / 13.0);
  highp float I;
  I = cos(O);
  highp vec2 i;
  i.x = I;
  i.y = sin(O);
  highp vec2 l;
  l.x = i.y;
  l.y = -(I);
  lowp vec4 S;
  highp vec2 G;
  G = (((
    ((texCoord.x - 0.5) * i)
   + 
    ((texCoord.y - 0.5) * l)
  ) * 3.0) + randomOffset);
  S = texture2D (hatch, G);
  r = S;
  g = Z;
  lowp vec4 B;
  highp vec2 c;
  c = (texCoord * 1.75);
  B = texture2D (animNoise, c);
  highp vec4 d;
  d = mix (vec4(1.0, 1.0, 1.0, 1.0), g, mix (1.0, clamp (
    ((((timeStrength.x * 5.0) - 1.05) - ((
      dot ((texCoord - vec2(0.5, 0.5)), i)
     / 
      dot (i, i)
    ) + (
      (B.y - r.y)
     * 0.4))) * 30.0)
  , 0.0, 1.0), timeStrength.y));
  g = d;
  g = (1.0 - ((1.0 - g) * 1.666667));
  mediump vec4 e;
  e.w = 1.0;
  e.xyz = (mix ((
    mix (g.xyz, vec3(1.0, 1.0, 1.0), (r.y + ((
      (1.0 - pow (b.w, 3.0))
     * 0.8) * z.y)))
   * 
    (1.0 + (x.y - 1.0))
  ), vec3(1.0, 1.0, 1.0), (1.0 - x.w)) * a.xyz);
  gl_FragColor = e;
}

